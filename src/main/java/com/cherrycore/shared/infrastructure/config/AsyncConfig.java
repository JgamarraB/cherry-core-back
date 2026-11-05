package com.cherrycore.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Permite tareas en segundo plano (@Async), p. ej. el envío de emails.
 * Con hilos virtuales activados, cada tarea usa un hilo virtual.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
