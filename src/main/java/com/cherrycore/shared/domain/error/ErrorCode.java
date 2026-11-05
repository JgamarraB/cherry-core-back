package com.cherrycore.shared.domain.error;

/**
 * Contrato de todos los códigos de error.
 *
 * <p>Cada módulo define su propio {@code enum} que implementa esta interfaz
 * (p. ej. {@code CustomerErrorCode}). Así no hay un enum gigante y cada módulo
 * es dueño de sus errores.</p>
 *
 * <ul>
 *   <li>{@link #code()}: identificador estable. El frontend traduce a partir de él.
 *       Nunca se cambia una vez publicado.</li>
 *   <li>{@link #description()}: texto en inglés, orientado al desarrollador.</li>
 *   <li>{@link #category()}: determina el estado HTTP.</li>
 * </ul>
 */
public interface ErrorCode {

    String code();

    String description();

    ErrorCategory category();
}
