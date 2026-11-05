package com.cherrycore.company.domain;

import java.util.Locale;
import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Identificador fiscal (CIF/NIF/VAT...) normalizado: mayúsculas, sin espacios
 * ni guiones. El formato exacto depende del país; aquí solo se exige algo
 * razonable y la unicidad se comprueba por país.
 */
public record TaxId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9]{5,30}$");
    private static final String SEPARATORS = "[\\s\\-./]";
    private static final String NOTHING = "";

    public TaxId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException(CompanyErrorCode.INVALID_TAX_ID);
        }
    }

    public static TaxId of(String raw) {
        return new TaxId(raw == null ? null : raw.replaceAll(SEPARATORS, NOTHING).toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
