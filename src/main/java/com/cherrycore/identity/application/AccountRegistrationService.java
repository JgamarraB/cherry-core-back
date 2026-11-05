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
        return register(AccountType.CUSTOMER, rawEmail, rawPassword, requestedLocale, context);
    }

    /**
     * Igual que {@link #registerCustomer} para una cuenta de backoffice (alta de empresa).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Account> registerStaff(String rawEmail, String rawPassword, Locale requestedLocale,
            RequestContext context) {
        return register(AccountType.STAFF, rawEmail, rawPassword, requestedLocale, context);
    }

    /**
     * Crea una cuenta de backoffice ya verificada (al aceptar una invitación).
     * El llamante debe haber comprobado que no existe.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account createInvitedStaff(EmailAddress email, String rawPassword, Locale requestedLocale,
            RequestContext context) {
        settings.passwordPolicy().validate(rawPassword, email);
        Account account = Account.newInvitedStaff(UuidV7.generate(clock), email, passwordHasher.hash(rawPassword),
                settings.resolveLocale(requestedLocale), clock.instant());
        accountRepository.save(account);
        eventLogger.log(AuthEventType.ACCOUNT_REGISTERED, account.id(), context);
        return account;
    }

    private Optional<Account> register(AccountType type, String rawEmail, String rawPassword,
            Locale requestedLocale, RequestContext context) {
        EmailAddress email = EmailAddress.of(rawEmail);
        settings.passwordPolicy().validate(rawPassword, email);

        Optional<Account> existing = accountRepository.findByTypeAndEmail(type, email);
        if (existing.isPresent()) {
            passwordHasher.simulateMatch(rawPassword);
            eventLogger.log(AuthEventType.REGISTRATION_ON_EXISTING_EMAIL, existing.get().id(), context);
            notifier.registrationAttemptedOnExistingAccount(existing.get());
            return Optional.empty();
        }

        String passwordHash = passwordHasher.hash(rawPassword);
        Locale locale = settings.resolveLocale(requestedLocale);
        Account account = type == AccountType.CUSTOMER
                ? Account.newCustomer(UuidV7.generate(clock), email, passwordHash, locale)
                : Account.newStaff(UuidV7.generate(clock), email, passwordHash, locale);
        accountRepository.save(account);

        String rawToken = tokenIssuer.issue(account, OneTimeTokenPurpose.EMAIL_VERIFICATION,
                settings.emailVerificationTtl());
        notifier.emailVerificationRequested(account, rawToken);
        eventLogger.log(AuthEventType.ACCOUNT_REGISTERED, account.id(), context);
        return Optional.of(account);
    }
}
