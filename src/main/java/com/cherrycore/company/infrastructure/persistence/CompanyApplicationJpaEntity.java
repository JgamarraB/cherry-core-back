package com.cherrycore.company.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.company.domain.CompanyApplicationStatus;
import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_applications")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class CompanyApplicationJpaEntity extends AuditableJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompanyApplicationStatus status;

    protected CompanyApplicationJpaEntity() {
    }

    public CompanyApplicationJpaEntity(UUID id, UUID companyId, UUID applicationId) {
        super(id);
        this.companyId = companyId;
        this.applicationId = applicationId;
    }

    public UUID getCompanyId() { return companyId; }
    public UUID getApplicationId() { return applicationId; }
    public CompanyApplicationStatus getStatus() { return status; }
    public void setStatus(CompanyApplicationStatus status) { this.status = status; }
}
