package com.cherrycore.auth.infrastructure.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.auth.infrastructure.web.AccountDtos.AccountOverview;
import com.cherrycore.auth.infrastructure.web.AccountDtos.ChangeEmailRequest;
import com.cherrycore.auth.infrastructure.web.AccountDtos.ChangePasswordRequest;
import com.cherrycore.auth.infrastructure.web.AccountDtos.DeleteAccountRequest;
import com.cherrycore.auth.infrastructure.web.AccountDtos.MfaSetupRequest;
import com.cherrycore.auth.infrastructure.web.AccountDtos.RecoveryCodesResponse;
import com.cherrycore.auth.infrastructure.web.AccountDtos.UpdateProfileRequest;
import com.cherrycore.auth.infrastructure.web.LoginDtos.CodeRequest;
import com.cherrycore.company.application.StaffProfileService;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.customer.application.CustomerProfile;
import com.cherrycore.customer.application.CustomerProfileService;
import com.cherrycore.customer.application.UpdateCustomerProfileCommand;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.AccountSecurityService;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.MfaEnrollment;
import com.cherrycore.identity.application.MfaService;
import com.cherrycore.identity.application.MfaStatus;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.infrastructure.security.AccountSessions;
import com.cherrycore.identity.infrastructure.security.AccountSessions.SessionView;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * "Mi cuenta" en el frontend de CherryCore (clientes y backoffice): perfil,
 * contraseña, email, sesiones abiertas, MFA y borrado de la cuenta.
 */
@RestController
@RequestMapping(AccountController.BASE)
public class AccountController {

    public static final String BASE = "/api/account";
    public static final String PROFILE = "/profile";
    public static final String PASSWORD = "/password";
    public static final String EMAIL = "/email";
    public static final String SESSIONS = "/sessions";
    public static final String SESSION = "/sessions/{sessionId}";
    public static final String MFA = "/mfa";
    public static final String MFA_SETUP = "/mfa/setup";
    public static final String MFA_SETUP_CONFIRM = "/mfa/setup/confirm";
    public static final String MFA_DISABLE = "/mfa/disable";
    public static final String MFA_RECOVERY_CODES = "/mfa/recovery-codes";
    public static final String DELETE = "/delete";

    private static final Duration REAUTHENTICATION_WINDOW = Duration.ofMinutes(10);

    private final AccountQueryService accounts;
    private final AccountSecurityService security;
    private final MfaService mfa;
    private final CustomerProfileService customerProfiles;
    private final StaffProfileService staffProfiles;
    private final AccountSessions sessions;
    private final SessionLoginHandler sessionHandler;
    private final AuthEventLogger eventLogger;
    private final Clock clock;

    AccountController(AccountQueryService accounts, AccountSecurityService security, MfaService mfa,
            CustomerProfileService customerProfiles, StaffProfileService staffProfiles, AccountSessions sessions,
            SessionLoginHandler sessionHandler, AuthEventLogger eventLogger, Clock clock) {
        this.clock = clock;
        this.accounts = accounts;
        this.security = security;
        this.mfa = mfa;
        this.customerProfiles = customerProfiles;
        this.staffProfiles = staffProfiles;
        this.sessions = sessions;
        this.sessionHandler = sessionHandler;
        this.eventLogger = eventLogger;
    }

    @GetMapping
    public ApiSuccessResponse<AccountOverview> overview() {
        return ApiSuccessResponse.of(overviewOf(accounts.getActive(currentAccount())));
    }

