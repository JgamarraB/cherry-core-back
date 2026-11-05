package com.cherrycore.company.application;

import com.cherrycore.company.domain.CompanyName;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.company.domain.TaxId;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.domain.value.CountryCode;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Datos de empresa tal y como llegan de la petición.
 */
public record CompanyDataCommand(
        String legalName,
        String tradeName,
        String taxId,
        String email,
        String phone,
        String countryCode
) {

    public CompanyProfileData toProfile() {
        return new CompanyProfileData(
                CompanyName.of(legalName, CompanyName.MAX_LEGAL_LENGTH),
                CompanyName.of(tradeName, CompanyName.MAX_TRADE_LENGTH),
                TaxId.of(taxId),
                email == null || email.isBlank() ? null : EmailAddress.of(email),
                phone == null || phone.isBlank() ? null : PhoneNumber.of(phone),
                CountryCode.of(countryCode));
    }
}
