package com.cherrycore.shared.infrastructure.storage;

/**
 * Fallo técnico del almacenamiento. Acaba en un 500 genérico (el detalle solo en logs).
 */
public class StorageException extends RuntimeException {

    public StorageException(Throwable cause) {
        super(cause);
    }
}
