package in.anytimework.atwbackend.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the three independently-keyed {@link PiiEncryptor} instances: one for platform-staff PII
 * (identity module - Aadhaar/PAN/UAN/PF/ESIC, bank account number, address lines), one for worker PII
 * (same categories, but a dataset that will vastly outscale staff records in both row count and
 * external exposure), and one for company PII (GST/PAN). Keeping them on separate keys limits the
 * blast radius of any one key being compromised to just that one dataset.
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

    @Bean
    public PiiEncryptor companyPiiEncryptor(CompanyPiiEncryptionProperties properties) {
        return new PiiEncryptor(properties.key(), "atw.security.company-pii-encryption.key");
    }
}
