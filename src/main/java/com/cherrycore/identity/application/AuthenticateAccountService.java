package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Comprueba email y contraseña.
 *
 * <p>Medidas de seguridad:</p>
 * <ul>
 *   <li>Un único error ({@code INVALID_CREDENTIALS}) para email inexistente,
 *       contraseña incorrecta, cuenta bloqueada o desactivada.</li>
 *   <li>Mismo tiempo de respuesta exista o no la cuenta.</li>
 *   <li>Bloqueo temporal tras N intentos, con aviso por email.</li>
 *   <li>La fila de la cuenta se bloquea durante la comprobación para que los
 *       intentos simultáneos cuenten todos.</li>
 * </ul>
 *
 * <p>{@code noRollbackFor}: aunque se lance el error, el contador de intentos
 * y la auditoría se tienen que guardar.</p>
 */
@Service
public class AuthenticateAccountService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public AuthenticateAccountService(AccountRepository accountRepository, PasswordHasher passwordHasher,
            IdentityNotifier notifier, AuthEventLogger eventLogger, IdentitySettings settings, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = DomainException.class)
    public AuthenticatedAccount authenticate(AccountType type, String rawEmail, String rawPassword,
            RequestContext context) {
        Optional<Account> found = findForUpdate(type, rawEmail);
        if (found.isEmpty()) {
            passwordHasher.simulateMatch(rawPassword);
            eventLogger.log(AuthEventType.LOGIN_FAILED, null, context);
            throw invalidCredentials();
        }

        Account account = found.get();
        Instant now = clock.instant();

        if (account.isLockedAt(now) || !account.hasPassword()) {
            passwordHasher.simulateMatch(rawPassword);
            eventLogger.log(AuthEventType.LOGIN_BLOCKED_WHILE_LOCKED, account.id(), context);
            throw invalidCredentials();
        }

        if (!passwordHasher.matches(rawPassword, account.passwordHash())) {
            handleWrongPassword(account, now, context);
            throw invalidCredentials();
        }

        if (account.isDisabled()) {
            eventLogger.log(AuthEventType.LOGIN_FAILED, account.id(), context);
            throw invalidCredentials();
        }

        if (account.isPendingVerification()) {
            // La contraseña es correcta: ya se puede decir el motivo sin desvelar nada.
            eventLogger.log(AuthEventType.LOGIN_UNVERIFIED_EMAIL, account.id(), context);
            throw new DomainException(IdentityErrorCode.EMAIL_NOT_VERIFIED);
        }

        account.registerSuccessfulLogin(now);
        accountRepository.save(account);
        eventLogger.log(AuthEventType.LOGIN_SUCCEEDED, account.id(), context);
        return new AuthenticatedAccount(account.id(), account.type());
    }

    private Optional<Account> findForUpdate(AccountType type, String rawEmail) {
        try {
            return accountRepository.findByTypeAndEmailForUpdate(type, EmailAddress.of(rawEmail));
        } catch (DomainException invalidEmail) {
            return Optional.empty();
        }
    }

    private void handleWrongPassword(Account account, Instant now, RequestContext context) {
        boolean lockedNow = account.registerFailedLogin(now, settings.loginPolicy());
        accountRepository.save(account);
        eventLogger.log(AuthEventType.LOGIN_FAILED, account.id(), context);
        if (lockedNow) {
            eventLogger.log(AuthEventType.ACCOUNT_LOCKED, account.id(), context);
            notifier.accountLocked(account);
        }
    }

    private static DomainException invalidCredentials() {
        return new DomainException(IdentityErrorCode.INVALID_CREDENTIALS);
    }
}
