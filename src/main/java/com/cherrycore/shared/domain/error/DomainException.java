package com.cherrycore.shared.domain.error;

import java.util.Objects;

/**
 * Excepción base de todos los errores controlados de la aplicación.
 *
 * <p>Los casos de uso lanzan {@code new DomainException(CustomerErrorCode.X)}
 * (o una subclase) y el {@code GlobalExceptionHandler} la convierte en la
 * respuesta KO con su estado HTTP.</p>
 */
public class DomainException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public DomainException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).description());
        this.errorCode = errorCode;
    }

    public DomainException(ErrorCode errorCode, Throwable cause) {
        super(Objects.requireNonNull(errorCode).description(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
