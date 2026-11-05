package com.cherrycore.shared.infrastructure.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.infrastructure.web.ApiErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Petición sin autenticar → 401 con el formato KO.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public JsonAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        errorWriter.write(response, CommonErrorCode.AUTHENTICATION_REQUIRED);
    }
}
