package com.cherrycore.auth.infrastructure.clients;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import com.cherrycore.auth.infrastructure.ClientSettingNames;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientAudience;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;

/**
 * Construye y valida los clientes OAuth con las reglas de seguridad de
 * CherryCore: PKCE obligatorio, rotación de refresh tokens, sin consentimiento
 * (apps propias) y redirect URIs exactas.
 */
@Component
public class RegisteredClientFactory {

    /** Scope del token de máquina para sincronizar el catálogo de permisos. */
    public static final String CATALOG_SYNC_SCOPE = "catalog.sync";
    public static final Set<String> ALLOWED_USER_SCOPES = Set.of(OidcScopes.OPENID, OidcScopes.PROFILE,
            OidcScopes.EMAIL);

    private static final String CLIENT_ID_NAMESPACE = "cherrycore-client:";
    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,99}$");
    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1");
    private static final String WILDCARD = "*";

    private final CherryCoreProperties properties;

    public RegisteredClientFactory(CherryCoreProperties properties) {
        this.properties = properties;
    }

    public static String stableId(String clientId) {
        return UUID.nameUUIDFromBytes((CLIENT_ID_NAMESPACE + clientId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public void validate(ClientDefinition definition) {
        if (definition.clientId() == null || !CLIENT_ID.matcher(definition.clientId()).matches()) {
            throw new DomainException(ClientErrorCode.INVALID_CLIENT_ID);
        }
        definition.redirectUris().forEach(RegisteredClientFactory::validateUri);
        definition.postLogoutRedirectUris().forEach(RegisteredClientFactory::validateUri);
        if (!ALLOWED_USER_SCOPES.containsAll(definition.scopes())) {
            throw new DomainException(ClientErrorCode.INVALID_SCOPE);
        }
        /*
         * La app "cherrycore" es la propia gestión de empresas: un cliente
         * enlazado a ella recibiría esos permisos en sus tokens y, con acceso
         * de máquina, podría retirarlos del catálogo (y romper la gestión de
         * todas las empresas).
         */
        if (CompanyPermission.APPLICATION_CODE.equals(definition.applicationCode())) {
            throw new DomainException(ClientErrorCode.RESERVED_APPLICATION);
        }
        boolean needsApplication = definition.audience() == ClientAudience.STAFF || definition.machineAccess();
        if (needsApplication && (definition.applicationCode() == null || definition.applicationCode().isBlank())) {
            throw new DomainException(ClientErrorCode.APPLICATION_REQUIRED);
        }
        if (definition.machineAccess() && definition.type() != ClientType.CONFIDENTIAL) {
            throw new DomainException(ClientErrorCode.MACHINE_ACCESS_REQUIRES_CONFIDENTIAL);
        }
    }

    /**
     * @param encodedSecret secreto ya cifrado con el PasswordEncoder (o {@code null} si es público)
     */
    public RegisteredClient build(ClientDefinition definition, String encodedSecret) {
        RegisteredClient.Builder builder = RegisteredClient.withId(stableId(definition.clientId()))
                .clientId(definition.clientId())
                .clientName(definition.clientName())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUris(uris -> uris.addAll(definition.redirectUris()))
                .postLogoutRedirectUris(uris -> uris.addAll(definition.postLogoutRedirectUris()))
                .scopes(scopes -> scopes.addAll(definition.scopes()))
                .clientSettings(clientSettings(definition))
                .tokenSettings(tokenSettings(definition.audience()));

        if (definition.type() == ClientType.CONFIDENTIAL) {
            builder.clientSecret(encodedSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            if (definition.machineAccess()) {
                builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope(CATALOG_SYNC_SCOPE);
            }
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        return builder.build();
    }

    private static ClientSettings clientSettings(ClientDefinition definition) {
        ClientSettings.Builder settings = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .setting(ClientSettingNames.AUDIENCE, definition.audience().name())
                .setting(ClientSettingNames.MACHINE_ACCESS, definition.machineAccess());
        if (definition.applicationCode() != null && !definition.applicationCode().isBlank()) {
            settings.setting(ClientSettingNames.APPLICATION, definition.applicationCode());
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

    private static void validateUri(String raw) {
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean secure = HTTPS.equals(scheme) || (HTTP.equals(scheme) && LOCAL_HOSTS.contains(uri.getHost()));
            if (!uri.isAbsolute() || !secure || uri.getFragment() != null || raw.contains(WILDCARD)) {
                throw new DomainException(ClientErrorCode.INVALID_REDIRECT_URI);
            }
        } catch (IllegalArgumentException malformed) {
            throw new DomainException(ClientErrorCode.INVALID_REDIRECT_URI);
        }
    }
}
