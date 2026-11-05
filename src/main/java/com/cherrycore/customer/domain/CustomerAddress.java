package com.cherrycore.customer.domain;

import java.util.Objects;
import java.util.UUID;

public class CustomerAddress {

    private final UUID id;
    private final UUID customerId;
    private PostalAddress address;
    private boolean isDefault;

    public CustomerAddress(UUID id, UUID customerId, PostalAddress address, boolean isDefault) {
        this.id = Objects.requireNonNull(id);
        this.customerId = Objects.requireNonNull(customerId);
        this.address = Objects.requireNonNull(address);
        this.isDefault = isDefault;
    }

    public void change(PostalAddress newAddress) {
        this.address = Objects.requireNonNull(newAddress);
    }

    public void markDefault() {
        isDefault = true;
    }

    public void unmarkDefault() {
        isDefault = false;
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public PostalAddress address() {
        return address;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
