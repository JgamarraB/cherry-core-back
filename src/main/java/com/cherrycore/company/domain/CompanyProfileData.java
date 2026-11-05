package com.cherrycore.company.domain;

import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.domain.value.CountryCode;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Datos editables de la empresa, ya validados.
 */
public record CompanyProfileData(
        CompanyName legalName,
        CompanyName tradeName,
        TaxId taxId,
        EmailAddress email,
        PhoneNumber phone,
        CountryCode country
) {
}
