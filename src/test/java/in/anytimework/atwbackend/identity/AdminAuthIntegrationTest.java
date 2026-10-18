package in.anytimework.atwbackend.identity;

import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.dto.AuthTokens;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.Role;
import in.anytimework.atwbackend.identity.domain.RoleRepository;
import in.anytimework.atwbackend.notification.application.EmailMessage;
import in.anytimework.atwbackend.notification.application.EmailSender;
import in.anytimework.atwbackend.shared.exception.UnauthorizedException;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end admin auth against the real local Postgres: seeded template + roles, full
 * login -> email OTP -> JWT -> refresh (rotation) -> logout, decoding the issued token.
 * A capturing EmailSender lets the test read the OTP that would have been emailed.
 */
@SpringBootTest
@Import(AdminAuthIntegrationTest.EmailTestConfig.class)
class AdminAuthIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private AuthService authService;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private CapturingEmailSender emailSender;

    private AdminUser createAdmin(String password, String roleName) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Role role = roles.findByName(roleName).orElseThrow();
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
        assertThat(jwt.getClaimAsStringList("authorities")).contains("staff.view", "roles.view");

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
    void wrongPasswordIsRejectedAndCounted() {
        AdminUser admin = createAdmin("Secret@12345", "Sub Admin");

        assertThatThrownBy(() -> authService.login(admin.getEmail(), "WrongPass@1", "127.0.0.1", "JUnit"))
                .isInstanceOf(UnauthorizedException.class);

        AdminUser reloaded = adminUsers.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(1);
    }

    /** Captures outgoing emails so the test can read the OTP; overrides the dev log sender. */
    static class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        public String send(EmailMessage message) {
            sent.add(message);
            return "test-" + message.to();
        }

        EmailMessage last() {
            return sent.get(sent.size() - 1);
        }
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
