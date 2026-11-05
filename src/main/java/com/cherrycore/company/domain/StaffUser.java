package com.cherrycore.company.domain;

import java.util.Objects;
import java.util.UUID;

import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Perfil de una persona de backoffice. Su id es el de su cuenta (accounts.id).
 */
public class StaffUser {

    private static final String ANONYMOUS_FIRST_NAME = "Deleted";
    private static final String ANONYMOUS_LAST_NAME = "User";

    private final UUID id;
    private PersonName firstName;
    private PersonName lastName;
    private PhoneNumber phone;

    public StaffUser(UUID id, PersonName firstName, PersonName lastName, PhoneNumber phone) {
        this.id = Objects.requireNonNull(id);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.phone = phone;
    }

    public void updateProfile(PersonName newFirstName, PersonName newLastName, PhoneNumber newPhone) {
        firstName = Objects.requireNonNull(newFirstName);
        lastName = Objects.requireNonNull(newLastName);
        phone = newPhone;
    }

    /**
     * Borrado de cuenta (RGPD): se eliminan los datos personales.
     */
    public void anonymize() {
        firstName = new PersonName(ANONYMOUS_FIRST_NAME);
        lastName = new PersonName(ANONYMOUS_LAST_NAME);
        phone = null;
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
}
