package com.cherrycore.auth.infrastructure.social;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Apple vuelve con un POST desde su web (response_mode=form_post). En una
 * petición POST entre sitios el navegador NO envía la cookie de sesión
 * (SameSite=Lax), así que se perdería la petición de autorización y el
 * login de la app que estaba esperando.
 *
 * <p>Solución: responder 303 hacia la misma URL por GET con los parámetros.
 * Una navegación GET sí lleva la cookie y Spring completa el login normalmente.
 * Solo se reenvían los parámetros OAuth necesarios.</p>
 */
class FormPostCallbackRedirectFilter extends OncePerRequestFilter {

    private static final String CALLBACK_PREFIX = "/login/oauth2/code/";
    private static final String QUERY_START = "?";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final List<String> FORWARDED_PARAMETERS = List.of("code", "state", "error", "error_description");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !request.getRequestURI().startsWith(request.getContextPath() + CALLBACK_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        StringJoiner query = new StringJoiner(PARAMETER_SEPARATOR, QUERY_START, "").setEmptyValue("");
        FORWARDED_PARAMETERS.forEach(name -> {
            String value = request.getParameter(name);
            if (value != null) {
                // Codificación de formulario: "+" -> %2B (si no, llegaría como espacio y fallaría "state").
                query.add(name + KEY_VALUE_SEPARATOR + URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        response.setStatus(HttpStatus.SEE_OTHER.value());
        response.setHeader(HttpHeaders.LOCATION, request.getRequestURI() + query);
    }
}
