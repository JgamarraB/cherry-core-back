package com.cherrycore.auth.infrastructure;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.Client;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientAudience;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;

/**
 * Da de alta (o actualiza) en base de datos los clientes definidos en
 * {@code cherrycore.oauth.clients} al arrancar. La configuración es la fuente
 * de verdad: así cada entorno tiene sus clientes y sus URLs.
 */
@Component
class RegisteredClientsInitializer implements ApplicationRunner {

    private static final String CLIENT_ID_NAMESPACE = "cherrycore-client:";

    private final RegisteredClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final CherryCoreProperties properties;

    RegisteredClientsInitializer(RegisteredClientRepository repository, PasswordEncoder passwordEncoder,
            CherryCoreProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.oauth().clients().forEach(client -> repository.save(toRegisteredClient(client)));
    }

    private RegisteredClient toRegisteredClient(Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(stableId(client.clientId()))
                .clientId(client.clientId())
                .clientName(client.clientName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(toStrings(client.redirectUris())))
                .postLogoutRedirectUris(uris -> uris.addAll(toStrings(client.postLogoutRedirectUris())))
                .scopes(scopes -> scopes.addAll(client.scopes()))
                .clientSettings(clientSettings(client))
                .tokenSettings(tokenSettings(client.audience()));

        if (client.type() == ClientType.CONFIDENTIAL) {
            builder.clientSecret(passwordEncoder.encode(client.clientSecret()))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return builder.build();
    }

    private static ClientSettings clientSettings(Client client) {
        ClientSettings.Builder settings = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .setting(ClientSettingNames.AUDIENCE, client.audience().name());
        if (client.applicationCode() != null) {
            settings.setting(ClientSettingNames.APPLICATION, client.applicationCode());
        }
        return settings.build();
    }

    private TokenSettings tokenSettings(ClientAudience audience) {
        CherryCoreProperties.Tokens tokens = properties.security().tokens();
        return TokenSettings.builder()
                .accessTokenTimeToLive(tokens.accessTokenTtl())
                .refreshTokenTimeToLive(audience == ClientAudience.CUSTOMER
                        ? tokens.customerRefreshTokenTtl()
                        : tokens.staffRefreshTokenTtl())
                .authorizationCodeTimeToLive(tokens.authorizationCodeTtl())
                // Rotación: cada refresh devuelve un refresh token nuevo y el anterior deja de valer.
                .reuseRefreshTokens(false)
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    /* Id fijo por client-id: al reiniciar se actualiza el mismo registro. */
    private static String stableId(String clientId) {
        return UUID.nameUUIDFromBytes((CLIENT_ID_NAMESPACE + clientId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<String> toStrings(List<URI> uris) {
        return uris == null ? List.of() : uris.stream().map(URI::toString).toList();
    }
}
