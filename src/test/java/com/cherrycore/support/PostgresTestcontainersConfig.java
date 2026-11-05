package com.cherrycore.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real en Docker para los tests (misma versión que en local y producción).
 * Spring conecta el datasource automáticamente gracias a {@link ServiceConnection}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    private static final String POSTGRES_IMAGE = "postgres:17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
