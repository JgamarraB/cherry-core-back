package com.cherrycore.shared.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación. Se inyecta en vez de llamar a {@code Instant.now()}
 * para poder controlar el tiempo en los tests (caducidades, bloqueos...).
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
