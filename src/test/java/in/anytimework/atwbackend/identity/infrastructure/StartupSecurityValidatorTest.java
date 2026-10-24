package in.anytimework.atwbackend.identity.infrastructure;

import in.anytimework.atwbackend.identity.application.AdminBootstrapProperties;
import in.anytimework.atwbackend.identity.application.AdminSecurityProperties;
import in.anytimework.atwbackend.shared.security.PiiEncryptionProperties;
import in.anytimework.atwbackend.shared.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the strict outside-local/test checks (the PII encryption key and the
 * otp-in-response shortcut, added alongside the JWT secret / bootstrap password checks). local/test
 * themselves short-circuit before any of this runs - every other test in this codebase runs under
 * "test" and never reaches these checks - so this simulates "neither profile active" to exercise
 * them directly.
 */
class StartupSecurityValidatorTest {

    private static final String VALID_JWT_SECRET = "a-sufficiently-long-and-unique-production-jwt-secret-value";
    private static final String VALID_PII_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final Environment environment = mock(Environment.class);
    private final AdminBootstrapProperties bootstrapProperties =
            new AdminBootstrapProperties(false, null, null, null, null);

    private StartupSecurityValidator validator(PiiEncryptionProperties pii, AdminSecurityProperties admin) {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);
        JwtProperties jwt = new JwtProperties(VALID_JWT_SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "atw-backend");
        return new StartupSecurityValidator(environment, jwt, bootstrapProperties, pii, admin);
    }

    private static AdminSecurityProperties adminSecurity(boolean otpInResponse) {
        return new AdminSecurityProperties(true, Duration.ofMinutes(10), 6, 5, Duration.ofSeconds(60), 3, 5,
                Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofDays(3), otpInResponse);
    }

    @Test
    void allValid_doesNotThrow() {
        StartupSecurityValidator v = validator(new PiiEncryptionProperties(VALID_PII_KEY), adminSecurity(false));
        assertThatCode(() -> v.run(null)).doesNotThrowAnyException();
    }

    @Test
    void missingPiiKey_throws() {
        StartupSecurityValidator v = validator(new PiiEncryptionProperties(null), adminSecurity(false));
        assertThatThrownBy(() -> v.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PII_ENCRYPTION_KEY");
    }

    @Test
    void piiKeyWrongByteLength_throws() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        StartupSecurityValidator v = validator(new PiiEncryptionProperties(tooShort), adminSecurity(false));
        assertThatThrownBy(() -> v.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void piiKeyEqualsKnownLocalLiteral_throws() {
        StartupSecurityValidator v = validator(
                new PiiEncryptionProperties("aMgtn19OYBxgsWujXQpe/YjMiaigx0E7r0DXa3kKrRQ="), adminSecurity(false));
        assertThatThrownBy(() -> v.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicly-committed local-dev value");
    }

    @Test
    void piiKeyNotValidBase64_throws() {
        StartupSecurityValidator v = validator(new PiiEncryptionProperties("not-valid-base64!!"), adminSecurity(false));
        assertThatThrownBy(() -> v.run(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void otpInResponseEnabled_throws() {
        StartupSecurityValidator v = validator(new PiiEncryptionProperties(VALID_PII_KEY), adminSecurity(true));
        assertThatThrownBy(() -> v.run(null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otp-in-response");
    }
}
