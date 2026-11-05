package com.cherrycore.company.infrastructure.persistence;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.cherrycore.company.domain.MembershipStatus;
import com.cherrycore.shared.infrastructure.persistence.AuditableJpaEntity;
import com.cherrycore.shared.infrastructure.persistence.SoftDelete;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "memberships")
@SQLRestriction(SoftDelete.NOT_DELETED)
public class MembershipJpaEntity extends AuditableJpaEntity {

    @Column(name = "staff_user_id", nullable = false, updatable = false)
    private UUID staffUserId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;

    protected MembershipJpaEntity() {
    }

    public MembershipJpaEntity(UUID id, UUID staffUserId, UUID companyId) {
        super(id);
        this.staffUserId = staffUserId;
        this.companyId = companyId;
    }

    public UUID getStaffUserId() { return staffUserId; }
    public UUID getCompanyId() { return companyId; }
    public UUID getRoleId() { return roleId; }
    public void setRoleId(UUID roleId) { this.roleId = roleId; }
    public MembershipStatus getStatus() { return status; }
    public void setStatus(MembershipStatus status) { this.status = status; }
}
