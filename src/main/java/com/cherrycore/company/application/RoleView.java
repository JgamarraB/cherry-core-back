package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

/**
 * Rol con sus permisos. {@code allPermissions} = OWNER (los tiene todos).
 */
public record RoleView(
        UUID id,
        String code,
        String name,
        boolean system,
        boolean allPermissions,
        List<PermissionView> permissions
) {

    public record PermissionView(UUID id, String applicationCode, String code, String description) {
    }
}
