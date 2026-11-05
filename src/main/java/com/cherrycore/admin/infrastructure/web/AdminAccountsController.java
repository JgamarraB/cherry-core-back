package com.cherrycore.admin.infrastructure.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.admin.application.AccountSummary;
import com.cherrycore.admin.application.AdminService;
import com.cherrycore.admin.application.AdminService.AccountDetail;
import com.cherrycore.identity.application.AccountAdministrationService;
import com.cherrycore.identity.domain.AccountStatus;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;
import com.cherrycore.shared.infrastructure.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping(AdminPaths.ACCOUNTS)
@PreAuthorize(AccountAuthorities.HAS_ROLE_SUPER_ADMIN)
public class AdminAccountsController {

    private final AdminService admin;
    private final AccountAdministrationService administration;

    public AdminAccountsController(AdminService admin, AccountAdministrationService administration) {
        this.admin = admin;
        this.administration = administration;
    }

    @GetMapping
    public ApiSuccessResponse<PageResponse<AccountSummary>> search(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(name = AdminPaths.PAGE, defaultValue = AdminPaths.DEFAULT_PAGE) int page,
            @RequestParam(name = AdminPaths.SIZE, defaultValue = AdminPaths.DEFAULT_SIZE) int size) {
        return ApiSuccessResponse.of(PageResponse.from(admin.searchAccounts(type == null ? null : type.name(),
                status == null ? null : status.name(), search, new PageQuery(page, size))));
    }

    @GetMapping("/{accountId}")
    public ApiSuccessResponse<AccountDetail> get(@PathVariable UUID accountId) {
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    @PostMapping("/{accountId}/disable")
    public ApiSuccessResponse<AccountDetail> disable(@PathVariable UUID accountId, HttpServletRequest request) {
        administration.disable(AdminSupport.currentAdmin(), accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    @PostMapping("/{accountId}/enable")
    public ApiSuccessResponse<AccountDetail> enable(@PathVariable UUID accountId, HttpServletRequest request) {
        administration.enable(AdminSupport.currentAdmin(), accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    @PostMapping("/{accountId}/unlock")
    public ApiSuccessResponse<AccountDetail> unlock(@PathVariable UUID accountId, HttpServletRequest request) {
        administration.unlock(accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    @PostMapping("/{accountId}/mfa/reset")
    public ApiSuccessResponse<AccountDetail> resetMfa(@PathVariable UUID accountId, HttpServletRequest request) {
        administration.resetMfa(AdminSupport.currentAdmin(), accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    @PostMapping("/{accountId}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> sendPasswordReset(@PathVariable UUID accountId, HttpServletRequest request) {
        administration.sendPasswordReset(accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @PutMapping("/{accountId}/super-admin")
    public ApiSuccessResponse<AccountDetail> setSuperAdmin(@PathVariable UUID accountId,
            @Valid @RequestBody SuperAdminRequest body, HttpServletRequest request) {
        administration.setSuperAdmin(AdminSupport.currentAdmin(), accountId, body.enabled(),
                RequestContextResolver.from(request));
        return ApiSuccessResponse.of(admin.accountDetail(accountId));
    }

    public record SuperAdminRequest(@NotNull Boolean enabled) {
    }
}
