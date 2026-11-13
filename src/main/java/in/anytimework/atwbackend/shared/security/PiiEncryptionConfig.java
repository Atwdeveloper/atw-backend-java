package in.anytimework.atwbackend.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the two independently-keyed {@link PiiEncryptor} instances: one for platform-staff PII
 * (identity module - Aadhaar/PAN/UAN/PF/ESIC, bank account number, address lines) and one for
 * worker PII (same categories, but a dataset that will vastly outscale staff records in both row
 * count and external exposure). Keeping them on separate keys limits the blast radius of either key
 * being compromised to just that one dataset.
 */
@Configuration
public class PiiEncryptionConfig {

    @Bean
    public PiiEncryptor identityPiiEncryptor(PiiEncryptionProperties properties) {
        return new PiiEncryptor(properties.key(), "atw.security.pii-encryption.key");
    }

    @Bean
    public PiiEncryptor workerPiiEncryptor(WorkerPiiEncryptionProperties properties) {
        return new PiiEncryptor(properties.key(), "atw.security.worker-pii-encryption.key");
    }
}
