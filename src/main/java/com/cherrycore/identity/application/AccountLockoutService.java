package com.cherrycore.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AuthEventType;

/**
 * Contador de intentos fallidos de la CUENTA (no de la sesión): contraseña,
 * código MFA y reautenticaciones de "mi cuenta" suman en el mismo contador,
 * así que repetir el login no da más intentos.
 *
 * <p>Transacción propia (REQUIRES_NEW): el fallo se guarda aunque la
 * operación que lo provoca se deshaga.</p>
 */
@Service
public class AccountLockoutService {

    private final AccountRepository accounts;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public AccountLockoutService(AccountRepository accounts, IdentityNotifier notifier, AuthEventLogger eventLogger,
            IdentitySettings settings, Clock clock) {
        this.accounts = accounts;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID accountId, AuthEventType reason, RequestContext context) {
        accounts.findByIdForUpdate(accountId).ifPresent(account -> {
            boolean lockedNow = account.registerFailedLogin(clock.instant(), settings.loginPolicy());
            accounts.save(account);
            eventLogger.log(reason, accountId, context);
            if (lockedNow) {
                eventLogger.log(AuthEventType.ACCOUNT_LOCKED, accountId, context);
                notifier.accountLocked(account);
            }
        });
    }

    @Transactional
    public void recordSuccess(UUID accountId) {
        accounts.findByIdForUpdate(accountId).ifPresent(account -> {
            account.registerSuccessfulLogin(clock.instant());
            accounts.save(account);
        });
    }

    @Transactional(readOnly = true)
    public boolean isLocked(UUID accountId) {
        return accounts.findById(accountId).map(account -> account.isLockedAt(clock.instant())).orElse(true);
    }

    public boolean isLocked(Account account) {
        return account.isLockedAt(clock.instant());
    }
}
