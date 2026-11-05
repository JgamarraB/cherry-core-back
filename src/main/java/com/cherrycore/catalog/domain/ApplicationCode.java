package com.cherrycore.catalog.domain;

import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Identificador estable de una app: {@code cherrycore}, {@code lirgar}...
 */
public record ApplicationCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9-]{1,49}$");

    public ApplicationCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException(CatalogErrorCode.INVALID_APPLICATION_CODE);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
