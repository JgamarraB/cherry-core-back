package com.cherrycore.catalog.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_permissions")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class ApplicationPermissionJpaEntity extends AuditableJpaEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "description")
    private String description;

    protected ApplicationPermissionJpaEntity() {
    }

    public ApplicationPermissionJpaEntity(UUID id, UUID applicationId, String code) {
        super(id);
        this.applicationId = applicationId;
        this.code = code;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
