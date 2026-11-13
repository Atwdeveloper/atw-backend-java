package in.anytimework.atwbackend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key for encrypting worker PII at rest (Aadhaar/PAN numbers, bank account numbers, address lines),
 * bound from {@code atw.security.worker-pii-encryption.key}. Base64-encoded, must decode to exactly
 * 32 bytes (AES-256). Deliberately a separate key from {@link PiiEncryptionProperties} (used for
 * platform-staff PII): worker records will vastly outscale staff records in both row count and
 * external exposure, so a compromise of one key does not also expose the other dataset. No insecure
 * fallback outside local/test - see StartupSecurityValidator, which enforces this the same way it
 * already does for the identity PII key and the JWT signing secret.
 */
@ConfigurationProperties(prefix = "atw.security.worker-pii-encryption")
public record WorkerPiiEncryptionProperties(String key) {
}
