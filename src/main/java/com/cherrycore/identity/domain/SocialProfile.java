package com.cherrycore.identity.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * Datos que devuelve el proveedor social tras el login.
 *
 * @param subject       id del usuario en el proveedor (estable)
 * @param emailVerified el proveedor garantiza que el email es de esa persona
 */
public record SocialProfile(
        SocialProvider provider,
        String subject,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
