package com.cherrycore.customer.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.customer.application.port.CustomerAddressRepository;
import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.CustomerAddress;
import com.cherrycore.customer.domain.CustomerErrorCode;
import com.cherrycore.customer.domain.PostalAddress;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.domain.value.CountryCode;

/**
 * Direcciones del cliente. Siempre se trabaja con el id del cliente
 * autenticado, nunca con uno recibido en la petición.
 */
@Service
public class CustomerAddressService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSettings settings;
    private final Clock clock;

    public CustomerAddressService(CustomerAddressRepository addressRepository, CustomerRepository customerRepository,
            CustomerSettings settings, Clock clock) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> list(UUID customerId) {
        return addressRepository.findByCustomer(customerId);
    }

    @Transactional
    public CustomerAddress add(UUID customerId, AddressData data) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new DomainException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
        long existing = addressRepository.countByCustomer(customerId);
        if (existing >= settings.maxAddresses()) {
            throw new DomainException(CustomerErrorCode.ADDRESS_LIMIT_REACHED);
        }
        // La primera dirección es la de por defecto.
        boolean makeDefault = data.makeDefault() || existing == 0;
        if (makeDefault) {
            clearDefault(customerId);
        }
        CustomerAddress address = new CustomerAddress(UuidV7.generate(clock), customerId, toPostalAddress(data),
                makeDefault);
        addressRepository.save(address);
        return address;
    }

    @Transactional
    public CustomerAddress update(UUID customerId, UUID addressId, AddressData data) {
        CustomerAddress address = find(customerId, addressId);
        address.change(toPostalAddress(data));
        if (data.makeDefault() && !address.isDefault()) {
            clearDefault(customerId);
            address.markDefault();
        }
        addressRepository.save(address);
        return address;
    }

    @Transactional
    public void delete(UUID customerId, UUID addressId) {
        addressRepository.delete(find(customerId, addressId));
    }

    private CustomerAddress find(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomer(addressId, customerId)
                .orElseThrow(() -> new DomainException(CustomerErrorCode.ADDRESS_NOT_FOUND));
    }

    private void clearDefault(UUID customerId) {
        addressRepository.findByCustomer(customerId).stream()
                .filter(CustomerAddress::isDefault)
                .forEach(current -> {
                    current.unmarkDefault();
                    addressRepository.save(current);
                });
    }

    private static PostalAddress toPostalAddress(AddressData data) {
        return new PostalAddress(data.line1(), data.line2(), data.city(), data.postalCode(), data.region(),
                CountryCode.of(data.countryCode()));
    }
}
