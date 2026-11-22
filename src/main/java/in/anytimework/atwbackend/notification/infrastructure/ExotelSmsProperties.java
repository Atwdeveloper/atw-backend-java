package in.anytimework.atwbackend.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for Exotel (the legacy app's SMS/OTP vendor), bound from {@code atw.sms.exotel.*}.
 * Carried over from the legacy Laravel app's own {@code config/services.php}/{@code .env} - same
 * field shape (api key + token for HTTP Basic auth, account SID, regional subdomain, sender id, DLT
 * template id), confirmed with the developer 2026-11-11 to reuse those exact credential values via
 * environment variables (never hardcoded here or in any committed file).
 */
@ConfigurationProperties(prefix = "atw.sms.exotel")
public record ExotelSmsProperties(
        String apiKey,
        String apiToken,
        String sid,
        String subdomain,
        String senderId,
        String dltTemplateId,
        Duration timeout
) {
    public ExotelSmsProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(15);
        }
    }
}
