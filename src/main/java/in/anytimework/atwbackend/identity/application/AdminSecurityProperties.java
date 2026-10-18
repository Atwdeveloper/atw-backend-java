package in.anytimework.atwbackend.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Admin authentication policy, bound from {@code atw.admin.security.*}.
 */
@ConfigurationProperties(prefix = "atw.admin.security")
public record AdminSecurityProperties(
        boolean otpEnabled,
        Duration otpTtl,
        int otpLength,
        int otpMaxAttempts,
        Duration otpResendInterval,
        int maxFailedLogins,
        Duration lockoutDuration,
        Duration passwordResetTtl
) {
}
