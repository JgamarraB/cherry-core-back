package com.cherrycore.identity.application;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.FederatedIdentityRepository;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.FederatedIdentity;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.SocialProfile;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Login con Google / Apple.
 *
 * <ol>
 *   <li>Si el usuario del proveedor ya está vinculado: entra.</li>
 *   <li>Si hay una cuenta con ese email y el proveedor garantiza que el email
 *       es suyo: se vincula y entra. Sin esa garantía NO se vincula (alguien
 *       podría crear una cuenta en el proveedor con el email de otro).</li>
 *   <li>Cliente sin cuenta: hay que aceptar los términos para crearla.</li>
 *   <li>Backoffice sin cuenta: no se crea (se entra por registro o invitación).</li>
 * </ol>
 */
@Service
public class SocialLoginService {

    private final FederatedIdentityRepository identities;
    private final AccountRepository accounts;
    private final AuthEventLogger eventLogger;
    private final IdentitySettings settings;
    private final Clock clock;

    public SocialLoginService(FederatedIdentityRepository identities, AccountRepository accounts,
            AuthEventLogger eventLogger, IdentitySettings settings, Clock clock) {
        this.identities = identities;
        this.accounts = accounts;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public SocialLoginResult signIn(SocialProfile profile, AccountType type, RequestContext context) {
        Optional<Account> linked = identities.findByProviderAndSubject(profile.provider(), profile.subject())
                .flatMap(identity -> accounts.findById(identity.accountId()))
                .filter(account -> account.type() == type);
        if (linked.isPresent()) {
            return signedIn(linked.get(), AuthEventType.SOCIAL_LOGIN, context);
        }

        if (!profile.emailVerified() || profile.email() == null) {
            throw new DomainException(IdentityErrorCode.SOCIAL_EMAIL_NOT_VERIFIED);
        }
        EmailAddress email = EmailAddress.of(profile.email());
        Optional<Account> byEmail = accounts.findByTypeAndEmail(type, email);
        if (byEmail.isPresent()) {
            Account account = byEmail.get();
            if (account.isPendingVerification()) {
                // El proveedor acaba de demostrar que el email es suyo.
                account.verifyEmail(clock.instant());
                accounts.save(account);
            }
            link(account.id(), profile);
            return signedIn(account, AuthEventType.SOCIAL_ACCOUNT_LINKED, context);
        }

        if (type == AccountType.STAFF) {
            throw new DomainException(IdentityErrorCode.SOCIAL_ACCOUNT_NOT_FOUND);
        }
        return new SocialLoginResult.SignupRequired(profile);
    }

    /**
     * Crea la cuenta de un cliente que viene de Google/Apple (sin contraseña)
     * y la vincula. El perfil lo crea el módulo de clientes.
     */
    @Transactional
    public Account createCustomerAccount(SocialProfile profile, Locale locale, RequestContext context) {
        EmailAddress email = EmailAddress.of(profile.email());
        if (accounts.existsByTypeAndEmail(AccountType.CUSTOMER, email)) {
            throw new DomainException(IdentityErrorCode.EMAIL_ALREADY_IN_USE);
        }
        Account account = Account.newSocialCustomer(UuidV7.generate(clock), email, settings.resolveLocale(locale),
                clock.instant());
        accounts.save(account);
        link(account.id(), profile);
        eventLogger.log(AuthEventType.ACCOUNT_REGISTERED, account.id(), context);
        return account;
    }

    @Transactional
    public void unlinkAll(UUID accountId) {
        identities.deleteByAccount(accountId);
    }

    private void link(UUID accountId, SocialProfile profile) {
        identities.save(new FederatedIdentity(UuidV7.generate(clock), accountId, profile.provider(),
                profile.subject(), profile.email()));
    }

    private SocialLoginResult signedIn(Account account, AuthEventType event, RequestContext context) {
        if (!account.isActive()) {
            throw new DomainException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        eventLogger.log(event, account.id(), context);
        return new SocialLoginResult.SignedIn(new AuthenticatedAccount(account.id(), account.type()));
    }
}
