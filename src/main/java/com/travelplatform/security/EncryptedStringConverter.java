package com.travelplatform.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code @Convert(converter = EncryptedStringConverter.class)}: the column holds only ciphertext. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(EncryptedStringConverter.class);

    @Override
    public String convertToDatabaseColumn(String plain) {
        return plain == null ? null : FieldEncryption.encrypt(plain);
    }

    /**
     * Unreadable (key missing or changed) reads as absent rather than failing the whole entity: a
     * driver must still be able to sign in and drive when payouts are misconfigured. Hibernate
     * only writes a column back when its value changes, so the stored ciphertext survives.
     */
    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return FieldEncryption.decrypt(stored);
        } catch (IllegalStateException e) {
            log.error("Encrypted field unreadable: {}", e.getMessage());
            return null;
        }
    }
}
