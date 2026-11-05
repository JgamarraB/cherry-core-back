package com.cherrycore.customer.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;
import com.cherrycore.identity.application.AccountRegistrationService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Alta de cliente final: cuenta + perfil en una única transacción.
 *
 * <p>Si el email ya existe, la respuesta es la misma que si se hubiera
 * creado (ver {@link AccountRegistrationService#registerCustomer}).</p>
 */
@Service
public class RegisterCustomerService {

    private final AccountRegistrationService accountRegistration;
    private final CustomerRepository customerRepository;
    private final CustomerSettings settings;
    private final Clock clock;

    public RegisterCustomerService(AccountRegistrationService accountRegistration,
            CustomerRepository customerRepository, CustomerSettings settings, Clock clock) {
        this.accountRegistration = accountRegistration;
        this.customerRepository = customerRepository;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public void register(RegisterCustomerCommand command, RequestContext context) {
        if (!command.termsAccepted()) {
            throw new DomainException(CommonErrorCode.TERMS_NOT_ACCEPTED);
        }
        // Se valida todo antes de crear nada.
        PersonName firstName = PersonName.of(command.firstName());
        PersonName lastName = PersonName.of(command.lastName());
        PhoneNumber phone = command.phone() == null || command.phone().isBlank()
                ? null : PhoneNumber.of(command.phone());

        accountRegistration.registerCustomer(command.email(), command.password(), command.locale(), context)
                .ifPresent(account -> customerRepository.save(Customer.register(account.id(), firstName, lastName,
                        phone, clock.instant(), settings.termsVersion())));
    }
}