    @PutMapping(PROFILE)
    public ApiSuccessResponse<AccountOverview> updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
        UUID accountId = currentAccount();
        Account account = accounts.getActive(accountId);
        Locale locale = body.locale() == null ? null : Locale.forLanguageTag(body.locale());
        if (account.type() == AccountType.CUSTOMER) {
            customerProfiles.update(accountId,
                    new UpdateCustomerProfileCommand(body.firstName(), body.lastName(), body.phone(), locale));
        } else {
            staffProfiles.update(accountId, body.firstName(), body.lastName(), body.phone());
            if (locale != null) {
                accounts.changeLocale(accountId, locale);
            }
        }
        return ApiSuccessResponse.of(overviewOf(accounts.getActive(accountId)));
    }

    @PutMapping(PASSWORD)
    public ApiSuccessResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request) {
        security.changePassword(currentAccount(), body.currentPassword(), body.newPassword(),
                request.getSession().getId(), recentlyAuthenticated(), RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    /**
     * Envía un enlace al email nuevo; el cambio se aplica al confirmarlo.
     */
    @PostMapping(EMAIL)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> changeEmail(@Valid @RequestBody ChangeEmailRequest body,
            HttpServletRequest request) {
        security.requestEmailChange(currentAccount(), body.currentPassword(), body.newEmail(),
                recentlyAuthenticated(), RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @GetMapping(SESSIONS)
    public ApiSuccessResponse<List<SessionView>> sessions(HttpServletRequest request) {
        return ApiSuccessResponse.of(sessions.list(currentAccount(), request.getSession().getId()));
    }

    @DeleteMapping(SESSION)
    public ApiSuccessResponse<Void> revokeSession(@PathVariable String sessionId, HttpServletRequest request) {
        sessions.revoke(currentAccount(), sessionId, request.getSession().getId());
        return ApiSuccessResponse.of(null);
    }

    @GetMapping(MFA)
    public ApiSuccessResponse<MfaStatus> mfaStatus() {
        return ApiSuccessResponse.of(mfa.status(currentAccount()));
    }

    /**
     * Pide la contraseña actual (cuentas sin contraseña: haber entrado hace poco).
     */
    @PostMapping(MFA_SETUP)
    public ApiSuccessResponse<MfaEnrollment> startMfaSetup(@Valid @RequestBody MfaSetupRequest body,
            HttpServletRequest request) {
        return ApiSuccessResponse.of(security.startMfaEnrollment(currentAccount(), body.currentPassword(),
                recentlyAuthenticated(), RequestContextResolver.from(request)));
    }

    @PostMapping(MFA_SETUP_CONFIRM)
    public ApiSuccessResponse<RecoveryCodesResponse> confirmMfaSetup(@Valid @RequestBody CodeRequest body,
            HttpServletRequest request) {
        UUID accountId = currentAccount();
        List<String> codes = mfa.confirmEnrollment(accountId, body.code());
        eventLogger.log(AuthEventType.MFA_ENABLED, accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(new RecoveryCodesResponse(codes));
    }

    @PostMapping(MFA_DISABLE)
    public ApiSuccessResponse<Void> disableMfa(@Valid @RequestBody CodeRequest body, HttpServletRequest request) {
        UUID accountId = currentAccount();
        mfa.disable(accountId, body.code());
        eventLogger.log(AuthEventType.MFA_DISABLED, accountId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(MFA_RECOVERY_CODES)
    public ApiSuccessResponse<RecoveryCodesResponse> regenerateRecoveryCodes(@Valid @RequestBody CodeRequest body) {
        return ApiSuccessResponse.of(new RecoveryCodesResponse(
                mfa.regenerateRecoveryCodes(currentAccount(), body.code())));
    }

    /**
     * Borra la cuenta y cierra la sesión actual.
     */
    @PostMapping(DELETE)
    public ApiSuccessResponse<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest body,
            HttpServletRequest request) {
        security.deleteAccount(currentAccount(), body.currentPassword(), body.mfaCode(), recentlyAuthenticated(),
                RequestContextResolver.from(request));
        sessionHandler.logout(request);
        return ApiSuccessResponse.of(null);
    }

    private AccountOverview overviewOf(Account account) {
        String firstName;
        String lastName;
        String phone;
        if (account.type() == AccountType.CUSTOMER) {
            CustomerProfile profile = customerProfiles.get(account.id());
            firstName = profile.firstName();
            lastName = profile.lastName();
            phone = profile.phone();
        } else {
            StaffUser profile = staffProfiles.get(account.id());
            firstName = profile.firstName().value();
            lastName = profile.lastName().value();
            phone = profile.phone() == null ? null : profile.phone().value();
        }
        return new AccountOverview(account.id(), account.type().name(), account.email().value(),
                account.emailVerifiedAt() != null, account.hasPassword(), account.locale().toLanguageTag(), firstName, lastName, phone,
                account.isSuperAdmin(), mfa.status(account.id()));
    }

    /**
     * Ha iniciado sesión (primer factor) hace menos de {@link #REAUTHENTICATION_WINDOW}.
     * Lo necesitan las cuentas sin contraseña para cambios sensibles.
     */
    private boolean recentlyAuthenticated() {
        Instant threshold = clock.instant().minus(REAUTHENTICATION_WINDOW);
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .filter(FactorGrantedAuthority.class::isInstance)
                .map(authority -> ((FactorGrantedAuthority) authority).getIssuedAt())
                .anyMatch(issuedAt -> issuedAt != null && issuedAt.isAfter(threshold));
    }

    private static UUID currentAccount() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }
}
