package com.cherrycore.shared.infrastructure.storage;

import java.io.ByteArrayInputStream;

import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.shared.application.port.FileStorage;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

/**
 * Ficheros en un servidor compatible con S3 (RustFS, MinIO, AWS S3...).
 * Se usa el cliente Java de MinIO, que habla el protocolo S3 estándar.
 * Estructura de claves:
 * {@code companies/{companyId}/public/...} (públicos, p. ej. logos).
 */
public class S3FileStorage implements FileStorage {

    /* -1 = MinIO calcula el tamaño de las partes al subir. */
    private static final long AUTO_PART_SIZE = -1;
    private static final String PATH_SEPARATOR = "/";

    private final MinioClient client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(MinioClient client, CherryCoreProperties.Storage settings) {
        this.client = client;
        this.bucket = settings.bucket();
        this.publicBaseUrl = settings.publicBaseUrl().toString();
    }

    @Override
    public void storePublic(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, AUTO_PART_SIZE)
                    .contentType(contentType)
                    .build());
        } catch (Exception storageFailure) {
            throw new StorageException(storageFailure);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception storageFailure) {
            throw new StorageException(storageFailure);
        }
    }

    @Override
    public String publicUrl(String key) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
                .path(PATH_SEPARATOR + bucket + PATH_SEPARATOR + key)
                .build()
                .toUriString();
    }
}
