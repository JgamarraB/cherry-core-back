package com.cherrycore.identity.application;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.OneTimeTokenPurpose;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Crea cuentas. Lo usan los módulos de perfil (customer, y más adelante staff)
 * dentro de su propia transacción.
 */
@Service
public class AccountRegistrationService {

    private final AccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final OneTimeTokenIssuer tokenIssuer;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public AccountRegistrationService(AccountRepository accountRepository, PasswordHasher passwordHasher,
            OneTimeTokenIssuer tokenIssuer, IdentityNotifier notifier, AuthEventLogger eventLogger,
            IdentitySettings settings, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * Registra una cuenta de cliente y le envía el email de verificación.
     *
     * <p>Si el email ya existe no se crea nada y se avisa al dueño real por
     * email. La respuesta al que se registra es la misma en ambos casos, así
     * que no puede usar el registro para descubrir qué emails existen.</p>
     *
     * @return la cuenta creada, o vacío si el email ya estaba registrado
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Account> registerCustomer(String rawEmail, String rawPassword, Locale requestedLocale,
            RequestContext context) {
        EmailAddress email = EmailAddress.of(rawEmail);
        settings.passwordPolicy().validate(rawPassword, email);

        Optional<Account> existing = accountRepository.findByTypeAndEmail(AccountType.CUSTOMER, email);
        if (existing.isPresent()) {
            passwordHasher.simulateMatch(rawPassword);
            eventLogger.log(AuthEventType.REGISTRATION_ON_EXISTING_EMAIL, existing.get().id(), context);
            notifier.registrationAttemptedOnExistingAccount(existing.get());
            return Optional.empty();
        }

        Account account = Account.newCustomer(UuidV7.generate(clock), email, passwordHasher.hash(rawPassword),
                settings.resolveLocale(requestedLocale));
        accountRepository.save(account);

        String rawToken = tokenIssuer.issue(account, OneTimeTokenPurpose.EMAIL_VERIFICATION,
                settings.emailVerificationTtl());
        notifier.emailVerificationRequested(account, rawToken);
        eventLogger.log(AuthEventType.ACCOUNT_REGISTERED, account.id(), context);
        return Optional.of(account);
    }
}
