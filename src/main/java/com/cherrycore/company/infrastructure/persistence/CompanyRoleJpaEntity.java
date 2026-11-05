package com.cherrycore.company.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_roles")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class CompanyRoleJpaEntity extends AuditableJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_system", nullable = false, updatable = false)
    private boolean system;

    protected CompanyRoleJpaEntity() {
    }

    public CompanyRoleJpaEntity(UUID id, UUID companyId, String code, boolean system) {
        super(id);
        this.companyId = companyId;
        this.code = code;
        this.system = system;
    }

    public UUID getCompanyId() { return companyId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isSystem() { return system; }
}
