package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.application.dto.AdminProfile;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.application.dto.ResendOtpResult;
import in.anytimework.atwbackend.identity.domain.AdminAuthToken;
import in.anytimework.atwbackend.identity.domain.AdminAuthTokenRepository;
import in.anytimework.atwbackend.identity.domain.AdminRefreshToken;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AuthTokenType;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import in.anytimework.atwbackend.shared.security.OpaqueTokens;
import in.anytimework.atwbackend.shared.security.jwt.AccessTokenService;
import in.anytimework.atwbackend.shared.security.jwt.AdminTokenClaims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates admin authentication: password login → email OTP (2FA) → JWT access + rotating
 * refresh, plus refresh/logout and the forgot/reset-password flows. Audits every step.
 */
@Service
public class AuthService {

    private static final String OTP_TEMPLATE = "ADMIN_LOGIN_OTP";
    private static final String RESET_TEMPLATE = "ADMIN_PASSWORD_RESET";
    private static final String CHANGED_TEMPLATE = "ADMIN_PASSWORD_CHANGED";

    private final AdminUserRepository adminUsers;
    private final AdminAuthTokenRepository authTokens;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AdminAuditService audit;
    private final AdminSecurityProperties properties;
    private final Clock clock;
    private final String frontendBaseUrl;

    public AuthService(AdminUserRepository adminUsers, AdminAuthTokenRepository authTokens, OtpService otpService,
                       RefreshTokenService refreshTokenService, AccessTokenService accessTokenService,
                       PasswordEncoder passwordEncoder, NotificationService notificationService,
                       AdminAuditService audit, AdminSecurityProperties properties, Clock clock,
                       @Value("${atw.frontend.base-url}") String frontendBaseUrl) {
        this.adminUsers = adminUsers;
        this.authTokens = authTokens;
        this.otpService = otpService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Step 1: verify password; on success send an OTP (or issue tokens directly if OTP is disabled). */
    public LoginResult login(String usernameOrEmail, String password, String ip, String userAgent) {
        Instant now = clock.instant();
        AdminUser admin = adminUsers
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElse(null);

        if (admin == null) {
            audit.record(null, "LOGIN_FAILED", "Unknown identifier: " + usernameOrEmail, ip, userAgent);
            throw new UnauthorizedException("Invalid credentials");
        }
        if (admin.isLocked(now)) {
            audit.record(admin.getId(), "LOGIN_BLOCKED", "Account locked", ip, userAgent);
            throw new UnauthorizedException("Account is temporarily locked. Please try again later.");
        }
        if (!admin.isActive()) {
            audit.record(admin.getId(), "LOGIN_BLOCKED", "Account disabled", ip, userAgent);
            throw new UnauthorizedException("Account is disabled.");
        }
        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.registerFailedLogin(properties.maxFailedLogins(), properties.lockoutDuration(), now);
            adminUsers.save(admin);
            audit.record(admin.getId(), "LOGIN_FAILED", "Invalid password", ip, userAgent);
            throw new UnauthorizedException("Invalid credentials");
        }

        admin.clearLoginFailures();
        adminUsers.save(admin);

        if (!properties.otpEnabled()) {
            admin.registerSuccessfulLogin(now);
            adminUsers.save(admin);
            audit.record(admin.getId(), "LOGIN_SUCCESS", "Password login (OTP disabled)", ip, userAgent);
            return LoginResult.authenticated(issueTokens(admin, ip, userAgent));
        }

        OtpService.IssuedOtp otp = otpService.issue(admin.getId());
        sendOtpEmail(admin, otp.code());
        audit.record(admin.getId(), "LOGIN_OTP_SENT", "OTP sent for login", ip, userAgent);
        return LoginResult.otpChallenge(otp.otpId(), properties.otpInResponse() ? otp.code() : null);
    }

    /** Step 2: verify the OTP and issue tokens. */
    public AuthTokens verifyOtp(UUID challengeId, String code, String ip, String userAgent) {
        UUID adminId = otpService.verifyAndConsume(challengeId, code);
        AdminUser admin = adminUsers.findById(adminId)
                .orElseThrow(() -> new UnauthorizedException("Invalid verification request"));
        if (!admin.isActive()) {
            throw new UnauthorizedException("Account is disabled.");
        }
        admin.registerSuccessfulLogin(clock.instant());
        adminUsers.save(admin);
        audit.record(admin.getId(), "LOGIN_SUCCESS", "OTP verified", ip, userAgent);
        return issueTokens(admin, ip, userAgent);
    }

