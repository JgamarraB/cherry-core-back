package com.cherrycore.company.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.Membership;

/**
 * Empresas en las que puede entrar una persona de backoffice y lo que puede
 * hacer en cada app (para el login y los tokens).
 */
@Service
public class StaffTenantService {

    private final MembershipRepository memberships;
    private final CompanyRepository companies;
    private final CompanyRoleRepository roles;
    private final CompanyApplicationsService companyApplications;
    private final CatalogService catalog;
    private final CompanyLogos logos;

    public StaffTenantService(MembershipRepository memberships, CompanyRepository companies,
            CompanyRoleRepository roles, CompanyApplicationsService companyApplications, CatalogService catalog,
            CompanyLogos logos) {
        this.memberships = memberships;
        this.companies = companies;
        this.roles = roles;
        this.companyApplications = companyApplications;
        this.catalog = catalog;
        this.logos = logos;
    }

    /**
     * Empresas activas donde la persona es miembro activo.
     */
    @Transactional(readOnly = true)
    public List<TenantOption> tenantsOf(UUID accountId) {
        List<Membership> active = memberships.findByStaffUser(accountId).stream().filter(Membership::isActive)
                .toList();
        Map<UUID, Company> companiesById = companies.findByIds(active.stream().map(Membership::companyId).toList())
                .stream().filter(Company::isActive).collect(Collectors.toMap(Company::id, Function.identity()));
        return active.stream()
                .filter(membership -> companiesById.containsKey(membership.companyId()))
                .map(membership -> {
                    Company company = companiesById.get(membership.companyId());
                    CompanyRole role = roles.findById(membership.roleId()).orElseThrow();
                    return new TenantOption(company.id(), company.profile().tradeName().value(),
                            logos.urlOf(company), role.code().value(), role.name());
                })
                .sorted(Comparator.comparing(TenantOption::name))
                .toList();
    }

    /**
     * Todas mis empresas, en cualquier estado (pendientes, rechazadas...), con mi rol.
     */
    @Transactional(readOnly = true)
    public List<MyCompanyView> myCompanies(UUID accountId) {
        List<Membership> mine = memberships.findByStaffUser(accountId);
        Map<UUID, Company> companiesById = companies.findByIds(mine.stream().map(Membership::companyId).toList())
                .stream().collect(Collectors.toMap(Company::id, Function.identity()));
        return mine.stream()
                .filter(membership -> companiesById.containsKey(membership.companyId()))
                .map(membership -> {
                    CompanyRole role = roles.findById(membership.roleId()).orElseThrow();
                    return new MyCompanyView(logos.view(companiesById.get(membership.companyId())),
                            role.code().value(), role.name(), membership.status());
                })
                .toList();
    }

    public boolean canEnter(UUID accountId, UUID companyId) {
        return tenantsOf(accountId).stream().anyMatch(option -> option.companyId().equals(companyId));
    }

    /**
     * Acceso de la persona a una app dentro de una empresa. Vacío si ya no es
     * miembro activo, la empresa no está activa o la app no está activada.
     */
    @Transactional(readOnly = true)
    public Optional<TenantAccess> accessFor(UUID accountId, UUID companyId, String applicationCode) {
        Optional<Company> company = companies.findById(companyId).filter(Company::isActive);
        Optional<Membership> membership = memberships.findByCompanyAndStaffUser(companyId, accountId)
                .filter(Membership::isActive);
        Optional<Application> application = catalog.findByCode(applicationCode).filter(Application::isActive);
        if (company.isEmpty() || membership.isEmpty() || application.isEmpty()
                || !companyApplications.isEnabled(companyId, applicationCode)) {
            return Optional.empty();
        }
        CompanyRole role = roles.findById(membership.get().roleId()).orElseThrow();
        List<ApplicationPermission> appPermissions = catalog.permissionsOf(application.get().id());
        Set<UUID> granted = role.permissionIds();
        List<String> codes = appPermissions.stream()
                .filter(permission -> role.isOwner() || granted.contains(permission.id()))
                .map(permission -> permission.code().value())
                .sorted()
                .toList();
        return Optional.of(new TenantAccess(companyId, company.get().profile().tradeName().value(),
                role.code().value(), codes));
    }
}
