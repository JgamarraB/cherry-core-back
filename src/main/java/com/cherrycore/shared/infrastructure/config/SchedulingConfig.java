package com.cherrycore.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas (limpieza de datos caducados).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
