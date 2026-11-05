package com.cherrycore.identity.infrastructure.security;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hash de contraseñas con Argon2id (recomendación actual de OWASP).
 *
 * <p>Se usa un {@link DelegatingPasswordEncoder}: cada hash guarda el
 * algoritmo con el que se hizo ({@code {argon2}...}), así podremos cambiar de
 * algoritmo en el futuro sin invalidar las contraseñas existentes.
 * Lo usa también Spring Authorization Server para los secretos de los clientes.</p>
 */
@Configuration
public class PasswordEncoderConfig {

    private static final String ARGON2_ID = "argon2";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(ARGON2_ID,
                Map.of(ARGON2_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }
}
