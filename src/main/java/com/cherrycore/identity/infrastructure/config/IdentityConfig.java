package com.cherrycore.identity.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cherrycore.identity.application.IdentitySettings;
import com.cherrycore.identity.domain.LoginPolicy;
import com.cherrycore.identity.domain.PasswordPolicy;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * Traduce la configuración central a los parámetros del módulo de identidad.
 */
@Configuration
public class IdentityConfig {

    @Bean
    IdentitySettings identitySettings(CherryCoreProperties properties) {
        CherryCoreProperties.Security security = properties.security();
        return new IdentitySettings(
                new PasswordPolicy(security.password().minLength(), security.password().maxLength()),
                new LoginPolicy(security.loginProtection().maxFailedAttempts(),
                        security.loginProtection().lockDuration()),
                security.oneTimeTokens().emailVerificationTtl(),
                security.oneTimeTokens().passwordResetTtl(),
                properties.i18n().defaultLocale(),
                properties.i18n().supportedLocales(),
                properties.branding().platformName());
    }
}
