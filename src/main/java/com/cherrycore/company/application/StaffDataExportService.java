package com.cherrycore.company.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Parte de empresas de "descargar mis datos".
 */
@Service
public class StaffDataExportService {

    private final StaffUserRepository staffUsers;
    private final StaffTenantService tenants;

    public StaffDataExportService(StaffUserRepository staffUsers, StaffTenantService tenants) {
        this.staffUsers = staffUsers;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public StaffDataExport export(UUID accountId) {
        StaffUser profile = staffUsers.findById(accountId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.STAFF_PROFILE_NOT_FOUND));
        return new StaffDataExport(profile.firstName().value(), profile.lastName().value(),
                profile.phone() == null ? null : profile.phone().value(), tenants.myCompanies(accountId));
    }
}
