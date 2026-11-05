package com.cherrycore.shared.infrastructure.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.infrastructure.web.ApiErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticado pero sin permiso → 403 con el formato KO.
 * Si el motivo es el token CSRF, se indica con un código propio.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    public JsonAccessDeniedHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        errorWriter.write(response, exception instanceof CsrfException
                ? CommonErrorCode.CSRF_TOKEN_INVALID
                : CommonErrorCode.ACCESS_DENIED);
    }
}
