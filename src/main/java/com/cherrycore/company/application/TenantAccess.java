package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

/**
 * Lo que va en el token de una app para la empresa elegida.
 *
 * @param permissions códigos de permiso de esa app (OWNER: todos)
 */
public record TenantAccess(UUID companyId, String companyName, String roleCode, List<String> permissions) {
}
