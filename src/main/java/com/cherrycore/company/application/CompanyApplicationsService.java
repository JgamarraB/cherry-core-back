package com.cherrycore.company.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.company.application.port.CompanyApplicationRepository;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.domain.CompanyApplication;
import com.cherrycore.company.domain.CompanyApplicationStatus;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Apps activadas por empresa. Hoy las activa el superadmin; cuando llegue
 * Stripe, lo hará la suscripción sobre esta misma información.
 */
@Service
public class CompanyApplicationsService {

    private final CompanyAccessPolicy access;
    private final CompanyApplicationRepository companyApplications;
    private final CompanyRepository companies;
    private final CatalogService catalog;
    private final Clock clock;

    public CompanyApplicationsService(CompanyAccessPolicy access, CompanyApplicationRepository companyApplications,
            CompanyRepository companies, CatalogService catalog, Clock clock) {
        this.access = access;
        this.companyApplications = companyApplications;
        this.companies = companies;
        this.catalog = catalog;
        this.clock = clock;
    }

    /**
     * Apps activadas, vistas por la propia empresa.
     */
    @Transactional(readOnly = true)
    public List<CompanyApplicationView> enabledFor(StaffActor actor) {
        UUID companyId = access.require(actor, CompanyPermission.APPLICATIONS_VIEW).company().id();
        return allFor(companyId).stream().filter(CompanyApplicationView::enabled).toList();
    }

    /**
     * Todas las apps con su estado para una empresa (vista de superadmin).
     */
    @Transactional(readOnly = true)
    public List<CompanyApplicationView> allFor(UUID companyId) {
        Map<UUID, CompanyApplication> byApp = companyApplications.findByCompany(companyId).stream()
                .collect(Collectors.toMap(CompanyApplication::applicationId, Function.identity()));
        return catalog.listApplications().stream()
                .map(app -> new CompanyApplicationView(app.id(), app.code().value(), app.name(),
                        app.isActive() && byApp.containsKey(app.id()) && byApp.get(app.id()).isEnabled()))
                .toList();
    }

    @Transactional
    public List<CompanyApplicationView> setEnabled(UUID companyId, UUID applicationId, boolean enabled) {
        companies.findById(companyId).orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_NOT_FOUND));
        Application application = catalog.get(applicationId);
        if (CompanyPermission.APPLICATION_CODE.equals(application.code().value()) && !enabled) {
            // CherryCore (gestión de la empresa) no se puede desactivar.
            throw new DomainException(CompanyErrorCode.INVALID_STATUS_TRANSITION);
        }
        CompanyApplication companyApplication = companyApplications.findByCompanyAndApplication(companyId,
                applicationId).orElseGet(() -> new CompanyApplication(UuidV7.generate(clock), companyId,
                        applicationId, CompanyApplicationStatus.DISABLED));
        if (enabled) {
            companyApplication.enable();
        } else {
            companyApplication.disable();
        }
        companyApplications.save(companyApplication);
        return allFor(companyId);
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID companyId, String applicationCode) {
        return allFor(companyId).stream()
                .anyMatch(view -> view.enabled() && view.code().equals(applicationCode));
    }
}
