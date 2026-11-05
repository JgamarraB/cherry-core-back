package com.cherrycore.company.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.company.application.CompanyLogos;
import com.cherrycore.company.application.CompanyRegistrationService;
import com.cherrycore.company.application.CompanyView;
import com.cherrycore.company.application.MyCompanyView;
import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.company.infrastructure.web.CompanyDtos.CompanyData;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.validation.Valid;

/**
 * Una persona de backoffice con sesión registra otra empresa (queda pendiente
 * de aprobación y ella como OWNER).
 */
@RestController
@PreAuthorize(AccountAuthorities.HAS_ROLE_STAFF)
public class AccountCompaniesController {

    private final CompanyRegistrationService registration;
    private final StaffTenantService tenants;
    private final CompanyLogos logos;

    public AccountCompaniesController(CompanyRegistrationService registration, StaffTenantService tenants,
            CompanyLogos logos) {
        this.registration = registration;
        this.tenants = tenants;
        this.logos = logos;
    }

    /**
     * Mis empresas en cualquier estado (para mostrar "pendiente de aprobación", etc.).
     */
    @GetMapping(CompanyPaths.ACCOUNT_COMPANIES)
    public ApiSuccessResponse<List<MyCompanyView>> myCompanies() {
        return ApiSuccessResponse.of(tenants.myCompanies(currentAccount()));
    }

    @PostMapping(CompanyPaths.ACCOUNT_COMPANIES)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<CompanyView> register(@Valid @RequestBody CompanyData body) {
        return ApiSuccessResponse.of(logos.view(registration.registerForExistingStaff(currentAccount(),
                body.toCommand())));
    }

    private static UUID currentAccount() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }
}
