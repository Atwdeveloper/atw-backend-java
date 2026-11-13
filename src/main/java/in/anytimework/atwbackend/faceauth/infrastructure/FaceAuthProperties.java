package in.anytimework.atwbackend.faceauth.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for the external Python face-auth service, bound from {@code atw.face-auth.*}.
 * {@code timeout} defaults to 75s, matching the legacy integration's own value - per its own docs, the
 * service can be slow to wake up after being idle.
 */
@ConfigurationProperties(prefix = "atw.face-auth")
public record FaceAuthProperties(String baseUrl, String apiKey, Duration timeout) {

    public FaceAuthProperties {
        if (timeout == null) {
            timeout = Duration.ofSeconds(75);
        }
    }
}
