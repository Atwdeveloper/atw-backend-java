package in.anytimework.atwbackend.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Key for encrypting company PII at rest (GST/PAN numbers), bound from
 * {@code atw.security.company-pii-encryption.key}. Base64-encoded, must decode to exactly 32 bytes
 * (AES-256). Deliberately a third, independent key from {@link PiiEncryptionProperties} (platform-staff
 * PII) and {@link WorkerPiiEncryptionProperties} (worker PII) - same blast-radius-bounding reasoning:
 * a compromise of one key never also exposes either of the other two datasets. No insecure fallback
 * outside local/test - see {@code StartupSecurityValidator}, which enforces this the same way it
 * already does for the other two PII keys and the JWT signing secret.
 */
@ConfigurationProperties(prefix = "atw.security.company-pii-encryption")
public record CompanyPiiEncryptionProperties(String key) {
}
