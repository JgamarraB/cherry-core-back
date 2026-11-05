package com.cherrycore.customer.domain;

import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Perfil global del cliente final. Su id es el de su cuenta (accounts.id).
 * Cada app guarda aparte los datos propios de su negocio.
 */
public class Customer {

    private final UUID id;
    private PersonName firstName;
    private PersonName lastName;
    private PhoneNumber phone;
    private final Instant termsAcceptedAt;
    private final String termsVersion;

    private Customer(UUID id, PersonName firstName, PersonName lastName, PhoneNumber phone,
            Instant termsAcceptedAt, String termsVersion) {
        this.id = Objects.requireNonNull(id);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.phone = phone;
        this.termsAcceptedAt = Objects.requireNonNull(termsAcceptedAt);
        this.termsVersion = Objects.requireNonNull(termsVersion);
    }

    public static Customer register(UUID accountId, PersonName firstName, PersonName lastName, PhoneNumber phone,
            Instant termsAcceptedAt, String termsVersion) {
        return new Customer(accountId, firstName, lastName, phone, termsAcceptedAt, termsVersion);
    }

    public static Customer restore(UUID id, PersonName firstName, PersonName lastName, PhoneNumber phone,
            Instant termsAcceptedAt, String termsVersion) {
        return new Customer(id, firstName, lastName, phone, termsAcceptedAt, termsVersion);
    }

    public void updateProfile(PersonName newFirstName, PersonName newLastName, PhoneNumber newPhone) {
        this.firstName = Objects.requireNonNull(newFirstName);
        this.lastName = Objects.requireNonNull(newLastName);
        this.phone = newPhone;
    }

    public UUID id() {
        return id;
    }

    public PersonName firstName() {
        return firstName;
    }

    public PersonName lastName() {
        return lastName;
    }

    public PhoneNumber phone() {
        return phone;
    }

    public Instant termsAcceptedAt() {
        return termsAcceptedAt;
    }

    public String termsVersion() {
        return termsVersion;
    }
}
