package com.cherrycore.shared.infrastructure.mail;

import java.util.List;
import java.util.Locale;

/**
 * Evento: hay que enviar un email. Lo procesa {@link EmailDispatcher} cuando
 * la transacción se confirma (si la operación falla, no se envía nada).
 *
 * @param templateKey prefijo de los textos en i18n/emails*.properties
 *                    ({@code key.subject}, {@code key.body}, {@code key.action})
 * @param branding    marca de la empresa, o {@code null} para la de la plataforma
 * @param actionUrl   enlace del botón, o {@code null}
 * @param bodyArgs    valores para los huecos {0}, {1}... del cuerpo
 */
public record EmailRequested(
        String templateKey,
        String to,
        Locale locale,
        EmailBranding branding,
        String actionUrl,
        List<Object> bodyArgs
) {
}
