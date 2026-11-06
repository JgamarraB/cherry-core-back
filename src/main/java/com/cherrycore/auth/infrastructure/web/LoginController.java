package com.cherrycore.auth.infrastructure.web;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.auth.infrastructure.web.LoginDtos.CodeRequest;
import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginRequest;
import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginResponse;
import com.cherrycore.auth.infrastructure.web.LoginDtos.MfaSetupConfirmed;
import com.cherrycore.auth.infrastructure.web.LoginDtos.SessionResponse;
import com.cherrycore.auth.infrastructure.web.LoginDtos.TenantRequest;
import com.cherrycore.auth.infrastructure.web.LoginDtos.TenantsResponse;
import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.AuthenticateAccountService;
import com.cherrycore.identity.application.MfaEnrollment;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Login por pasos para el frontend de CherryCore (cookie de sesión + CSRF).
 * Cada respuesta dice el siguiente paso ({@link LoginStatus}).
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class LoginController {

    private final AuthenticateAccountService authenticateService;
    private final LoginFlow loginFlow;
    private final StaffTenantService tenants;
    private final SessionLoginHandler sessionHandler;
    private final AuthEventLogger eventLogger;

    LoginController(AuthenticateAccountService authenticateService, LoginFlow loginFlow, StaffTenantService tenants,
            SessionLoginHandler sessionHandler, AuthEventLogger eventLogger) {
        this.authenticateService = authenticateService;
        this.loginFlow = loginFlow;
        this.tenants = tenants;
        this.sessionHandler = sessionHandler;
        this.eventLogger = eventLogger;
    }

    @PostMapping(AuthPaths.CUSTOMER_LOGIN)
    public ApiSuccessResponse<LoginResponse> customerLogin(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return login(AccountType.CUSTOMER, body, request, response);
    }

    @PostMapping(AuthPaths.STAFF_LOGIN)
    public ApiSuccessResponse<LoginResponse> staffLogin(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return login(AccountType.STAFF, body, request, response);
    }

    @PostMapping(AuthPaths.MFA_VERIFY)
    public ApiSuccessResponse<LoginResponse> verifyMfa(@Valid @RequestBody CodeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiSuccessResponse.of(loginFlow.verifyMfa(body.code(), request, response));
    }

    @PostMapping(AuthPaths.MFA_SETUP)
    public ApiSuccessResponse<MfaEnrollment> startMfaSetup(HttpServletRequest request) {
        return ApiSuccessResponse.of(loginFlow.startMfaSetup(request));
    }

    @PostMapping(AuthPaths.MFA_SETUP_CONFIRM)
    public ApiSuccessResponse<MfaSetupConfirmed> confirmMfaSetup(@Valid @RequestBody CodeRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiSuccessResponse.of(loginFlow.confirmMfaSetup(body.code(), request, response));
    }

    @GetMapping(AuthPaths.STAFF_TENANTS)
    public ApiSuccessResponse<TenantsResponse> staffTenants() {
        UUID accountId = requireAuthenticated();
        return ApiSuccessResponse.of(new TenantsResponse(CurrentAccount.activeCompanyId().orElse(null),
                tenants.tenantsOf(accountId)));
    }

    @PostMapping(AuthPaths.STAFF_TENANT)
    public ApiSuccessResponse<LoginResponse> selectTenant(@Valid @RequestBody TenantRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return ApiSuccessResponse.of(loginFlow.selectTenant(requireAuthenticated(), body.companyId(), request,
                response));
    }

    @GetMapping(AuthPaths.SESSION)
    public ApiSuccessResponse<SessionResponse> session(Authentication authentication, HttpServletRequest request) {
        if (CurrentAccount.id().isEmpty() || authentication == null) {
            return ApiSuccessResponse.of(SessionResponse.anonymous(loginFlow.pendingStep(request)));
        }
        String accountType = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name != null && name.startsWith(AccountAuthorities.ROLE_PREFIX)
                        && !name.equals(AccountAuthorities.ROLE_PREFIX + AccountAuthorities.SUPER_ADMIN))
                .map(name -> name.substring(AccountAuthorities.ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);
        boolean superAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> (AccountAuthorities.ROLE_PREFIX + AccountAuthorities.SUPER_ADMIN)
                        .equals(authority.getAuthority()));
        return ApiSuccessResponse.of(new SessionResponse(true, CurrentAccount.id().orElseThrow(), accountType,
                superAdmin,
                CurrentAccount.activeCompanyId().orElse(null), null));
    }

    @PostMapping(AuthPaths.LOGOUT)
    public ApiSuccessResponse<Void> logout(HttpServletRequest request) {
        Optional<UUID> accountId = CurrentAccount.id();
        sessionHandler.logout(request);
        accountId.ifPresent(id -> eventLogger.log(AuthEventType.LOGOUT, id, RequestContextResolver.from(request)));
        return ApiSuccessResponse.of(null);
    }

    private ApiSuccessResponse<LoginResponse> login(AccountType type, LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        var account = authenticateService.authenticate(type, body.email(), body.password(),
                RequestContextResolver.from(request));
        return ApiSuccessResponse.of(loginFlow.afterPassword(account, request, response));
    }

    private static UUID requireAuthenticated() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }
}
