package com.cherrycore.auth.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginResponse;
import com.cherrycore.auth.infrastructure.web.LoginDtos.MfaSetupConfirmed;
import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.company.application.TenantOption;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.identity.application.MfaEnrollment;
import com.cherrycore.identity.application.MfaService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.DomainException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Login por pasos: contraseña → segundo factor (o configurarlo) → empresa.
 *
 * <p>Hasta completar el MFA no hay sesión autenticada: solo un
 * {@link PendingLogin}. Tras {@value #MAX_MFA_ATTEMPTS} códigos erróneos se
 * descarta y hay que volver a poner la contraseña.</p>
 */
@Component
class LoginFlow {

    static final int MAX_MFA_ATTEMPTS = 5;

    private final MfaService mfa;
    private final AccountQueryService accounts;
    private final StaffTenantService tenants;
    private final SessionLoginHandler session;
    private final AuthEventLogger eventLogger;

    LoginFlow(MfaService mfa, AccountQueryService accounts, StaffTenantService tenants, SessionLoginHandler session,
            AuthEventLogger eventLogger) {
        this.mfa = mfa;
        this.accounts = accounts;
        this.tenants = tenants;
        this.session = session;
        this.eventLogger = eventLogger;
    }

    LoginResponse afterPassword(AuthenticatedAccount authenticated, HttpServletRequest request,
            HttpServletResponse response) {
        Account account = accounts.getActive(authenticated.id());
        session.rotateSessionId(request, response);
        boolean enabled = mfa.isEnabled(account.id());
        if (!enabled && !MfaService.isMandatoryFor(account.type())) {
            return finish(account.id(), account.type(), account.isSuperAdmin(), false, request, response);
        }
        request.getSession().setAttribute(PendingLogin.SESSION_ATTRIBUTE,
                new PendingLogin(account.id(), account.type(), account.isSuperAdmin(), 0));
        return new LoginResponse(enabled ? LoginStatus.MFA_REQUIRED : LoginStatus.MFA_SETUP_REQUIRED, null);
    }

    LoginResponse verifyMfa(String code, HttpServletRequest request, HttpServletResponse response) {
        PendingLogin pending = pending(request);
        if (!mfa.verify(pending.accountId(), code)) {
            PendingLogin failed = pending.withFailedAttempt();
            eventLogger.log(AuthEventType.MFA_FAILED, pending.accountId(), RequestContextResolver.from(request));
            if (failed.failedMfaAttempts() >= MAX_MFA_ATTEMPTS) {
                request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
            } else {
                request.getSession().setAttribute(PendingLogin.SESSION_ATTRIBUTE, failed);
            }
            throw new DomainException(IdentityErrorCode.INVALID_MFA_CODE);
        }
        request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
        return finish(pending.accountId(), pending.type(), pending.superAdmin(), true, request, response);
    }

    MfaEnrollment startMfaSetup(HttpServletRequest request) {
        return mfa.startEnrollment(pending(request).accountId());
    }

    MfaSetupConfirmed confirmMfaSetup(String code, HttpServletRequest request, HttpServletResponse response) {
        PendingLogin pending = pending(request);
        List<String> recoveryCodes = mfa.confirmEnrollment(pending.accountId(), code);
        request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
        LoginResponse login = finish(pending.accountId(), pending.type(), pending.superAdmin(), true, request,
                response);
        return new MfaSetupConfirmed(recoveryCodes, login);
    }

    LoginResponse selectTenant(UUID accountId, UUID companyId, HttpServletRequest request,
            HttpServletResponse response) {
        if (!tenants.canEnter(accountId, companyId)) {
            throw new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED);
        }
        session.selectTenant(companyId, request, response);
        return new LoginResponse(LoginStatus.AUTHENTICATED, session.consumeRedirectUrl(request, response));
    }

    LoginStatus pendingStep(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession == null || !(httpSession.getAttribute(PendingLogin.SESSION_ATTRIBUTE)
                instanceof PendingLogin pending)) {
            return null;
        }
        return mfa.isEnabled(pending.accountId()) ? LoginStatus.MFA_REQUIRED : LoginStatus.MFA_SETUP_REQUIRED;
    }

    private LoginResponse finish(UUID accountId, AccountType type, boolean superAdmin, boolean usedTotp,
            HttpServletRequest request, HttpServletResponse response) {
        session.establish(accountId, type, superAdmin, usedTotp, request, response);
        eventLogger.log(AuthEventType.LOGIN_COMPLETED, accountId, RequestContextResolver.from(request));
        if (type == AccountType.STAFF) {
            List<TenantOption> options = tenants.tenantsOf(accountId);
            if (options.size() == 1) {
                session.selectTenant(options.getFirst().companyId(), request, response);
            } else if (options.size() > 1) {
                return new LoginResponse(LoginStatus.TENANT_SELECTION_REQUIRED, null);
            }
        }
        return new LoginResponse(LoginStatus.AUTHENTICATED, session.consumeRedirectUrl(request, response));
    }

    private static PendingLogin pending(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null && httpSession.getAttribute(PendingLogin.SESSION_ATTRIBUTE)
                instanceof PendingLogin pending) {
            return pending;
        }
        throw new DomainException(IdentityErrorCode.NO_PENDING_LOGIN);
    }
}
