package com.cherrycore.auth.infrastructure.clients;

import java.util.List;
import java.util.Set;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientAudience;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;

/**
 * Definición de una app cliente (desde configuración o desde el panel).
 *
 * @param machineAccess el backend de la app puede pedir un token de máquina
 *                      (client_credentials) para sincronizar sus permisos
 */
public record ClientDefinition(
        String clientId,
        String clientName,
        ClientType type,
        ClientAudience audience,
        String applicationCode,
        List<String> redirectUris,
        List<String> postLogoutRedirectUris,
        Set<String> scopes,
        boolean machineAccess
) {
}
