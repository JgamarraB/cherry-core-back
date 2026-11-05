package com.cherrycore.company.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.cherrycore.shared.domain.error.DomainException;

/**
 * Rol de una empresa con sus permisos (ids del catálogo de permisos).
 */
public class CompanyRole {

    public static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final UUID companyId;
    private final RoleCode code;
    private String name;
    private final boolean system;
    private Set<UUID> permissionIds;

    public CompanyRole(UUID id, UUID companyId, RoleCode code, String name, boolean system, Set<UUID> permissionIds) {
        this.id = Objects.requireNonNull(id);
        this.companyId = Objects.requireNonNull(companyId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.system = system;
        this.permissionIds = Set.copyOf(permissionIds);
    }

    public static CompanyRole system(UUID id, UUID companyId, SystemRole role, Set<UUID> permissionIds) {
        return new CompanyRole(id, companyId, role.code(), role.defaultName(), true,
                role == SystemRole.OWNER ? Set.of() : permissionIds);
    }

    public void rename(String newName) {
        if (system) {
            throw new DomainException(CompanyErrorCode.SYSTEM_ROLE_IMMUTABLE);
        }
        name = Objects.requireNonNull(newName);
    }

    public void replacePermissions(Set<UUID> newPermissionIds) {
        if (isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_PERMISSIONS_FIXED);
        }
        permissionIds = Set.copyOf(newPermissionIds);
    }

    public void ensureDeletable() {
        if (system) {
            throw new DomainException(CompanyErrorCode.SYSTEM_ROLE_IMMUTABLE);
        }
    }

    public boolean isOwner() {
        return code.isOwner();
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public RoleCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public boolean isSystem() {
        return system;
    }

    public Set<UUID> permissionIds() {
        return permissionIds;
    }
}
