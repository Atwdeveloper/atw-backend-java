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
        int otpMaxResends,
        // maxFailedLogins/lockoutDuration: no longer read anywhere (account-level lockout was
        // removed from AuthService.login). Left bound so an environment that still sets
        // ADMIN_MAX_FAILED_LOGINS/ADMIN_LOCKOUT_DURATION doesn't need to change anything; they're
        // just inert now.
        int maxFailedLogins,
        Duration lockoutDuration,
        Duration passwordResetTtl,
        Duration inviteTtl,
        boolean otpInResponse,
        String fixedOtp
) {
}
