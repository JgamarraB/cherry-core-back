package com.cherrycore.auth.infrastructure;

/**
 * Ajustes propios guardados en cada cliente registrado.
 */
public final class ClientSettingNames {

    /** Tipo de cuenta que puede iniciar sesión en el cliente (CUSTOMER / STAFF). */
    public static final String AUDIENCE = "settings.client.cherrycore.audience";

    private ClientSettingNames() {
    }
}
