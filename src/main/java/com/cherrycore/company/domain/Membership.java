package com.cherrycore.company.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Pertenencia de una persona de backoffice a una empresa, con un rol.
 */
public class Membership {

    private final UUID id;
    private final UUID staffUserId;
    private final UUID companyId;
    private UUID roleId;
    private MembershipStatus status;

    public Membership(UUID id, UUID staffUserId, UUID companyId, UUID roleId, MembershipStatus status) {
        this.id = Objects.requireNonNull(id);
        this.staffUserId = Objects.requireNonNull(staffUserId);
        this.companyId = Objects.requireNonNull(companyId);
        this.roleId = Objects.requireNonNull(roleId);
        this.status = Objects.requireNonNull(status);
    }

    public void changeRole(UUID newRoleId) {
        roleId = Objects.requireNonNull(newRoleId);
    }

    public void suspend() {
        status = MembershipStatus.SUSPENDED;
    }

    public void reactivate() {
        status = MembershipStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public UUID staffUserId() {
        return staffUserId;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID roleId() {
        return roleId;
    }

    public MembershipStatus status() {
        return status;
    }
}
