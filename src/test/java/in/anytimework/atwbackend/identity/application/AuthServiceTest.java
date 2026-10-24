package in.anytimework.atwbackend.identity.application;

import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminAuthTokenRepository;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.notification.application.NotificationService;
import in.anytimework.atwbackend.notification.application.SendEmailCommand;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import in.anytimework.atwbackend.shared.security.jwt.AccessTokenService;
import in.anytimework.atwbackend.shared.security.jwt.AdminTokenClaims;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the auth orchestration (no Spring, no DB, no real email/JWT). */
class AuthServiceTest {

    private final AdminUserRepository adminUsers = mock(AdminUserRepository.class);
    private final AdminAuthTokenRepository authTokens = mock(AdminAuthTokenRepository.class);
    private final OtpService otpService = mock(OtpService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
            mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdminAuditService audit = mock(AdminAuditService.class);
    private final AdminSecurityProperties props = new AdminSecurityProperties(
            true, Duration.ofMinutes(10), 6, 5, Duration.ofSeconds(60), 3, 5, Duration.ofMinutes(15),
            Duration.ofMinutes(30), Duration.ofDays(3), false);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final AuthService authService = new AuthService(adminUsers, authTokens, otpService, refreshTokenService,
            accessTokenService, passwordEncoder, notificationService, audit, props, clock, "http://localhost:3000");

    private AdminUser activeAdmin() {
        return new AdminUser("itadmin", "admin@example.com", "HASH", "IT Admin", null, false, false);
    }

    @Test
    void login_withUnknownIdentifier_is401AndAudited() {
        when(adminUsers.findByUsernameIgnoreCaseOrEmailIgnoreCase("nobody", "nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody", "pw", "ip", "ua"))
                .isInstanceOf(UnauthorizedException.class);

        verify(audit).record(isNull(), eq("LOGIN_FAILED"), anyString(), any(), any());
    }

    @Test
    void login_withWrongPassword_countsFailureAnd401() {
        AdminUser admin = activeAdmin();
        when(adminUsers.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin@example.com", "admin@example.com"))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("bad", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("admin@example.com", "bad", "ip", "ua"))
                .isInstanceOf(UnauthorizedException.class);

        assertThat(admin.getFailedLoginAttempts()).isEqualTo(1);
        verify(adminUsers).save(admin);
        verify(otpService, never()).issue(any());
    }

    @Test
    void login_success_sendsOtpAndReturnsChallenge() {
        AdminUser admin = activeAdmin();
        when(adminUsers.findByUsernameIgnoreCaseOrEmailIgnoreCase("admin@example.com", "admin@example.com"))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("pw", "HASH")).thenReturn(true);
        UUID challengeId = UUID.randomUUID();
        when(otpService.issue(any())).thenReturn(new OtpService.IssuedOtp(challengeId, null, "123456"));

        LoginResult result = authService.login("admin@example.com", "pw", "ip", "ua");

        assertThat(result.otpRequired()).isTrue();
        assertThat(result.challengeId()).isEqualTo(challengeId);
        verify(notificationService).send(any(SendEmailCommand.class));
        verify(audit).record(any(), eq("LOGIN_OTP_SENT"), anyString(), any(), any());
    }

    @Test
    void verifyOtp_issuesTokens() {
        AdminUser admin = activeAdmin();
        UUID adminId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        when(otpService.verifyAndConsume(challengeId, "123456")).thenReturn(adminId);
        when(adminUsers.findById(adminId)).thenReturn(Optional.of(admin));
        when(adminUsers.findPermissionCodes(any())).thenReturn(List.of("admin_staff.view", "admin_roles.view"));
        when(adminUsers.findRoleNames(any())).thenReturn(List.of("Sub Admin"));
        when(refreshTokenService.issue(any(), any(), any()))
                .thenReturn(new RefreshTokenService.IssuedRefresh("refresh-token", Instant.EPOCH, UUID.randomUUID()));
        when(accessTokenService.issue(any(AdminTokenClaims.class)))
                .thenReturn(new AccessTokenService.IssuedAccessToken("access-jwt", Instant.EPOCH, 900));

        AuthTokens tokens = authService.verifyOtp(challengeId, "123456", "ip", "ua");

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.admin().authorities()).contains("admin_staff.view", "admin_roles.view");
        verify(audit).record(any(), eq("LOGIN_SUCCESS"), anyString(), any(), any());
    }
}
