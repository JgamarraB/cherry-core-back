package com.cherrycore.shared.infrastructure.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.stereotype.Component;

/**
 * Cabeceras de seguridad comunes a todas las cadenas de seguridad.
 */
@Component
public class SecurityHeadersCustomizer {

    public void apply(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(SecurityHeaders.CONTENT_SECURITY_POLICY))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(permissions -> permissions.policy(SecurityHeaders.PERMISSIONS_POLICY));
    }
}
