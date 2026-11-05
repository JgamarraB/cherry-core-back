package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.AccountSessionRevoker;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.OneTimeTokenRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.application.port.SecureTokenService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountDeletionRequested;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Cambios sensibles que hace el propio usuario desde su perfil: contraseña,
 * email y borrado de la cuenta. Todos piden la contraseña actual.
 */
@Service
public class AccountSecurityService {

    private static final String DELETED_EMAIL_FORMAT = "deleted-%s@deleted.invalid";

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final OneTimeTokenIssuer tokenIssuer;
    private final OneTimeTokenRepository tokenRepository;
    private final SecureTokenService tokenService;
    private final AccountSessionRevoker sessionRevoker;
    private final MfaService mfa;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final ApplicationEventPublisher events;
    private final IdentitySettings settings;
    private final Clock clock;

    public AccountSecurityService(AccountRepository accounts, PasswordHasher passwordHasher,
            OneTimeTokenIssuer tokenIssuer, OneTimeTokenRepository tokenRepository, SecureTokenService tokenService,
            AccountSessionRevoker sessionRevoker, MfaService mfa, IdentityNotifier notifier,
            AuthEventLogger eventLogger, ApplicationEventPublisher events, IdentitySettings settings, Clock clock) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.sessionRevoker = sessionRevoker;
        this.mfa = mfa;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Cambia la contraseña y cierra el resto de sesiones (mantiene la actual).
     */
    @Transactional
    public void changePassword(UUID accountId, String currentPassword, String newPassword, String keepSessionId,
            RequestContext context) {
        Account account = activeAccount(accountId);
        requirePassword(account, currentPassword);
        settings.passwordPolicy().validate(newPassword, account.email());
        account.changePassword(passwordHasher.hash(newPassword));
        accounts.save(account);
        sessionRevoker.revokeAllExcept(accountId, keepSessionId);
        notifier.passwordChanged(account);
        eventLogger.log(AuthEventType.PASSWORD_CHANGED, accountId, context);
    }

    /**
     * Envía un enlace al email nuevo. El cambio solo se aplica al pulsarlo.
     */
    @Transactional
    public void requestEmailChange(UUID accountId, String currentPassword, String rawNewEmail,
            RequestContext context) {
        Account account = activeAccount(accountId);
        requirePassword(account, currentPassword);
        EmailAddress newEmail = EmailAddress.of(rawNewEmail);
        if (newEmail.equals(account.email())) {
            throw new DomainException(IdentityErrorCode.EMAIL_UNCHANGED);
        }
        if (accounts.existsByTypeAndEmail(account.type(), newEmail)) {
            throw new DomainException(IdentityErrorCode.EMAIL_ALREADY_IN_USE);
        }
        String rawToken = tokenIssuer.issue(account, OneTimeTokenPurpose.EMAIL_CHANGE,
                settings.emailVerificationTtl(), newEmail.value());
        notifier.emailChangeRequested(account, newEmail, rawToken);
        eventLogger.log(AuthEventType.EMAIL_CHANGE_REQUESTED, accountId, context);
    }

    @Transactional
    public void confirmEmailChange(String rawToken, RequestContext context) {
        Instant now = clock.instant();
        OneTimeToken token = tokenRepository.findByHashForUpdate(tokenService.hash(rawToken))
                .filter(found -> found.isUsableFor(OneTimeTokenPurpose.EMAIL_CHANGE, now))
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_OR_EXPIRED_TOKEN));
        Account account = activeAccount(token.accountId());
        EmailAddress newEmail = new EmailAddress(token.payload());
        if (accounts.existsByTypeAndEmail(account.type(), newEmail)) {
            throw new DomainException(IdentityErrorCode.EMAIL_ALREADY_IN_USE);
        }
        EmailAddress previous = account.email();
        token.markUsed(now);
        tokenRepository.save(token);
        account.changeEmail(newEmail, now);
        accounts.save(account);
        notifier.emailChanged(previous, account.locale());
        eventLogger.log(AuthEventType.EMAIL_CHANGED, account.id(), context);
    }

    /**
     * Borra la cuenta (RGPD): cada módulo borra sus datos, se anonimiza la
     * cuenta, se cierran todas las sesiones y se envía una confirmación.
     *
     * @param mfaCode obligatorio si la cuenta tiene MFA activado
     */
    @Transactional
    public void deleteAccount(UUID accountId, String currentPassword, String mfaCode, RequestContext context) {
        Account account = activeAccount(accountId);
        requirePassword(account, currentPassword);
        if (mfa.isEnabled(accountId) && !mfa.verify(accountId, mfaCode)) {
            throw new DomainException(IdentityErrorCode.INVALID_MFA_CODE);
        }
        EmailAddress originalEmail = account.email();
        Locale locale = account.locale();

        events.publishEvent(new AccountDeletionRequested(accountId, account.type()));

        mfa.reset(accountId);
        account.anonymize(new EmailAddress(DELETED_EMAIL_FORMAT.formatted(accountId)));
        accounts.save(account);
        accounts.softDelete(accountId);
        sessionRevoker.revokeAll(accountId);
        notifier.accountDeleted(originalEmail, locale);
        eventLogger.log(AuthEventType.ACCOUNT_DELETED, accountId, context);
    }

    private Account activeAccount(UUID accountId) {
        return accounts.findById(accountId)
                .filter(Account::isActive)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
    }

    /*
     * Las cuentas creadas solo con login social no tienen contraseña: para
     * ellas basta la sesión (y el MFA si lo tienen).
     */
    private void requirePassword(Account account, String currentPassword) {
        if (account.hasPassword() && !passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new DomainException(IdentityErrorCode.WRONG_CURRENT_PASSWORD);
        }
    }
}
