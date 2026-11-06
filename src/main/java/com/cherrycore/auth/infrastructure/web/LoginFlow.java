package com.cherrycore.auth.infrastructure.web;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginResponse;
import com.cherrycore.auth.infrastructure.web.LoginDtos.MfaSetupConfirmed;
import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.company.application.TenantOption;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.identity.application.AccountLockoutService;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.identity.application.KnownDeviceService;
import com.cherrycore.identity.application.MfaEnrollment;
import com.cherrycore.identity.application.MfaService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

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
public class LoginFlow {

    static final int MAX_MFA_ATTEMPTS = 5;

    private final MfaService mfa;
    private final AccountQueryService accounts;
    private final StaffTenantService tenants;
    private final SessionLoginHandler session;
    private final AuthEventLogger eventLogger;
    private final AccountLockoutService lockout;
    private final KnownDeviceService knownDevices;
    private final DeviceCookie deviceCookie;
    private final Duration pendingLoginTtl;
    private final Clock clock;

    LoginFlow(MfaService mfa, AccountQueryService accounts, StaffTenantService tenants, SessionLoginHandler session,
            AuthEventLogger eventLogger, AccountLockoutService lockout, KnownDeviceService knownDevices,
            DeviceCookie deviceCookie, CherryCoreProperties properties, Clock clock) {
        this.knownDevices = knownDevices;
        this.deviceCookie = deviceCookie;
        this.pendingLoginTtl = properties.security().loginProtection().pendingLoginTtl();
        this.clock = clock;
        this.lockout = lockout;
        this.mfa = mfa;
        this.accounts = accounts;
        this.tenants = tenants;
        this.session = session;
        this.eventLogger = eventLogger;
    }

    public LoginResponse afterPassword(AuthenticatedAccount authenticated, HttpServletRequest request,
            HttpServletResponse response) {
        return afterPrimaryFactor(authenticated, PrimaryFactor.PASSWORD, request, response);
    }

    public LoginResponse afterPrimaryFactor(AuthenticatedAccount authenticated, PrimaryFactor factor,
            HttpServletRequest request, HttpServletResponse response) {
        Account account = accounts.getActive(authenticated.id());
        session.rotateSessionId(request, response);
        boolean enabled = mfa.isEnabled(account.id());
        if (!enabled && !MfaService.isMandatoryFor(account.type())) {
            return finish(account.id(), account.type(), account.isSuperAdmin(), factor, false, request, response);
        }
        request.getSession().setAttribute(PendingLogin.SESSION_ATTRIBUTE,
                new PendingLogin(account.id(), account.type(), account.isSuperAdmin(), factor, 0, clock.instant()));
        return new LoginResponse(enabled ? LoginStatus.MFA_REQUIRED : LoginStatus.MFA_SETUP_REQUIRED, null);
    }

    LoginResponse verifyMfa(String code, HttpServletRequest request, HttpServletResponse response) {
        PendingLogin pending = pending(request);
        // Bloqueada (también por fallos de MFA de logins anteriores): ningún código vale.
        if (lockout.isLocked(pending.accountId()) || !mfa.verify(pending.accountId(), code)) {
            PendingLogin failed = pending.withFailedAttempt();
            lockout.recordFailure(pending.accountId(), AuthEventType.MFA_FAILED,
                    RequestContextResolver.from(request));
            if (failed.failedMfaAttempts() >= MAX_MFA_ATTEMPTS || lockout.isLocked(pending.accountId())) {
                request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
            } else {
                request.getSession().setAttribute(PendingLogin.SESSION_ATTRIBUTE, failed);
            }
            throw new DomainException(IdentityErrorCode.INVALID_MFA_CODE);
        }
        Account account = stillActive(pending, request);
        request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
        lockout.recordSuccess(account.id());
        return finish(account.id(), account.type(), account.isSuperAdmin(), pending.primaryFactor(), true,
                request, response);
    }

    MfaEnrollment startMfaSetup(HttpServletRequest request) {
        return mfa.startEnrollment(pending(request).accountId());
    }

    MfaSetupConfirmed confirmMfaSetup(String code, HttpServletRequest request, HttpServletResponse response) {
        PendingLogin pending = pending(request);
        Account account = stillActive(pending, request);
        List<String> recoveryCodes = mfa.confirmEnrollment(account.id(), code);
        request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
        lockout.recordSuccess(account.id());
        LoginResponse login = finish(account.id(), account.type(), account.isSuperAdmin(),
                pending.primaryFactor(), true, request, response);
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
                instanceof PendingLogin pending) || pending.isExpiredAt(clock.instant(), pendingLoginTtl)) {
            return null;
        }
        return mfa.isEnabled(pending.accountId()) ? LoginStatus.MFA_REQUIRED : LoginStatus.MFA_SETUP_REQUIRED;
    }

    private LoginResponse finish(UUID accountId, AccountType type, boolean superAdmin, PrimaryFactor factor,
            boolean usedTotp, HttpServletRequest request, HttpServletResponse response) {
        session.establish(accountId, type, superAdmin, factor, usedTotp, request, response);
        eventLogger.log(AuthEventType.LOGIN_COMPLETED, accountId, RequestContextResolver.from(request));
        // Con contraseña (y MFA) o social: todos los logins pasan por aquí.
        if (knownDevices.enabled()) {
            knownDevices.loginCompleted(accountId, deviceCookie.resolve(request, response),
                    RequestContextResolver.from(request));
        }
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

    /*
     * Entre la contraseña y el segundo factor la cuenta puede haberse
     * desactivado o haber perdido el rol de superadmin: se vuelve a leer de la
     * base de datos (cerrar sus sesiones no borra un login a medias, que aún no
     * tiene usuario). Si ya no está activa, el login a medias se descarta.
     */
    private Account stillActive(PendingLogin pending, HttpServletRequest request) {
        return accounts.findActive(pending.accountId()).orElseThrow(() -> {
            request.getSession().removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
            return new DomainException(IdentityErrorCode.INVALID_CREDENTIALS);
        });
    }

    /*
     * El login a medias tiene su propia caducidad (cherrycore.security.login-protection.pending-login-ttl),
     * más corta que la sesión: pasado ese tiempo hay que volver a poner la contraseña.
     */
    private PendingLogin pending(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null && httpSession.getAttribute(PendingLogin.SESSION_ATTRIBUTE)
                instanceof PendingLogin pending) {
            if (!pending.isExpiredAt(clock.instant(), pendingLoginTtl)) {
                return pending;
            }
            httpSession.removeAttribute(PendingLogin.SESSION_ATTRIBUTE);
        }
        throw new DomainException(IdentityErrorCode.NO_PENDING_LOGIN);
    }
}
