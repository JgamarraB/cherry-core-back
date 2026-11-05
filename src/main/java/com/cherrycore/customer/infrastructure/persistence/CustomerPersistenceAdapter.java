package com.cherrycore.customer.infrastructure.persistence;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.customer.application.port.CustomerRepository;
import com.cherrycore.customer.domain.Customer;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class CustomerPersistenceAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;
    private final Clock clock;

    CustomerPersistenceAdapter(CustomerJpaRepository jpaRepository, Clock clock) {
        this.jpaRepository = jpaRepository;
        this.clock = clock;
    }

    @Override
    public void softDelete(UUID id) {
        jpaRepository.findById(id).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(id));
            jpaRepository.saveAndFlush(entity);
        });
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
