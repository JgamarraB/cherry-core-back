package com.cherrycore.customer.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.customer.domain.CustomerAddress;

public interface CustomerAddressRepository {

    List<CustomerAddress> findByCustomer(UUID customerId);

    /**
     * Solo devuelve la dirección si pertenece a ese cliente: evita que un
     * cliente acceda a direcciones de otro cambiando el id en la URL (IDOR).
     */
    Optional<CustomerAddress> findByIdAndCustomer(UUID addressId, UUID customerId);

    long countByCustomer(UUID customerId);

    void save(CustomerAddress address);

    void delete(CustomerAddress address);
}
