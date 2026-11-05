package com.cherrycore.admin.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.company.application.CompanyAdminService;
import com.cherrycore.company.application.CompanyApplicationView;
import com.cherrycore.company.application.CompanyApplicationsService;
import com.cherrycore.company.application.CompanyLogos;
import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.company.application.CompanyView;
import com.cherrycore.company.application.MemberView;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;
import com.cherrycore.shared.infrastructure.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping(AdminPaths.COMPANIES)
@PreAuthorize(AccountAuthorities.HAS_ROLE_SUPER_ADMIN)
public class AdminCompaniesController {

    private final CompanyAdminService companies;
    private final CompanyReviewService review;
    private final CompanyApplicationsService applications;
    private final CompanyLogos logos;

    public AdminCompaniesController(CompanyAdminService companies, CompanyReviewService review,
            CompanyApplicationsService applications, CompanyLogos logos) {
        this.companies = companies;
        this.review = review;
        this.applications = applications;
        this.logos = logos;
    }

    @GetMapping
    public ApiSuccessResponse<PageResponse<CompanyView>> search(
            @RequestParam(required = false) CompanyStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(name = AdminPaths.PAGE, defaultValue = AdminPaths.DEFAULT_PAGE) int page,
            @RequestParam(name = AdminPaths.SIZE, defaultValue = AdminPaths.DEFAULT_SIZE) int size) {
        return ApiSuccessResponse.of(PageResponse.from(companies.search(status, search, new PageQuery(page, size))));
    }

    @GetMapping("/{companyId}")
    public ApiSuccessResponse<CompanyView> get(@PathVariable UUID companyId) {
        return ApiSuccessResponse.of(companies.get(companyId));
    }

    @GetMapping("/{companyId}/members")
    public ApiSuccessResponse<List<MemberView>> members(@PathVariable UUID companyId) {
        return ApiSuccessResponse.of(companies.members(companyId));
    }

    @GetMapping("/{companyId}/applications")
    public ApiSuccessResponse<List<CompanyApplicationView>> applications(@PathVariable UUID companyId) {
        return ApiSuccessResponse.of(applications.allFor(companyId));
    }

    /**
     * Activa o desactiva una app para la empresa (mientras no hay suscripciones).
     */
    @PutMapping("/{companyId}/applications/{applicationId}")
    public ApiSuccessResponse<List<CompanyApplicationView>> setApplication(@PathVariable UUID companyId,
            @PathVariable UUID applicationId, @Valid @RequestBody EnabledRequest body, HttpServletRequest request) {
        return ApiSuccessResponse.of(applications.setEnabled(AdminSupport.currentAdmin(), companyId, applicationId,
                body.enabled(), RequestContextResolver.from(request)));
    }

    @PostMapping("/{companyId}/approve")
    public ApiSuccessResponse<CompanyView> approve(@PathVariable UUID companyId, HttpServletRequest request) {
        return view(review.approve(AdminSupport.currentAdmin(), companyId, RequestContextResolver.from(request)));
    }

    @PostMapping("/{companyId}/reject")
    public ApiSuccessResponse<CompanyView> reject(@PathVariable UUID companyId,
            @Valid @RequestBody RejectRequest body, HttpServletRequest request) {
        return view(review.reject(AdminSupport.currentAdmin(), companyId, body.reason(),
                RequestContextResolver.from(request)));
    }

    @PostMapping("/{companyId}/suspend")
    public ApiSuccessResponse<CompanyView> suspend(@PathVariable UUID companyId, HttpServletRequest request) {
        return view(review.suspend(AdminSupport.currentAdmin(), companyId, RequestContextResolver.from(request)));
    }

    @PostMapping("/{companyId}/reactivate")
    public ApiSuccessResponse<CompanyView> reactivate(@PathVariable UUID companyId, HttpServletRequest request) {
        return view(review.reactivate(AdminSupport.currentAdmin(), companyId, RequestContextResolver.from(request)));
    }

    private ApiSuccessResponse<CompanyView> view(Company company) {
        return ApiSuccessResponse.of(logos.view(company));
    }

    public record RejectRequest(@NotBlank @Size(max = Company.MAX_REJECTION_REASON_LENGTH) String reason) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }
}
