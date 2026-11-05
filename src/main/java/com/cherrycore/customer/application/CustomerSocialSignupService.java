package com.cherrycore.customer.application;

import java.time.Clock;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.application.SocialLoginService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.SocialProfile;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Termina el alta de un cliente que viene de Google/Apple: acepta los
 * términos y confirma su nombre. Cuenta (sin contraseña) + perfil.
 */
@Service
public class CustomerSocialSignupService {

    private final SocialLoginService socialLogin;
    private final CustomerRepository customers;
    private final CustomerSettings settings;
    private final Clock clock;

    public CustomerSocialSignupService(SocialLoginService socialLogin, CustomerRepository customers,
            CustomerSettings settings, Clock clock) {
        this.socialLogin = socialLogin;
        this.customers = customers;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedAccount complete(SocialProfile profile, String firstName, String lastName, String phone,
            boolean termsAccepted, Locale locale, RequestContext context) {
        if (!termsAccepted) {
            throw new DomainException(CommonErrorCode.TERMS_NOT_ACCEPTED);
        }
        PersonName first = PersonName.of(firstName);
        PersonName last = PersonName.of(lastName);
        PhoneNumber phoneNumber = phone == null || phone.isBlank() ? null : PhoneNumber.of(phone);

        Account account = socialLogin.createCustomerAccount(profile, locale, context);
        customers.save(Customer.register(account.id(), first, last, phoneNumber, clock.instant(),
                settings.termsVersion()));
        return new AuthenticatedAccount(account.id(), account.type());
    }
}
