package com.cherrycore.identity.infrastructure.notification;

import java.util.Locale;

/**
 * Evento interno: hay que enviar un email. Se procesa al confirmar la transacción.
 *
 * @param link enlace con token, o {@code null} si el email no lleva enlace
 */
record IdentityEmailRequested(IdentityEmailType type, String to, Locale locale, String link) {
}
