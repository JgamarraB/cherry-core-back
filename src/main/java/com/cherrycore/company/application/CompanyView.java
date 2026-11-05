package com.cherrycore.company.application;

import java.time.Instant;
import java.util.UUID;

import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.company.domain.CompanyStatus;

/**
 * Empresa lista para mostrar (con la URL pública del logo).
 */
public record CompanyView(
        UUID id,
        String legalName,
        String tradeName,
        String taxId,
        String email,
        String phone,
        String countryCode,
        CompanyStatus status,
        String logoUrl,
        Instant reviewedAt,
        String rejectionReason
) {

    public static CompanyView of(Company company, String logoUrl) {
        CompanyProfileData profile = company.profile();
        return new CompanyView(company.id(), profile.legalName().value(), profile.tradeName().value(),
                profile.taxId().value(), profile.email() == null ? null : profile.email().value(),
                profile.phone() == null ? null : profile.phone().value(), profile.country().value(),
                company.status(), logoUrl, company.reviewedAt(), company.rejectionReason());
    }
}
