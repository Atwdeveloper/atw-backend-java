package in.anytimework.atwbackend.identity.infrastructure;

import in.anytimework.atwbackend.identity.application.AdminBootstrapProperties;
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

/**
 * Refuses to start the application outside the "local"/"test" profiles if the JWT signing secret
 * or the bootstrap super-admin password is missing, too weak, or still the known local-dev literal.
 *
 * <p>Runs before every other {@link ApplicationRunner} (notably {@link AdminBootstrapRunner}),
 * so a misconfigured deployment never gets as far as creating a super-admin with a known password
 * or issuing a JWT signed with a known key.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    /** Must match the literal in application-local.properties. */
    private static final String KNOWN_LOCAL_JWT_SECRET =
            "dev-only-insecure-change-me-atw-backend-hs256-secret-key-0123456789";
    private static final String KNOWN_LOCAL_BOOTSTRAP_PASSWORD = "ChangeMe@12345";
    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a key >= 256 bits.

    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final AdminBootstrapProperties bootstrapProperties;

    public StartupSecurityValidator(Environment environment, JwtProperties jwtProperties,
                                    AdminBootstrapProperties bootstrapProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            // application-local.properties (packaged in every build, same as any other classpath
            // resource) supplies a known JWT secret and bootstrap password the instant "local" is
            // active — by design, for zero-config local dev. If this ever logs outside a developer's
            // own machine, SPRING_PROFILES_ACTIVE was set wrong and this instance is running with a
            // publicly-known signing secret: treat it as a live incident, not a warning to read later.
            log.error("Active profile is \"local\" — using known, publicly-committed dev secrets "
                    + "(JWT signing key, bootstrap admin password). This MUST NEVER be the active "
                    + "profile outside a developer's own machine.");
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
    }
}
