package com.cherrycore.auth.infrastructure;

/**
 * Ajustes propios guardados en cada cliente registrado.
 */
public final class ClientSettingNames {

    /** Tipo de cuenta que puede iniciar sesión en el cliente (CUSTOMER / STAFF). */
    public static final String AUDIENCE = "settings.client.cherrycore.audience";

    /** Código de la app del catálogo (sus permisos van en el token de backoffice). */
    public static final String APPLICATION = "settings.client.cherrycore.application";

    /** Puede pedir tokens de máquina (client_credentials) para sincronizar permisos. */
    public static final String MACHINE_ACCESS = "settings.client.cherrycore.machine-access";

    private ClientSettingNames() {
    }
}
