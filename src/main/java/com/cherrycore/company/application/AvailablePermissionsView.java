package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

/**
 * Permisos que la empresa puede asignar a sus roles, agrupados por app
 * (solo apps activadas para la empresa).
 */
public record AvailablePermissionsView(UUID applicationId, String applicationCode, String applicationName,
        List<RoleView.PermissionView> permissions) {
}
