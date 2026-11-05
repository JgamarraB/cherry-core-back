package com.cherrycore.customer.domain;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Datos de una dirección postal, validados.
 */
public record PostalAddress(
        String line1,
        String line2,
        String city,
        String postalCode,
        String region,
        CountryCode country
) {

    public static final int MAX_LINE_LENGTH = 200;
    public static final int MAX_CITY_LENGTH = 100;
    public static final int MAX_POSTAL_CODE_LENGTH = 20;
    public static final int MAX_REGION_LENGTH = 100;

    public PostalAddress {
        line1 = required(line1, MAX_LINE_LENGTH);
        line2 = optional(line2, MAX_LINE_LENGTH);
        city = required(city, MAX_CITY_LENGTH);
        postalCode = required(postalCode, MAX_POSTAL_CODE_LENGTH);
        region = optional(region, MAX_REGION_LENGTH);
        if (country == null) {
            throw new DomainException(CustomerErrorCode.INVALID_COUNTRY_CODE);
        }
    }

    private static String required(String value, int maxLength) {
        String cleaned = optional(value, maxLength);
        if (cleaned == null) {
            throw new DomainException(CustomerErrorCode.INVALID_ADDRESS);
        }
        return cleaned;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength || stripped.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(CustomerErrorCode.INVALID_ADDRESS);
        }
        return stripped;
    }
}
