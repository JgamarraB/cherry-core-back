package com.cherrycore.shared.infrastructure.web;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.domain.error.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe una respuesta KO directamente en el {@link HttpServletResponse}.
 *
 * <p>Se usa en los filtros de seguridad, que se ejecutan antes de llegar a
 * los controllers y por tanto fuera del {@code GlobalExceptionHandler}.</p>
 */
@Component
public class ApiErrorWriter {

    private final JsonMapper jsonMapper;

    public ApiErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatusResolver.resolve(errorCode).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(errorCode));
    }
}
