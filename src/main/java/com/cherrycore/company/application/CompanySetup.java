package com.cherrycore.company.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.company.application.port.CompanyApplicationRepository;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyApplication;
import com.cherrycore.company.domain.CompanyApplicationStatus;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.MembershipStatus;
import com.cherrycore.company.domain.SystemRole;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Crea una empresa nueva con todo lo que necesita: roles de sistema, el
 * dueño como OWNER y la app CherryCore activada (gestión de la empresa).
 */
@Component
class CompanySetup {

    private final CompanyRepository companies;
    private final CompanyRoleRepository roles;
    private final MembershipRepository memberships;
    private final CompanyApplicationRepository companyApplications;
    private final CatalogService catalog;
    private final Clock clock;

    CompanySetup(CompanyRepository companies, CompanyRoleRepository roles, MembershipRepository memberships,
            CompanyApplicationRepository companyApplications, CatalogService catalog, Clock clock) {
        this.companies = companies;
        this.roles = roles;
        this.memberships = memberships;
        this.companyApplications = companyApplications;
        this.catalog = catalog;
        this.clock = clock;
    }

    Company create(UUID ownerAccountId, CompanyProfileData profile) {
        if (companies.existsByTaxId(profile.country(), profile.taxId(), null)) {
            throw new DomainException(CompanyErrorCode.TAX_ID_ALREADY_REGISTERED);
        }
        Company company = Company.register(UuidV7.generate(clock), profile);
        companies.save(company);

        Application cherryCore = catalog.findByCode(CompanyPermission.APPLICATION_CODE)
                .orElseThrow(() -> new IllegalStateException("CherryCore application not registered"));
        Set<String> adminCodes = CompanyPermission.adminDefaults().stream()
                .map(CompanyPermission::code)
                .collect(Collectors.toSet());
        Set<UUID> adminPermissions = catalog.permissionsOf(cherryCore.id()).stream()
                .filter(permission -> adminCodes.contains(permission.code().value()))
                .map(ApplicationPermission::id)
                .collect(Collectors.toSet());

        CompanyRole owner = CompanyRole.system(UuidV7.generate(clock), company.id(), SystemRole.OWNER, Set.of());
        roles.save(owner);
        roles.save(CompanyRole.system(UuidV7.generate(clock), company.id(), SystemRole.ADMIN, adminPermissions));
        roles.save(CompanyRole.system(UuidV7.generate(clock), company.id(), SystemRole.STAFF, Set.of()));

        memberships.save(new Membership(UuidV7.generate(clock), ownerAccountId, company.id(), owner.id(),
                MembershipStatus.ACTIVE));
        companyApplications.save(new CompanyApplication(UuidV7.generate(clock), company.id(), cherryCore.id(),
                CompanyApplicationStatus.ENABLED));
        return company;
    }
}
