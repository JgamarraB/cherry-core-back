package com.cherrycore.shared.infrastructure.mail;

import java.util.Locale;

/**
 * Contenido de un email antes de darle formato.
 *
 * @param body        párrafos separados por una línea en blanco
 * @param actionUrl   enlace del botón principal (o {@code null})
 * @param actionLabel texto del botón (o {@code null})
 */
public record EmailContent(
        String to,
        Locale locale,
        EmailBranding branding,
        String subject,
        String body,
        String actionUrl,
        String actionLabel
) {
}