    /** Send a fresh OTP (throttled). Returns the new challenge id. */
    public ResendOtpResult resendOtp(UUID challengeId, String ip, String userAgent) {
        OtpService.IssuedOtp otp = otpService.reissue(challengeId);
        AdminUser admin = adminUsers.findById(otp.adminUserId())
                .orElseThrow(() -> new ValidationException("Invalid verification request"));
        sendOtpEmail(admin, otp.code());
        audit.record(admin.getId(), "LOGIN_OTP_RESENT", "OTP resent", ip, userAgent);
        return new ResendOtpResult(otp.otpId(), properties.otpInResponse() ? otp.code() : null);
    }

    /**
     * Exchange a valid refresh token for a new token pair (rotating the refresh token).
     *
     * <p>{@code @Transactional} here (default propagation) means the inner calls on
     * {@link RefreshTokenService} join this same transaction/connection instead of each opening
     * their own, so the pessimistic lock taken below is held for the whole method — across both
     * {@code validateActive} calls and the {@code rotate} call — not released early. This closes a
     * TOCTOU race against a concurrent password reset/change: that path takes the same per-admin
     * row lock before revoking refresh tokens and bumping the security version, so whichever side
     * gets the lock first is the one whose effects the other observes.</p>
     */
    @Transactional
    public AuthTokens refresh(String refreshToken, String ip, String userAgent) {
        AdminRefreshToken precheck = refreshTokenService.validateActive(refreshToken, ip, userAgent);
        AdminUser admin = adminUsers.findByIdForUpdate(precheck.getAdminUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!admin.isActive()) {
            refreshTokenService.revoke(refreshToken);
            throw new UnauthorizedException("Account is disabled.");
        }
        // Re-validate now that we hold the admin row lock: a concurrent password reset/change that
        // revoked this token had to acquire the same lock first, so if it already committed, THIS
        // second check (not the first one above) is what actually observes that revocation.
        AdminRefreshToken current = refreshTokenService.validateActive(refreshToken, ip, userAgent);
        RefreshTokenService.IssuedRefresh rotated = refreshTokenService.rotate(current, ip, userAgent);
        audit.record(admin.getId(), "TOKEN_REFRESHED", null, ip, userAgent);
        return buildTokens(admin, rotated.token());
    }

    /** Revoke a refresh token (logout). */
    public void logout(String refreshToken, String ip, String userAgent) {
        refreshTokenService.revoke(refreshToken)
                .ifPresent(adminId -> audit.record(adminId, "LOGOUT", null, ip, userAgent));
    }

