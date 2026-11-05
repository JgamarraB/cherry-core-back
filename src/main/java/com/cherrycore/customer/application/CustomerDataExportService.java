package com.cherrycore.customer.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.customer.application.CustomerDataExport.AddressData;
import com.cherrycore.customer.application.port.CustomerAddressRepository;
import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.customer.domain.CustomerErrorCode;
import com.cherrycore.customer.domain.PostalAddress;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Parte de cliente de "descargar mis datos".
 */
@Service
public class CustomerDataExportService {

    private final CustomerRepository customers;
    private final CustomerAddressRepository addresses;

    public CustomerDataExportService(CustomerRepository customers, CustomerAddressRepository addresses) {
        this.customers = customers;
        this.addresses = addresses;
    }

    @Transactional(readOnly = true)
    public CustomerDataExport export(UUID customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new DomainException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
        var addressList = addresses.findByCustomer(customerId).stream()
                .map(address -> {
                    PostalAddress postal = address.address();
                    return new AddressData(postal.line1(), postal.line2(), postal.city(), postal.postalCode(),
                            postal.region(), postal.country() == null ? null : postal.country().value(),
                            address.isDefault());
                })
                .toList();
        return new CustomerDataExport(customer.firstName().value(), customer.lastName().value(),
                customer.phone() == null ? null : customer.phone().value(), customer.termsAcceptedAt(),
                customer.termsVersion(), addressList);
    }
}
