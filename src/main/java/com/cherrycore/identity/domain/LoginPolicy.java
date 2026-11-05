package com.cherrycore.identity.domain;

import java.time.Duration;

/**
 * Cuántos intentos fallidos se permiten y cuánto dura el bloqueo temporal.
 */
public record LoginPolicy(int maxFailedAttempts, Duration lockDuration) {
}
