package com.cherrycore.customer.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.customer.application.port.CustomerAddressRepository;
import com.cherrycore.customer.domain.CountryCode;
import com.cherrycore.customer.domain.CustomerAddress;
import com.cherrycore.customer.domain.PostalAddress;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class CustomerAddressPersistenceAdapter implements CustomerAddressRepository {

    private final CustomerAddressJpaRepository jpaRepository;
    private final Clock clock;

    CustomerAddressPersistenceAdapter(CustomerAddressJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public List<CustomerAddress> findByCustomer(UUID customerId) {
        return jpaRepository.findByCustomerIdOrderByCreatedAtAsc(customerId).stream()
                .map(CustomerAddressPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<CustomerAddress> findByIdAndCustomer(UUID addressId, UUID customerId) {
        return jpaRepository.findByIdAndCustomerId(addressId, customerId)
                .map(CustomerAddressPersistenceAdapter::toDomain);
    }

    @Override
    public long countByCustomer(UUID customerId) {
        return jpaRepository.countByCustomerId(customerId);
    }

    /*
     * saveAndFlush: el índice único "una sola dirección por defecto" exige
     * que al cambiar la de por defecto se guarde primero la que la pierde.
     */
    @Override
    public void save(CustomerAddress address) {
        CustomerAddressJpaEntity entity = jpaRepository.findById(address.id())
                .orElseGet(() -> new CustomerAddressJpaEntity(address.id(), address.customerId()));
        PostalAddress data = address.address();
        entity.setLine1(data.line1());
        entity.setLine2(data.line2());
        entity.setCity(data.city());
        entity.setPostalCode(data.postalCode());
        entity.setRegion(data.region());
        entity.setCountryCode(data.country().value());
        entity.setDefault(address.isDefault());
        jpaRepository.saveAndFlush(entity);
    }

    @Override
    public void delete(CustomerAddress address) {
        jpaRepository.findById(address.id()).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
            entity.setDefault(false);
            jpaRepository.saveAndFlush(entity);
        });
    }

    private static CustomerAddress toDomain(CustomerAddressJpaEntity entity) {
        return new CustomerAddress(entity.getId(), entity.getCustomerId(),
                new PostalAddress(entity.getLine1(), entity.getLine2(), entity.getCity(), entity.getPostalCode(),
                        entity.getRegion(), new CountryCode(entity.getCountryCode())),
                entity.isDefault());
    }
}
