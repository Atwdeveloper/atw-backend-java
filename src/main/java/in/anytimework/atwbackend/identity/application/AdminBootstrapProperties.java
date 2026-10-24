package in.anytimework.atwbackend.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the one-time bootstrap super-admin, bound from {@code atw.admin.bootstrap.*}.
 */
@ConfigurationProperties(prefix = "atw.admin.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String username,
        String email,
        String password,
        String fullName
) {
}
