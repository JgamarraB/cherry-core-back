package com.cherrycore.customer.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cherrycore.customer.application.port.CustomerAddressRepository;
import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.identity.domain.AccountDeletionRequested;
import com.cherrycore.identity.domain.AccountType;

/**
 * Al borrar una cuenta de cliente: se anonimiza el perfil y se borran sus
 * direcciones. Se ejecuta en la misma transacción que el borrado.
 */
@Component
class CustomerAccountDeletionListener {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    CustomerAccountDeletionListener(CustomerRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @EventListener
    void on(AccountDeletionRequested event) {
        if (event.type() != AccountType.CUSTOMER) {
            return;
        }
        addresses.findByCustomer(event.accountId()).forEach(addresses::delete);
        customers.findById(event.accountId()).ifPresent(customer -> {
            customer.anonymize();
            customers.save(customer);
            customers.softDelete(customer.id());
        });
    }
}
