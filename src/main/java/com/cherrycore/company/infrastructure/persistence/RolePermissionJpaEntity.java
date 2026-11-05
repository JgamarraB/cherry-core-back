package com.cherrycore.company.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_role_permissions")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class RolePermissionJpaEntity extends AuditableJpaEntity {

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    protected RolePermissionJpaEntity() {
    }

    public RolePermissionJpaEntity(UUID id, UUID roleId, UUID permissionId) {
        super(id);
        this.roleId = roleId;
        this.permissionId = permissionId;
    }

    public UUID getRoleId() { return roleId; }
    public UUID getPermissionId() { return permissionId; }
}
