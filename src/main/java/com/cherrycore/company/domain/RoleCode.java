package com.cherrycore.company.domain;

import java.util.regex.Pattern;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Código de rol dentro de una empresa: {@code OWNER}, {@code SHIFT_MANAGER}...
 */
public record RoleCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,49}$");

    public RoleCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainException(CompanyErrorCode.INVALID_ROLE_CODE);
        }
    }

    public boolean isOwner() {
        return SystemRole.OWNER.name().equals(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
