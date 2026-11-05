package com.cherrycore.identity.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Email normalizado (sin espacios y en minúsculas) y validado.
 */
public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 254;

    /*
     * Validación pragmática: algo@dominio.tld sin espacios. La comprobación
     * real es que el usuario pulse el enlace de verificación.
     */
    private static final Pattern FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()
                || !value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new DomainException(IdentityErrorCode.INVALID_EMAIL);
        }
    }

    public static EmailAddress of(String raw) {
        if (raw == null) {
            throw new DomainException(IdentityErrorCode.INVALID_EMAIL);
        }
        return new EmailAddress(raw.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Parte anterior a la arroba, para comprobar que la contraseña no la contiene.
     */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    @Override
    public String toString() {
        return value;
    }
}
