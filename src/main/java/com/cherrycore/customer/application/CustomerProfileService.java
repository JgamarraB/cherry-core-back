package com.cherrycore.customer.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.customer.domain.CustomerErrorCode;
import com.cherrycore.customer.domain.PersonName;
import com.cherrycore.customer.domain.PhoneNumber;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.shared.domain.error.DomainException;

@Service
public class CustomerProfileService {

    private final CustomerRepository customerRepository;
    private final AccountQueryService accountQuery;

    public CustomerProfileService(CustomerRepository customerRepository, AccountQueryService accountQuery) {
        this.customerRepository = customerRepository;
        this.accountQuery = accountQuery;
    }

    @Transactional(readOnly = true)
    public CustomerProfile get(UUID customerId) {
        return toProfile(accountQuery.getActive(customerId), findCustomer(customerId));
    }

    @Transactional
    public CustomerProfile update(UUID customerId, UpdateCustomerProfileCommand command) {
        Customer customer = findCustomer(customerId);
        customer.updateProfile(PersonName.of(command.firstName()), PersonName.of(command.lastName()),
                command.phone() == null || command.phone().isBlank() ? null : PhoneNumber.of(command.phone()));
        customerRepository.save(customer);

        Account account = command.locale() == null
                ? accountQuery.getActive(customerId)
                : accountQuery.changeLocale(customerId, command.locale());
        return toProfile(account, customer);
    }

    private Customer findCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new DomainException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    }

    private static CustomerProfile toProfile(Account account, Customer customer) {
        return new CustomerProfile(customer.id(), account.email().value(), customer.firstName().value(),
                customer.lastName().value(), customer.phone() == null ? null : customer.phone().value(),
                account.locale().toLanguageTag());
    }
}
