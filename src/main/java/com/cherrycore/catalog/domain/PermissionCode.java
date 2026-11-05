package com.cherrycore.catalog.domain;

import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Código de permiso dentro de una app: {@code vehicles.manage}, {@code reservations.view}...
 */
public record PermissionCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.-]{1,99}$");

    public PermissionCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException(CatalogErrorCode.INVALID_PERMISSION_CODE);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
