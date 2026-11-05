package com.cherrycore.company.application;

import java.util.Locale;

/**
 * Alta pública: empresa + cuenta del dueño.
 */
public record RegisterCompanyCommand(
        CompanyDataCommand company,
        String ownerEmail,
        String ownerPassword,
        String ownerFirstName,
        String ownerLastName,
        String ownerPhone,
        boolean termsAccepted,
        Locale locale
) {

    @Override
    public String toString() {
        return "RegisterCompanyCommand[***]";
    }
}
