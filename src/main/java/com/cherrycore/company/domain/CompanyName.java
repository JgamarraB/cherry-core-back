package com.cherrycore.company.domain;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Nombre de empresa (razón social o comercial): sin caracteres de control.
 */
public record CompanyName(String value) {

    public static final int MAX_LEGAL_LENGTH = 200;
    public static final int MAX_TRADE_LENGTH = 100;

    public CompanyName {
        if (value == null || value.isBlank() || value.length() > MAX_LEGAL_LENGTH
                || value.chars().anyMatch(Character::isISOControl) || !value.equals(value.strip())) {
            throw new DomainException(CompanyErrorCode.INVALID_COMPANY_NAME);
        }
    }

    public static CompanyName of(String raw, int maxLength) {
        String stripped = raw == null ? null : raw.strip();
        if (stripped != null && stripped.length() > maxLength) {
            throw new DomainException(CompanyErrorCode.INVALID_COMPANY_NAME);
        }
        return new CompanyName(stripped);
    }

    @Override
    public String toString() {
        return value;
    }
}
