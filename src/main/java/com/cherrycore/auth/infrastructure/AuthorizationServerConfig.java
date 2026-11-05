package com.cherrycore.auth.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.security.JwtAccountAuthenticationConverter;
import com.cherrycore.shared.infrastructure.security.SecurityHeadersCustomizer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Spring Authorization Server (OAuth2 + OpenID Connect).
 *
 * <p>Endpoints estándar: {@code /oauth2/authorize}, {@code /oauth2/token},
 * {@code /oauth2/jwks}, {@code /oauth2/revoke}, {@code /userinfo},
 * {@code /connect/logout} y {@code /.well-known/openid-configuration}.</p>
 *
 * <p>Si alguien llega a {@code /oauth2/authorize} sin sesión, se le redirige al
 * frontend de login; tras el login, el frontend vuelve a la URL guardada.</p>
 */
@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, CherryCoreProperties properties,
            JwtDecoder jwtDecoder, SecurityHeadersCustomizer securityHeaders, RegisteredClientRepository clients)
            throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // /userinfo se llama con el access token.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(new JwtAccountAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new AppAwareLoginEntryPoint(properties.frontend().loginUrl(), clients),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .cors(Customizer.withDefaults())
                .headers(securityHeaders::apply);
        return http.build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(CherryCoreProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer().toString()).build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbcOperations,
            RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, clients);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbcOperations,
            RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, clients);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeyProvider signingKeys) {
        return new ImmutableJWKSet<>(new JWKSet(signingKeys.rsaKey()));
    }

    /**
     * Valida los tokens que el propio CherryCore recibe en su API:
     * firma, caducidad e emisor.
     */
    @Bean
    JwtDecoder jwtDecoder(SigningKeyProvider signingKeys, CherryCoreProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKeys.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer().toString()));
        return decoder;
    }
}
