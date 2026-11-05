package com.cherrycore.identity.infrastructure.web;

import com.cherrycore.identity.domain.EmailAddress;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Peticiones y respuestas de la API de autenticación.
 *
 * <p>Las validaciones de aquí solo limitan tamaños y obligatoriedad (protección
 * básica). Las reglas de negocio (formato del email, política de contraseña)
 * están en el dominio.</p>
 */
public final class AuthDtos {

    /** Margen generoso: la longitud real la controla PasswordPolicy. */
    public static final int MAX_PASSWORD_INPUT = 512;
    public static final int MAX_TOKEN_INPUT = 256;

    private AuthDtos() {
    }

    /**
     * Dónde leer el token CSRF (cookie) y en qué cabecera enviarlo.
     */
    public record CsrfResponse(String cookieName, String headerName) {
    }

    public record EmailRequest(@NotBlank @Size(max = EmailAddress.MAX_LENGTH) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = MAX_TOKEN_INPUT) String token) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = MAX_TOKEN_INPUT) String token,
            @NotBlank @Size(max = MAX_PASSWORD_INPUT) String newPassword
    ) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[***]";
        }
    }
}
