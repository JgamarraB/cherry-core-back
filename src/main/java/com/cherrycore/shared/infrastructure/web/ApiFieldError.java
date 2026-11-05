package com.cherrycore.shared.infrastructure.web;

/**
 * Error de un campo concreto.
 *
 * <p>{@code code} es el tipo de validación que falló ({@code NOT_BLANK},
 * {@code SIZE}, {@code EMAIL}...). Nunca se devuelve el valor enviado,
 * para no reflejar datos sensibles como contraseñas.</p>
 */
public record ApiFieldError(String field, String code) {
}
