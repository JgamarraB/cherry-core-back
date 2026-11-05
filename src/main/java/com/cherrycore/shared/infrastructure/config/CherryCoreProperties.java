package com.cherrycore.shared.infrastructure.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Toda la configuración propia de CherryCore, en un único sitio.
 *
 * <p>Los valores se definen en {@code application.yaml} bajo {@code cherrycore:}.
 * Si falta alguno obligatorio, la aplicación no arranca.</p>
 */
@Validated
@ConfigurationProperties(prefix = CherryCoreProperties.PREFIX)
public record CherryCoreProperties(
        @NotNull @Valid Security security,
        @NotNull @Valid Cors cors
) {

    public static final String PREFIX = "cherrycore";

    public record Security(@NotNull @Valid Tokens tokens) {
    }

    /**
     * Duración de los tokens emitidos.
     */
    public record Tokens(
            @NotNull Duration accessTokenTtl,
            @NotNull Duration customerRefreshTokenTtl,
            @NotNull Duration staffRefreshTokenTtl
    ) {
    }

    /**
     * Orígenes (frontends) que pueden llamar a la API desde el navegador.
     * Nunca se usa {@code *}.
     */
    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotNull Duration maxAge
    ) {
    }
}
