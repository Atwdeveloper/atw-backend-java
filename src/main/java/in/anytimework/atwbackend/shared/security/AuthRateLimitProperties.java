package in.anytimework.atwbackend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Request-volume limit for the public, abuse-prone admin auth endpoints (login, forgot-password,
 * reset-password, accept-invite), bound from {@code atw.admin.security.rate-limit.*}. Distinct
 * from the existing OTP-specific counters (max attempts per challenge, resend cooldown/cap): this
 * is a general per-IP request cap, enforced before those endpoints are even reached.
 */
@ConfigurationProperties(prefix = "atw.admin.security.rate-limit")
public record AuthRateLimitProperties(boolean enabled, int maxAttempts, Duration window) {
}
