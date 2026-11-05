package com.cherrycore.customer.domain;

import java.util.Locale;
import java.util.Set;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Código de país ISO 3166-1 alfa-2 (ES, FR, PT...).
 */
public record CountryCode(String value) {

    private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    public CountryCode {
        if (value == null || !ISO_COUNTRIES.contains(value)) {
            throw new DomainException(CustomerErrorCode.INVALID_COUNTRY_CODE);
        }
    }

    public static CountryCode of(String raw) {
        return new CountryCode(raw == null ? null : raw.strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
