package com.cherrycore.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Servidor S3 real (RustFS) en Docker para los tests: la misma imagen que en local.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StorageTestcontainersConfig {

    private static final String IMAGE = "rustfs/rustfs:1.0.3";
    private static final int API_PORT = 9000;
    private static final String ACCESS_KEY = "test-access";
    private static final String SECRET_KEY = "test-secret-key";

    @Bean
    GenericContainer<?> storageContainer() {
        GenericContainer<?> container = new GenericContainer<>(IMAGE)
                .withCommand("/data")
                .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
                .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
                .withExposedPorts(API_PORT)
                .waitingFor(Wait.forListeningPort());
        container.start();
        return container;
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(GenericContainer<?> storageContainer) {
        return registry -> {
            String url = "http://" + storageContainer.getHost() + ":" + storageContainer.getMappedPort(API_PORT);
            registry.add("cherrycore.storage.endpoint", () -> url);
            registry.add("cherrycore.storage.public-base-url", () -> url);
            registry.add("cherrycore.storage.access-key", () -> ACCESS_KEY);
            registry.add("cherrycore.storage.secret-key", () -> SECRET_KEY);
        };
    }
}
