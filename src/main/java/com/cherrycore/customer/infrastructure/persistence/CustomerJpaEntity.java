package com.cherrycore.customer.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class CustomerJpaEntity extends AuditableJpaEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "terms_accepted_at", nullable = false, updatable = false)
    private Instant termsAcceptedAt;

    @Column(name = "terms_version", nullable = false, updatable = false)
    private String termsVersion;

    protected CustomerJpaEntity() {
    }

    public CustomerJpaEntity(UUID id, Instant termsAcceptedAt, String termsVersion) {
        super(id);
        this.termsAcceptedAt = termsAcceptedAt;
        this.termsVersion = termsVersion;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Instant getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public String getTermsVersion() {
        return termsVersion;
    }
}
