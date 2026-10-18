package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.application.dto.AdminProfile;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AuthToken;
import in.anytimework.atwbackend.identity.domain.AuthTokenRepository;
import in.anytimework.atwbackend.identity.domain.AuthTokenType;
import in.anytimework.atwbackend.identity.domain.RefreshToken;
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
    private final AuthTokenRepository authTokens;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final AdminAuditService audit;
    private final AdminSecurityProperties properties;
    private final Clock clock;
    private final String frontendBaseUrl;

    public AuthService(AdminUserRepository adminUsers, AuthTokenRepository authTokens, OtpService otpService,
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
        return LoginResult.otpChallenge(otp.otpId());
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
    public UUID resendOtp(UUID challengeId, String ip, String userAgent) {
        OtpService.IssuedOtp otp = otpService.reissue(challengeId);
        AdminUser admin = adminUsers.findById(otp.adminUserId())
                .orElseThrow(() -> new ValidationException("Invalid verification request"));
        sendOtpEmail(admin, otp.code());
        audit.record(admin.getId(), "LOGIN_OTP_RESENT", "OTP resent", ip, userAgent);
        return otp.otpId();
    }

    /** Exchange a valid refresh token for a new token pair (rotating the refresh token). */
    public AuthTokens refresh(String refreshToken, String ip, String userAgent) {
        RefreshToken current = refreshTokenService.validateActive(refreshToken);
        AdminUser admin = adminUsers.findById(current.getAdminUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!admin.isActive()) {
            refreshTokenService.revoke(refreshToken);
            throw new UnauthorizedException("Account is disabled.");
        }
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
            authTokens.save(new AuthToken(admin.getId(), OpaqueTokens.sha256Hex(token),
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
        AuthToken authToken = authTokens.findByTokenHash(OpaqueTokens.sha256Hex(token))
                .orElseThrow(() -> new ValidationException("Invalid or expired reset link"));
        if (authToken.getType() != AuthTokenType.PASSWORD_RESET || !authToken.isUsable(clock.instant())) {
            throw new ValidationException("Invalid or expired reset link");
        }
        AdminUser admin = adminUsers.findById(authToken.getAdminUserId())
                .orElseThrow(() -> new ValidationException("Invalid or expired reset link"));
        admin.changePassword(passwordEncoder.encode(newPassword));
        adminUsers.save(admin);
        authToken.markUsed(clock.instant());
        authTokens.save(authToken);
        refreshTokenService.revokeAllForAdmin(admin.getId());
        notificationService.enqueue(new SendEmailCommand(CHANGED_TEMPLATE, admin.getEmail(),
                Map.of("name", admin.getFullName())));
        audit.record(admin.getId(), "PASSWORD_RESET", null, ip, userAgent);
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
                admin.isSuperAdmin(), authorities, roles));
        AdminProfile profile = new AdminProfile(admin.getId(), admin.getUsername(), admin.getEmail(),
                admin.getFullName(), admin.isSuperAdmin(), roles, authorities);
        return new AuthTokens(access.token(), "Bearer", access.expiresInSeconds(), refreshToken, profile);
    }
}
