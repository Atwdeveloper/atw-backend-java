package in.anytimework.atwbackend.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Transparent AES-256-GCM encryption for a String column — applied per-field via
 * {@code @Convert(converter = EncryptedStringConverter.class)}, not {@code autoApply}, so only
 * fields that actually need it (KYC numbers, bank account numbers) opt in.
 *
 * <p>Spring-managed (constructor-injected {@link PiiEncryptor}): Spring Data JPA registers a bean
 * container with Hibernate automatically, so a {@code @Component}-annotated converter referenced by
 * {@code @Convert} is resolved through Spring, not instantiated via a no-arg constructor.</p>
 */
@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiEncryptor encryptor;

    public EncryptedStringConverter(PiiEncryptor encryptor) {
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
