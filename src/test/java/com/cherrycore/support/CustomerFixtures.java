package com.cherrycore.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.cherrycore.identity.infrastructure.web.AuthPaths;

/**
 * Atajos para crear clientes ya verificados en los tests.
 */
public final class CustomerFixtures {

    public static final String PASSWORD = "Una-clave-larga-2026";
    public static final String VERIFICATION_SUBJECT_ES = "Confirma tu email";
    public static final String RESET_SUBJECT_ES = "Restablece tu contraseña";
    public static final String LOCKED_SUBJECT_ES = "Tu cuenta se ha bloqueado temporalmente";
    public static final String EXISTING_SUBJECT_ES = "Intento de registro con tu email";
    public static final String PASSWORD_CHANGED_SUBJECT_ES = "Tu contraseña se ha cambiado";

    private CustomerFixtures() {
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    public static String registerBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s", "firstName": "Ana", "lastName": "García",
                 "phone": "+34 600 11 12 22", "acceptTerms": true}
                """.formatted(email, password);
    }

    public static void register(BrowserSession browser, String email) throws Exception {
        int status = browser.postJson(AuthPaths.CUSTOMER_REGISTER, registerBody(email, PASSWORD))
                .getResponse().getStatus();
        assertThat(status).isEqualTo(HttpStatus.ACCEPTED.value());
    }

    /**
     * Registra, verifica el email con el enlace recibido y devuelve el email.
     */
    public static String registerVerified(BrowserSession browser, CapturingEmailSender mailbox) throws Exception {
        String email = uniqueEmail();
        register(browser, email);
        String token = mailbox.awaitToken(email, VERIFICATION_SUBJECT_ES);
        int status = browser.postJson(AuthPaths.CONFIRM_EMAIL, "{\"token\": \"" + token + "\"}")
                .getResponse().getStatus();
        assertThat(status).isEqualTo(HttpStatus.OK.value());
        return email;
    }
}
