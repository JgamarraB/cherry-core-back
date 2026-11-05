package com.cherrycore.customer.domain;

import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Teléfono en formato internacional E.164: + prefijo de país y número, sin espacios.
 */
public record PhoneNumber(String value) {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final String SEPARATORS = "[\\s\\-().]";
    private static final String NOTHING = "";

    public PhoneNumber {
        if (value == null || !E164.matcher(value).matches()) {
            throw new DomainException(CustomerErrorCode.INVALID_PHONE);
        }
    }

    /**
     * Acepta "+34 600 11 12 22" y lo guarda como "+34600111222".
     */
    public static PhoneNumber of(String raw) {
        return new PhoneNumber(raw == null ? null : raw.replaceAll(SEPARATORS, NOTHING));
    }

    @Override
    public String toString() {
        return value;
    }
}
