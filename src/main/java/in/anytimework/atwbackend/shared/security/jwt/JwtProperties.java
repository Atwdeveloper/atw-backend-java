package in.anytimework.atwbackend.shared.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings bound from {@code atw.security.jwt.*}.
 *
 * @param secret    HS256 signing secret (>= 32 bytes); supply via env / Key Vault in real environments
 * @param accessTtl access-token lifetime (short, e.g. 15m)
 * @param refreshTtl refresh-token lifetime (longer, e.g. 7d)
 * @param issuer    the {@code iss} claim
 */
@ConfigurationProperties(prefix = "atw.security.jwt")
public record JwtProperties(String secret, Duration accessTtl, Duration refreshTtl, String issuer) {
}
