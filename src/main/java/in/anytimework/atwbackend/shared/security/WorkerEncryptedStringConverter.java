package in.anytimework.atwbackend.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Transparent AES-256-GCM encryption for a worker-PII String column — applied per-field via
 * {@code @Convert(converter = WorkerEncryptedStringConverter.class)}, not {@code autoApply}.
 * Identical mechanism to {@link EncryptedStringConverter}, keyed separately - see
 * {@link PiiEncryptionConfig} for why.
 */
@Component
@Converter(autoApply = false)
public class WorkerEncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiEncryptor encryptor;

    public WorkerEncryptedStringConverter(@Qualifier("workerPiiEncryptor") PiiEncryptor encryptor) {
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
