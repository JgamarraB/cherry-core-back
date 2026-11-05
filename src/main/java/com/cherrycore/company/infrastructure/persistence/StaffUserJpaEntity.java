package com.cherrycore.company.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_users")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class StaffUserJpaEntity extends AuditableJpaEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    protected StaffUserJpaEntity() {
    }

    public StaffUserJpaEntity(UUID id) {
        super(id);
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
