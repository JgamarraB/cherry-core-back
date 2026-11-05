package com.cherrycore.customer.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerAddressJpaRepository extends JpaRepository<CustomerAddressJpaEntity, UUID> {

    List<CustomerAddressJpaEntity> findByCustomerIdOrderByCreatedAtAsc(UUID customerId);

    Optional<CustomerAddressJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    long countByCustomerId(UUID customerId);
}
