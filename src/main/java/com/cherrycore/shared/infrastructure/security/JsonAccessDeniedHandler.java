package com.cherrycore.shared.infrastructure.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.infrastructure.web.ApiErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticado pero sin permiso → 403 con el formato KO.
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
        errorWriter.write(response, CommonErrorCode.ACCESS_DENIED);
    }
}
