package in.anytimework.atwbackend.identity;

import in.anytimework.atwbackend.identity.application.AuthService;
import in.anytimework.atwbackend.identity.application.dto.LoginResult;
import in.anytimework.atwbackend.identity.application.dto.ResendOtpResult;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import in.anytimework.atwbackend.identity.domain.AdminRole;
import in.anytimework.atwbackend.identity.domain.AdminRoleRepository;
import in.anytimework.atwbackend.notification.CapturingEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The local-dev convenience that puts the OTP code directly in the login/resend-otp API response
 * (no SMTP needed to exercise the flow) - gated by atw.admin.security.otp-in-response, which
 * StartupSecurityValidator refuses to let run outside local/test (see StartupSecurityValidatorTest).
 */
@SpringBootTest(properties = {
        "atw.admin.security.otp-in-response=true",
        "atw.admin.security.otp-resend-interval=PT0S"
})
@ActiveProfiles("test")
@Import(OtpInResponseIntegrationTest.EmailTestConfig.class)
class OtpInResponseIntegrationTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired
    private AuthService authService;
    @Autowired
    private AdminUserRepository adminUsers;
    @Autowired
    private AdminRoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CapturingEmailSender emailSender;

    private AdminUser createAdmin(String password) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AdminRole role = roles.findByName("Sub Admin").orElseThrow();
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
    void login_includesTheOtpInTheResponse_matchingWhatWasEmailed() {
        AdminUser admin = createAdmin("Secret@12345");

        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");

        assertThat(login.testOtp()).isNotBlank();
        assertThat(login.testOtp()).isEqualTo(extractOtp(emailSender.last().htmlBody()));
    }

    @Test
    void resendOtp_alsoIncludesTheFreshOtpInTheResponse() {
        AdminUser admin = createAdmin("Secret@12345");
        LoginResult login = authService.login(admin.getEmail(), "Secret@12345", "127.0.0.1", "JUnit");

        ResendOtpResult resend = authService.resendOtp(login.challengeId(), "127.0.0.1", "JUnit");

        assertThat(resend.testOtp()).isNotBlank();
        assertThat(resend.testOtp()).isEqualTo(extractOtp(emailSender.last().htmlBody()));
        // Proves the response OTP is actually usable, not just present.
        assertThat(authService.verifyOtp(resend.challengeId(), resend.testOtp(), "127.0.0.1", "JUnit").accessToken())
                .isNotBlank();
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
