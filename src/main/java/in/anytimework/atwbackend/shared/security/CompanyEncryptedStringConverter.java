package in.anytimework.atwbackend.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Transparent AES-256-GCM encryption for a company-PII String column — applied per-field via
 * {@code @Convert(converter = CompanyEncryptedStringConverter.class)}, not {@code autoApply}.
 * Identical mechanism to {@link EncryptedStringConverter}/{@link WorkerEncryptedStringConverter},
 * keyed separately - see {@link PiiEncryptionConfig} for why.
 *
 * <p>Building this class is not, by itself, enough to encrypt anything - it must be explicitly
 * applied via {@code @Convert} on each field that needs it ({@code Company.gstNumber}/
 * {@code Company.panNumber}). This exact mistake (a converter built, never applied, the field
 * silently stored in plaintext) has happened three times before in this project's history.</p>
 */
@Component
@Converter(autoApply = false)
public class CompanyEncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiEncryptor encryptor;

    public CompanyEncryptedStringConverter(@Qualifier("companyPiiEncryptor") PiiEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}
