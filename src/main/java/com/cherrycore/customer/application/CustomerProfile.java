package com.cherrycore.customer.application;

import java.util.UUID;

/**
 * Vista del perfil del cliente: datos de la cuenta + perfil.
 */
public record CustomerProfile(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String locale
) {
}
