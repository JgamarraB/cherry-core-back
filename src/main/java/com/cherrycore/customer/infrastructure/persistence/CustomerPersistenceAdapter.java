package com.cherrycore.customer.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.customer.domain.PersonName;
import com.cherrycore.customer.domain.PhoneNumber;

@Repository
class CustomerPersistenceAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;

    CustomerPersistenceAdapter(CustomerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpaRepository.findById(id).map(entity -> Customer.restore(entity.getId(),
                new PersonName(entity.getFirstName()), new PersonName(entity.getLastName()),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()),
                entity.getTermsAcceptedAt(), entity.getTermsVersion()));
    }

    @Override
    public void save(Customer customer) {
        CustomerJpaEntity entity = jpaRepository.findById(customer.id())
                .orElseGet(() -> new CustomerJpaEntity(customer.id(), customer.termsAcceptedAt(),
                        customer.termsVersion()));
        entity.setFirstName(customer.firstName().value());
        entity.setLastName(customer.lastName().value());
        entity.setPhone(customer.phone() == null ? null : customer.phone().value());
        jpaRepository.save(entity);
    }
}
