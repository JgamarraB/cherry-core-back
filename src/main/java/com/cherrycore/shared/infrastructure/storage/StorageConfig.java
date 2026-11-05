package com.cherrycore.shared.infrastructure.storage;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cherrycore.shared.application.port.FileStorage;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;

@Configuration
public class StorageConfig {

    /*
     * Solo es público lo que está bajo companies/{id}/public/. El resto del
     * bucket es privado (para ficheros privados se usarán URLs firmadas).
     */
    private static final String PUBLIC_READ_POLICY = """
            {
              "Version": "2012-10-17",
              "Statement": [{
                "Effect": "Allow",
                "Principal": {"AWS": ["*"]},
                "Action": ["s3:GetObject"],
                "Resource": ["arn:aws:s3:::%s/companies/*/public/*"]
              }]
            }
            """;

    @Bean
    MinioClient minioClient(CherryCoreProperties properties) {
        CherryCoreProperties.Storage storage = properties.storage();
        return MinioClient.builder()
                .endpoint(storage.endpoint().toString())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
    }

    @Bean
    FileStorage fileStorage(MinioClient client, CherryCoreProperties properties) {
        return new S3FileStorage(client, properties.storage());
    }

    /**
     * Crea el bucket y su política al arrancar si no existen.
     */
    @Bean
    ApplicationRunner storageInitializer(MinioClient client, CherryCoreProperties properties) {
        String bucket = properties.storage().bucket();
        return args -> {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(PUBLIC_READ_POLICY.formatted(bucket))
                    .build());
        };
    }
}
