package com.cherrycore.customer.application.port;

import java.util.Optional;
import java.util.UUID;

import com.cherrycore.customer.domain.Customer;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    void save(Customer customer);
}