    /** Always succeeds (no account enumeration); emails a reset link only if the email exists. */
    public void forgotPassword(String email, String ip, String userAgent) {
        adminUsers.findByEmailIgnoreCase(email).ifPresent(admin -> {
            String token = OpaqueTokens.generate();
            authTokens.save(new AdminAuthToken(admin.getId(), OpaqueTokens.sha256Hex(token),
                    AuthTokenType.PASSWORD_RESET, clock.instant().plus(properties.passwordResetTtl())));
            String resetUrl = frontendBaseUrl + "/admin/reset-password?token=" + token;
            notificationService.send(new SendEmailCommand(RESET_TEMPLATE, admin.getEmail(), Map.of(
                    "name", admin.getFullName(),
                    "resetUrl", resetUrl,
                    "minutes", properties.passwordResetTtl().toMinutes())));
            audit.record(admin.getId(), "PASSWORD_RESET_REQUESTED", null, ip, userAgent);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword, String ip, String userAgent) {
        String tokenHash = OpaqueTokens.sha256Hex(token);
        AdminAuthToken precheck = authTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired reset link"));
        if (precheck.getType() != AuthTokenType.PASSWORD_RESET) {
            throw new ValidationException("Invalid or expired reset link");
        }
        // Pessimistic lock: the other half of the refresh()/reset race fix — both sides lock the
        // same admin row, so whichever commits first is the one the other observes.
        AdminUser admin = adminUsers.findByIdForUpdate(precheck.getAdminUserId())
                .orElseThrow(() -> new ValidationException("Invalid or expired reset link"));
        // Re-read and re-validate the token now that we hold the admin lock: a concurrent reset
        // using a sibling reset token could have invalidated this one (via invalidateActiveTokens
        // below) while this request was waiting for the lock — only a fresh read after the lock,
        // not the pre-lock snapshot above, actually observes that.
        AdminAuthToken authToken = authTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired reset link"));
        if (!authToken.isUsable(clock.instant())) {
            throw new ValidationException("Invalid or expired reset link");
        }
        admin.changePassword(passwordEncoder.encode(newPassword));
        admin.bumpSecurityVersion();
        adminUsers.save(admin);
        authToken.markUsed(clock.instant());
        authTokens.save(authToken);
        refreshTokenService.revokeAllForAdmin(admin.getId());
        authTokens.invalidateActiveTokens(admin.getId(), AuthTokenType.PASSWORD_RESET, clock.instant());
        otpService.invalidateAllActiveForAdmin(admin.getId());
        notificationService.enqueue(new SendEmailCommand(CHANGED_TEMPLATE, admin.getEmail(),
                Map.of("name", admin.getFullName())));
        audit.record(admin.getId(), "PASSWORD_RESET", null, ip, userAgent);
    }

    /**
     * Completes a staff invite issued by {@code AdminStaffService.create}: sets the account's first real
     * password (it was created with a random, never-disclosed one). Deliberately separate from
     * {@link #resetPassword}, which only accepts {@code PASSWORD_RESET} tokens - keeping the two
     * apart avoids touching that method's already-reasoned-through locking behavior, and keeps the
     * audit trail honest about which event actually happened.
     */
    @Transactional
    public void acceptInvite(String token, String password, String ip, String userAgent) {
        String tokenHash = OpaqueTokens.sha256Hex(token);
        AdminAuthToken precheck = authTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired invite link"));
        if (precheck.getType() != AuthTokenType.INVITE) {
            throw new ValidationException("Invalid or expired invite link");
        }
        // Same pessimistic-lock-then-re-read pattern as resetPassword, for the same reason: a
        // concurrent use of a sibling invite token (AdminStaffService.resendInvite, if/when it exists)
        // must not race this one.
        AdminUser admin = adminUsers.findByIdForUpdate(precheck.getAdminUserId())
                .orElseThrow(() -> new ValidationException("Invalid or expired invite link"));
        AdminAuthToken authToken = authTokens.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ValidationException("Invalid or expired invite link"));
        if (!authToken.isUsable(clock.instant())) {
            throw new ValidationException("Invalid or expired invite link");
        }
        admin.changePassword(passwordEncoder.encode(password));
        admin.bumpSecurityVersion();
        adminUsers.save(admin);
        authToken.markUsed(clock.instant());
        authTokens.save(authToken);
        authTokens.invalidateActiveTokens(admin.getId(), AuthTokenType.INVITE, clock.instant());
        audit.record(admin.getId(), "ADMIN_STAFF_INVITE_ACCEPTED", null, ip, userAgent);
    }

    private void sendOtpEmail(AdminUser admin, String code) {
        notificationService.send(new SendEmailCommand(OTP_TEMPLATE, admin.getEmail(), Map.of(
                "name", admin.getFullName(),
                "otp", code,
                "minutes", properties.otpTtl().toMinutes())));
    }

    private AuthTokens issueTokens(AdminUser admin, String ip, String userAgent) {
        RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(admin.getId(), ip, userAgent);
        return buildTokens(admin, refresh.token());
    }

    private AuthTokens buildTokens(AdminUser admin, String refreshToken) {
        List<String> authorities = admin.isSuperAdmin()
                ? List.of("SUPER_ADMIN")
                : adminUsers.findPermissionCodes(admin.getId());
        List<String> roles = adminUsers.findRoleNames(admin.getId());
        AccessTokenService.IssuedAccessToken access = accessTokenService.issue(new AdminTokenClaims(
                admin.getId(), admin.getUsername(), admin.getEmail(), admin.getFullName(),
                admin.isSuperAdmin(), authorities, roles, admin.getSecurityVersion(), admin.isMustChangePassword()));
        AdminProfile profile = new AdminProfile(admin.getId(), admin.getUsername(), admin.getEmail(),
                admin.getFullName(), admin.isSuperAdmin(), roles, authorities, admin.isMustChangePassword());
        return new AuthTokens(access.token(), "Bearer", access.expiresInSeconds(), refreshToken, profile);
    }
}
