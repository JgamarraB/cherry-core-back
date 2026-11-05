package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.AccountSessionRevoker;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.OneTimeTokenRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.application.port.SecureTokenService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.OneTimeToken;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * "He olvidado mi contraseña".
 */
@Service
public class PasswordResetService {

    private final AccountRepository accountRepository;
    private final OneTimeTokenRepository tokenRepository;
    private final SecureTokenService tokenService;
    private final OneTimeTokenIssuer tokenIssuer;
    private final PasswordHasher passwordHasher;
    private final AccountSessionRevoker sessionRevoker;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public PasswordResetService(AccountRepository accountRepository, OneTimeTokenRepository tokenRepository,
            SecureTokenService tokenService, OneTimeTokenIssuer tokenIssuer, PasswordHasher passwordHasher,
            AccountSessionRevoker sessionRevoker, IdentityNotifier notifier, AuthEventLogger eventLogger,
            IdentitySettings settings, Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.tokenService = tokenService;
        this.tokenIssuer = tokenIssuer;
        this.passwordHasher = passwordHasher;
        this.sessionRevoker = sessionRevoker;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Envía el enlace de recuperación si la cuenta existe. La respuesta es
     * siempre la misma, exista o no.
     */
    @Transactional
    public void request(AccountType type, String rawEmail, RequestContext context) {
        EmailAddress email;
        try {
            email = EmailAddress.of(rawEmail);
        } catch (DomainException invalidEmail) {
            return;
        }
        accountRepository.findByTypeAndEmail(type, email)
                .filter(account -> !account.isDisabled())
                .ifPresent(account -> {
                    String rawToken = tokenIssuer.issue(account, OneTimeTokenPurpose.PASSWORD_RESET,
                            settings.passwordResetTtl());
                    notifier.passwordResetRequested(account, rawToken);
                    eventLogger.log(AuthEventType.PASSWORD_RESET_REQUESTED, account.id(), context);
                });
    }

    /**
     * Envía el enlace de restablecer contraseña a una cuenta concreta (lo usa
     * el superadmin y el alta del primer superadmin).
     */
    @Transactional
    public void requestFor(UUID accountId, RequestContext context) {
        accountRepository.findById(accountId).filter(account -> !account.isDisabled()).ifPresent(account -> {
            notifier.passwordResetRequested(account, tokenIssuer.issue(account, OneTimeTokenPurpose.PASSWORD_RESET,
                    settings.passwordResetTtl()));
            eventLogger.log(AuthEventType.PASSWORD_RESET_REQUESTED, account.id(), context);
        });
    }

    /**
     * Cambia la contraseña con el token del email y cierra todas las sesiones
     * abiertas (por si alguien se había hecho con la cuenta).
     */
    @Transactional
    public void reset(String rawToken, String newPassword, RequestContext context) {
        Instant now = clock.instant();
        OneTimeToken token = tokenRepository.findByHashForUpdate(tokenService.hash(rawToken))
                .filter(found -> found.isUsableFor(OneTimeTokenPurpose.PASSWORD_RESET, now))
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_OR_EXPIRED_TOKEN));

        Account account = accountRepository.findById(token.accountId())
                .filter(found -> !found.isDisabled())
                .orElseThrow(() -> new DomainException(IdentityErrorCode.INVALID_OR_EXPIRED_TOKEN));

        settings.passwordPolicy().validate(newPassword, account.email());

        token.markUsed(now);
        tokenRepository.save(token);
        account.resetPassword(passwordHasher.hash(newPassword), now);
        accountRepository.save(account);
        // Un cambio de email pedido por quien tenía la sesión no debe poder completarse después.
        tokenIssuer.revokePending(account.id(), OneTimeTokenPurpose.PASSWORD_RESET, OneTimeTokenPurpose.EMAIL_CHANGE);
        sessionRevoker.revokeAll(account.id());
        notifier.passwordChanged(account);
        eventLogger.log(AuthEventType.PASSWORD_RESET_COMPLETED, account.id(), context);
    }
}
