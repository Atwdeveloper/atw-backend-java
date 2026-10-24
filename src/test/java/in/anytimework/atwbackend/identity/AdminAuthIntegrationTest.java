package in.anytimework.atwbackend.identity;

import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminOtp;
import in.anytimework.atwbackend.identity.domain.AdminOtpRepository;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.notification.CapturingEmailSender;
import in.anytimework.atwbackend.shared.exception.BusinessRuleException;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
import in.anytimework.atwbackend.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end admin auth against the real local Postgres: seeded template + roles, full
 * login -> email OTP -> JWT -> refresh (rotation) -> logout, decoding the issued token.
 * A capturing EmailSender lets the test read the OTP that would have been emailed.
 *
 * <p>{@code otp-resend-interval} is overridden to {@code PT0S} for this whole class: the default
 * (60s, see application.properties) exists to throttle real abuse, but it would otherwise force
 * every resend-related test here to either sleep for real seconds or reach for a separate
 * clock-controlled {@link OtpService}. Nothing else in this class depends on the real interval.</p>
 */
@SpringBootTest(properties = "atw.admin.security.otp-resend-interval=PT0S")
@ActiveProfiles("test")
@Import(AdminAuthIntegrationTest.EmailTestConfig.class)
class AdminAuthIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");
    private static final Pattern RESET_TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private AuthService authService;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private AdminOtpRepository adminOtps;
    @Autowired
    private AdminRoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private CapturingEmailSender emailSender;

    private AdminUser createAdmin(String password, String roleName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminRole role = roles.findByName(roleName).orElseThrow();
        AdminUser admin = new AdminUser("it-" + suffix, "it-" + suffix + "@example.com",
                passwordEncoder.encode(password), "IT Admin " + suffix, null, false, false);
        admin.assignRole(role);
        return adminUsers.save(admin);
    }

    private static String extractOtp(String html) {
        Matcher m = SIX_DIGITS.matcher(html);
        assertThat(m.find()).as("OTP present in email body").isTrue();
        return m.group(1);
    }

    private static String extractResetToken(String html) {
        Matcher m = RESET_TOKEN.matcher(html);
        assertThat(m.find()).as("reset token present in email body").isTrue();
        return m.group(1);
    }

    /** Any 6-digit code guaranteed to differ from {@code correctCode}. */
    private static String wrongCodeFor(String correctCode) {
        return "000000".equals(correctCode) ? "111111" : "000000";
    }

    @Test
    void fullLoginOtpJwtRefreshLogoutFlow() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        // 1) password login -> OTP challenge
        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");
        assertThat(login.otpRequired()).isTrue();
        assertThat(login.challengeId()).isNotNull();

        // 2) read the OTP from the (captured) email and verify it -> tokens
        String otp = extractOtp(emailSender.last().htmlBody());
        AuthTokens tokens = authService.verifyOtp(login.challengeId(), otp, "127.0.0.1", "JUnit");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.admin().email()).isEqualTo(admin.getEmail());

        // 3) the access token is a valid JWT carrying subject + authorities from the role
        Jwt jwt = jwtDecoder.decode(tokens.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(admin.getId().toString());
        assertThat(jwt.getClaimAsBoolean("super_admin")).isFalse();
        assertThat(jwt.getClaimAsStringList("authorities")).contains("admin_staff.view", "admin_roles.view");

        // 4) refresh rotates: a new token works, the old refresh token is now rejected
        AuthTokens refreshed = authService.refresh(tokens.refreshToken(), "127.0.0.1", "JUnit");
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        // 5) logout revokes the current refresh token
        authService.logout(refreshed.refreshToken(), "127.0.0.1", "JUnit");
        assertThatThrownBy(() -> authService.refresh(refreshed.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void replayingAnAlreadyRotatedRefreshToken_revokesTheWholeSessionFamily() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");
        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");
        String otp = extractOtp(emailSender.last().htmlBody());
        AuthTokens original = authService.verifyOtp(login.challengeId(), otp, "127.0.0.1", "JUnit");

        // original.refreshToken() rotates to "current" - the live end of the family.
        AuthTokens current = authService.refresh(original.refreshToken(), "127.0.0.1", "JUnit");

        // Replaying the dead, already-rotated-away original token is treated as theft: it doesn't
        // just fail for itself, it kills the live descendant too, forcing a fresh login either way.
        assertThatThrownBy(() -> authService.refresh(original.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> authService.refresh(current.refreshToken(), "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void wrongPasswordIsRejectedAndCounted() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        assertThatThrownBy(() -> authService.login(admin.getEmail(), "WrongPass@1", "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        AdminUser reloaded = adminUsers.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void otpAttemptCounterPersistsAndBlocksEvenTheCorrectCodeAfterTooManyFailures() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");
        String correctOtp = extractOtp(emailSender.last().htmlBody());
        String wrongOtp = wrongCodeFor(correctOtp);

        // otpMaxAttempts is 5 (application.properties default): five wrong codes, each persisted in turn.
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> authService.verifyOtp(login.challengeId(), wrongOtp, "127.0.0.1", "JUnit"))
                    .as("wrong-code attempt %d", attempt)
                    .isInstanceOf(ValidationException.class);
        }

        AdminOtp persisted = adminOtps.findById(login.challengeId()).orElseThrow();
        assertThat(persisted.getAttempts()).isEqualTo(5);

        // The cap itself is now what blocks verification - not the code, even though this one is correct.
        assertThatThrownBy(() -> authService.verifyOtp(login.challengeId(), correctOtp, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resendingOtpInvalidatesTheOriginalChallenge() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");
        UUID originalChallengeId = login.challengeId();

        UUID newChallengeId = authService.resendOtp(originalChallengeId, "127.0.0.1", "JUnit").challengeId();
        assertThat(newChallengeId).isNotEqualTo(originalChallengeId);
        String newOtp = extractOtp(emailSender.last().htmlBody());

        // The original challenge was consumed by the resend, so it is now dead - any code fails.
        assertThatThrownBy(() -> authService.verifyOtp(originalChallengeId, newOtp, "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);

        // The freshly-issued challenge from the resend is the one that actually works.
        AuthTokens tokens = authService.verifyOtp(newChallengeId, newOtp, "127.0.0.1", "JUnit");
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void passwordResetInvalidatesSiblingResetTokensAndPendingOtpChallenge() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");
        int originalSecurityVersion = admin.getSecurityVersion();

        // Two independent "forgot password" requests for the same admin -> two live reset tokens.
        authService.forgotPassword(admin.getEmail(), "127.0.0.1", "JUnit");
        String firstResetToken = extractResetToken(emailSender.last().htmlBody());
        authService.forgotPassword(admin.getEmail(), "127.0.0.1", "JUnit");
        String secondResetToken = extractResetToken(emailSender.last().htmlBody());

        // A login started before the reset, left pending (no OTP submitted yet).
        LoginResult pendingLogin = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");

        authService.resetPassword(secondResetToken, "NewSecret@6789", "127.0.0.1", "JUnit");

        // The sibling reset link is now dead too, even though it was never itself submitted.
        assertThatThrownBy(() -> authService.resetPassword(firstResetToken, "Other@12345", "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);

        // So is the login OTP challenge that was still pending when the reset happened.
        assertThatThrownBy(() -> authService.verifyOtp(pendingLogin.challengeId(), "000000", "127.0.0.1", "JUnit"))
                .isInstanceOf(ValidationException.class);

        // Proof the invalidation mechanism actually fired: every token/session minted before this
        // point now carries a stale security version.
        AdminUser reloaded = adminUsers.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getSecurityVersion()).isGreaterThan(originalSecurityVersion);
    }

    @Test
    void resendChainCannotExceedConfiguredMaxResends() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");

        // otpMaxResends is 3 (application.properties default): 3 resends in a chain succeed...
        UUID challengeId = login.challengeId();
        for (int resend = 1; resend <= 3; resend++) {
            challengeId = authService.resendOtp(challengeId, "127.0.0.1", "JUnit").challengeId();
        }

        // ...but the next one in the same chain is one too many.
        UUID lastChallengeId = challengeId;
        assertThatThrownBy(() -> authService.resendOtp(lastChallengeId, "127.0.0.1", "JUnit"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @TestConfiguration
    static class EmailTestConfig {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }
}
