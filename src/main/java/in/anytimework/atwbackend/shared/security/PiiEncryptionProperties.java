package in.anytimework.atwbackend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key for encrypting sensitive PII at rest (Aadhaar/PAN/UAN/PF/ESIC numbers, bank account numbers),
 * bound from {@code atw.security.pii-encryption.key}. Base64-encoded, must decode to exactly 32
 * bytes (AES-256). No insecure fallback outside local/test - see StartupSecurityValidator, which
 * enforces this the same way it already does for the JWT signing secret.
 */
@ConfigurationProperties(prefix = "atw.security.pii-encryption")
public record PiiEncryptionProperties(String key) {
}
