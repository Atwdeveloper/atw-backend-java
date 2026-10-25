package in.anytimework.atwbackend.identity.infrastructure;

import in.anytimework.atwbackend.identity.application.AdminBootstrapProperties;
import in.anytimework.atwbackend.identity.application.AdminSecurityProperties;
import in.anytimework.atwbackend.shared.security.PiiEncryptionProperties;
import in.anytimework.atwbackend.shared.security.jwt.JwtProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Refuses to start the application outside the "local"/"test" profiles if any security-critical
 * setting is missing, too weak, or still a known local-dev literal: the JWT signing secret, the
 * bootstrap super-admin password, the PII encryption key, the test-only "OTP in API response"
 * shortcut being left on, or a fixed/static OTP value configured outside the dedicated "dev"
 * profile.
 *
 * <p>Runs before every other {@link ApplicationRunner} (notably {@link AdminBootstrapRunner}),
 * so a misconfigured deployment never gets as far as creating a super-admin with a known password,
 * issuing a JWT signed with a known key, encrypting PII with a known key, or exposing OTP codes in
 * API responses.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    /** Must match the literals in application-local.properties. */
    private static final String KNOWN_LOCAL_JWT_SECRET =
            "dev-only-insecure-change-me-atw-backend-hs256-secret-key-0123456789";
    private static final String KNOWN_LOCAL_BOOTSTRAP_PASSWORD = "ChangeMe@12345";
    private static final String KNOWN_LOCAL_PII_ENCRYPTION_KEY = "aMgtn19OYBxgsWujXQpe/YjMiaigx0E7r0DXa3kKrRQ=";
    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a key >= 256 bits.
    private static final int PII_KEY_BYTES = 32; // AES-256 requires exactly 256 bits.

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final AdminBootstrapProperties bootstrapProperties;
    private final PiiEncryptionProperties piiEncryptionProperties;
    private final AdminSecurityProperties adminSecurityProperties;

    public StartupSecurityValidator(Environment environment, JwtProperties jwtProperties,
                                    AdminBootstrapProperties bootstrapProperties,
                                    PiiEncryptionProperties piiEncryptionProperties,
                                    AdminSecurityProperties adminSecurityProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.bootstrapProperties = bootstrapProperties;
        this.piiEncryptionProperties = piiEncryptionProperties;
        this.adminSecurityProperties = adminSecurityProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            // application-local.properties (packaged in every build, same as any other classpath
            // resource) supplies known dev secrets the instant "local" is active — by design, for
            // zero-config local dev. If this ever logs outside a developer's own machine,
            // SPRING_PROFILES_ACTIVE was set wrong and this instance is running with publicly-known
            // keys and the OTP-in-response shortcut: treat it as a live incident, not a warning to
            // read later.
            log.error("Active profile is \"local\" — using known, publicly-committed dev secrets "
                    + "(JWT signing key, bootstrap admin password, PII encryption key) and the "
                    + "OTP-in-response test shortcut. This MUST NEVER be the active profile outside a "
                    + "developer's own machine.");
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return; // automated test run: application-test.properties owns this, nothing to warn about.
        }

        String secret = jwtProperties.secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "atw.security.jwt.secret (JWT_SECRET) is not set. Refusing to start outside the "
                    + "local/test profiles: a missing signing secret must never silently fall back to a "
                    + "default value.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "atw.security.jwt.secret (JWT_SECRET) is shorter than " + MIN_SECRET_BYTES
                    + " bytes, which is too weak for HS256.");
        }
        if (secret.equals(KNOWN_LOCAL_JWT_SECRET)) {
            throw new IllegalStateException(
                    "atw.security.jwt.secret (JWT_SECRET) is set to the publicly-committed local-dev "
                    + "value. Set a strong, unique secret for this environment.");
        }

        if (bootstrapProperties.enabled()) {
            String bootstrapPassword = bootstrapProperties.password();
            if (!StringUtils.hasText(bootstrapPassword)) {
                throw new IllegalStateException(
                        "atw.admin.bootstrap.enabled is true but atw.admin.bootstrap.password "
                        + "(ADMIN_BOOTSTRAP_PASSWORD) is not set.");
            }
            if (bootstrapPassword.equals(KNOWN_LOCAL_BOOTSTRAP_PASSWORD)) {
                throw new IllegalStateException(
                        "atw.admin.bootstrap.password (ADMIN_BOOTSTRAP_PASSWORD) is set to the "
                        + "publicly-committed local-dev value. Set a strong, unique password, or disable "
                        + "bootstrap (ADMIN_BOOTSTRAP_ENABLED=false) once the real super-admin exists.");
            }
        }

        validatePiiEncryptionKey();

        if (adminSecurityProperties.otpInResponse()) {
            throw new IllegalStateException(
                    "atw.admin.security.otp-in-response (ADMIN_OTP_IN_RESPONSE) is true. This returns "
                    + "login OTP codes directly in the API response, defeating 2FA entirely, and must "
                    + "never be enabled outside the local/test profiles.");
        }

        validateFixedOtp();
    }

    /**
     * {@code atw.admin.security.fixed-otp} (ADMIN_FIXED_OTP) makes every issued OTP a known, static
     * value - a standing bypass of 2FA for every admin account wherever it's active. It exists only
     * to unblock admin login on a deployed environment before a real email provider is configured,
     * so it is permitted under exactly one profile: "dev" (see application-dev.properties). This is
     * a separate, narrower allowance than the top-of-method local/test short-circuit - a "dev"
     * deployment still must satisfy every other check above (real JWT secret, real bootstrap
     * password, real PII key, otp-in-response off).
     */
    private void validateFixedOtp() {
        String fixedOtp = adminSecurityProperties.fixedOtp();
        if (!StringUtils.hasText(fixedOtp)) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException(
                    "atw.admin.security.fixed-otp (ADMIN_FIXED_OTP) is set, but the active profile is "
                    + "not \"dev\". A static, config-known OTP is a standing bypass of 2FA for every "
                    + "admin account, so it is only ever permitted under the dedicated \"dev\" profile - "
                    + "never on any profile a production deployment might also use.");
        }
        int expectedLength = adminSecurityProperties.otpLength();
        boolean allDigits = fixedOtp.chars().allMatch(Character::isDigit);
        if (!allDigits || fixedOtp.length() != expectedLength) {
            throw new IllegalStateException(
                    "atw.admin.security.fixed-otp (ADMIN_FIXED_OTP) must be exactly " + expectedLength
                    + " numeric digits, matching atw.admin.security.otp-length.");
        }
        log.error("Active profile is \"dev\" with ADMIN_FIXED_OTP set - every admin login/resend-otp "
                + "issues the fixed code \"{}\" instead of a random one. This is a deliberate, scoped "
                + "2FA weakening for pre-SMTP testing only and MUST be unset the moment real OTP "
                + "delivery (email) is available on this environment.", fixedOtp);
    }

    private void validatePiiEncryptionKey() {
        String key = piiEncryptionProperties.key();
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(
                    "atw.security.pii-encryption.key (PII_ENCRYPTION_KEY) is not set. Refusing to start "
                    + "outside the local/test profiles: staff KYC/bank data must never fall back to being "
                    + "stored unencrypted.");
        }
        if (key.equals(KNOWN_LOCAL_PII_ENCRYPTION_KEY)) {
            throw new IllegalStateException(
                    "atw.security.pii-encryption.key (PII_ENCRYPTION_KEY) is set to the "
                    + "publicly-committed local-dev value. Set a strong, unique key for this environment.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "atw.security.pii-encryption.key (PII_ENCRYPTION_KEY) is not valid base64.", e);
        }
        if (keyBytes.length != PII_KEY_BYTES) {
            throw new IllegalStateException(
                    "atw.security.pii-encryption.key (PII_ENCRYPTION_KEY) must decode to exactly "
                    + PII_KEY_BYTES + " bytes (AES-256); got " + keyBytes.length + ".");
        }
    }
}
