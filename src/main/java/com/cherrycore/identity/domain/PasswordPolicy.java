package com.cherrycore.identity.domain;

import java.util.Locale;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Política de contraseñas, según las recomendaciones actuales (NIST SP 800-63B):
 * importa la longitud, no obligar a mayúsculas/símbolos.
 */
public record PasswordPolicy(int minLength, int maxLength) {

    /*
     * Por debajo de esta longitud no tiene sentido buscar la parte local del
     * email dentro de la contraseña (p. ej. "a@x.com").
     */
    private static final int MIN_LOCAL_PART_TO_CHECK = 3;

    public void validate(String rawPassword, EmailAddress email) {
        int length = rawPassword == null ? 0 : rawPassword.codePointCount(0, rawPassword.length());
        if (length < minLength) {
            throw new DomainException(IdentityErrorCode.PASSWORD_TOO_SHORT);
        }
        if (length > maxLength) {
            throw new DomainException(IdentityErrorCode.PASSWORD_TOO_LONG);
        }
        String localPart = email.localPart();
        if (localPart.length() >= MIN_LOCAL_PART_TO_CHECK
                && rawPassword.toLowerCase(Locale.ROOT).contains(localPart)) {
            throw new DomainException(IdentityErrorCode.PASSWORD_CONTAINS_EMAIL);
        }
    }
}
