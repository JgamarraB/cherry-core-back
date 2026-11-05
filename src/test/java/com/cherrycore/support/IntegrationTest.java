package com.cherrycore.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test de integración: aplicación completa (perfil "test") + Postgres y S3 en
 * Docker + MockMvc + buzón de email falso.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestcontainersConfig.class, StorageTestcontainersConfig.class, TestEmailConfig.class})
public @interface IntegrationTest {
}
