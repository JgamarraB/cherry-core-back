package com.cherrycore.shared.infrastructure.mail;

/**
 * Marca que aparece en el email: la de la plataforma o la de una empresa.
 *
 * @param logoUrl {@code null} si no hay logo (se muestra el nombre)
 */
public record EmailBranding(String name, String logoUrl) {
}
