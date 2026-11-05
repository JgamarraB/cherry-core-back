package com.cherrycore.shared.application.port;

/**
 * Almacenamiento de ficheros en un servidor compatible con S3 (RustFS, MinIO,
 * AWS S3...). Cambiar de proveedor es cambiar la configuración.
 */
public interface FileStorage {

    /**
     * Guarda un fichero público (accesible por URL sin autenticación).
     */
    void storePublic(String key, byte[] content, String contentType);

    void delete(String key);

    String publicUrl(String key);
}
