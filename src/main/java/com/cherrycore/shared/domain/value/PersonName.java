package com.cherrycore.shared.domain.value;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Nombre o apellido: sin espacios sobrantes, no vacío, longitud limitada y
 * sin caracteres de control (evita inyecciones en emails, logs, CSV...).
 */
public record PersonName(String value) {

    public static final int MAX_LENGTH = 100;

    public PersonName {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH
                || value.chars().anyMatch(Character::isISOControl) || !value.equals(value.strip())) {
            throw new DomainException(CommonErrorCode.INVALID_NAME);
        }
    }

    public static PersonName of(String raw) {
        return new PersonName(raw == null ? null : raw.strip());
    }

    @Override
    public String toString() {
        return value;
    }
}
