package in.anytimework.atwbackend.identity.infrastructure;

import in.anytimework.atwbackend.identity.application.AdminBootstrapProperties;
import in.anytimework.atwbackend.identity.domain.AdminUser;
import in.anytimework.atwbackend.identity.domain.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first super-admin on startup when the admin table is empty, from
 * {@code atw.admin.bootstrap.*}. Idempotent: does nothing once any admin exists.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminUserRepository adminUsers;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    public AdminBootstrapRunner(AdminUserRepository adminUsers, PasswordEncoder passwordEncoder,
                                AdminBootstrapProperties properties) {
        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled() || adminUsers.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("Bootstrap enabled but email/password not configured; skipping super-admin creation.");
            return;
        }
        AdminUser superAdmin = new AdminUser(
                properties.username(),
                properties.email(),
                passwordEncoder.encode(properties.password()),
                properties.fullName(),
                null,
                true,  // super-admin
                true); // must change password on first login
        adminUsers.save(superAdmin);
        log.info("Bootstrapped super-admin '{}' ({}). It must change its password on first login.",
                properties.username(), properties.email());
    }
}
