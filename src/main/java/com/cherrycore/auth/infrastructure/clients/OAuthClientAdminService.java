package com.cherrycore.auth.infrastructure.clients;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.auth.infrastructure.ClientSettingNames;
import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.CatalogErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientAudience;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;

/**
 * Alta y mantenimiento de las apps cliente desde el panel de superadmin.
 *
 * <p>El secreto se genera aquí (256 bits aleatorios), se devuelve UNA vez y
 * solo se guarda su hash. Si se pierde, se rota.</p>
 */
@Service
public class OAuthClientAdminService {

    private static final int SECRET_BYTES = 32;
    private static final String LIST_IDS_SQL = "SELECT id FROM oauth2_registered_client ORDER BY client_name";
    private static final String DELETE_AUTHORIZATIONS_SQL =
            "DELETE FROM oauth2_authorization WHERE registered_client_id = :id";
    private static final String DELETE_CONSENTS_SQL =
            "DELETE FROM oauth2_authorization_consent WHERE registered_client_id = :id";
    private static final String DELETE_CLIENT_SQL = "DELETE FROM oauth2_registered_client WHERE id = :id";

    private final RegisteredClientRepository repository;
    private final RegisteredClientFactory factory;
    private final PasswordEncoder passwordEncoder;
    private final CatalogService catalog;
    private final JdbcClient jdbc;
    private final SecureRandom random = new SecureRandom();

    public OAuthClientAdminService(RegisteredClientRepository repository, RegisteredClientFactory factory,
            PasswordEncoder passwordEncoder, CatalogService catalog, JdbcClient jdbc) {
        this.repository = repository;
        this.factory = factory;
        this.passwordEncoder = passwordEncoder;
        this.catalog = catalog;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<OAuthClientView> list() {
        return jdbc.sql(LIST_IDS_SQL).query(String.class).list().stream()
                .map(repository::findById)
                .map(OAuthClientAdminService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OAuthClientView get(String clientId) {
        return toView(find(clientId));
    }

    @Transactional
    public CreatedClient create(ClientDefinition definition) {
        validate(definition);
        if (repository.findByClientId(definition.clientId()) != null) {
            throw new DomainException(ClientErrorCode.CLIENT_ID_TAKEN);
        }
        String secret = definition.type() == ClientType.CONFIDENTIAL ? newSecret() : null;
        RegisteredClient client = factory.build(definition, secret == null ? null : passwordEncoder.encode(secret));
        repository.save(client);
        return new CreatedClient(toView(client), secret);
    }

    /**
     * Actualiza nombre, URLs, scopes, app y acceso de máquina. El tipo
     * (público/confidencial) y el secreto no cambian aquí.
     */
    @Transactional
    public OAuthClientView update(String clientId, ClientDefinition changes) {
        RegisteredClient current = find(clientId);
        ClientType type = isConfidential(current) ? ClientType.CONFIDENTIAL : ClientType.PUBLIC;
        ClientDefinition definition = new ClientDefinition(clientId, changes.clientName(), type, changes.audience(),
                changes.applicationCode(), changes.redirectUris(), changes.postLogoutRedirectUris(),
                changes.scopes(), changes.machineAccess());
        validate(definition);
        RegisteredClient updated = factory.build(definition, current.getClientSecret());
        repository.save(updated);
        return toView(updated);
    }

    @Transactional
    public CreatedClient rotateSecret(String clientId) {
        RegisteredClient current = find(clientId);
        if (!isConfidential(current)) {
            throw new DomainException(ClientErrorCode.SECRET_ONLY_FOR_CONFIDENTIAL);
        }
        String secret = newSecret();
        RegisteredClient rotated = RegisteredClient.from(current).clientSecret(passwordEncoder.encode(secret)).build();
        repository.save(rotated);
        return new CreatedClient(toView(rotated), secret);
    }

    /**
     * Borra el cliente y todos los tokens que tenía emitidos.
     */
    @Transactional
    public void delete(String clientId) {
        String id = find(clientId).getId();
        jdbc.sql(DELETE_AUTHORIZATIONS_SQL).param("id", id).update();
        jdbc.sql(DELETE_CONSENTS_SQL).param("id", id).update();
        jdbc.sql(DELETE_CLIENT_SQL).param("id", id).update();
    }

    private void validate(ClientDefinition definition) {
        factory.validate(definition);
        if (definition.applicationCode() != null && !definition.applicationCode().isBlank()
                && catalog.findByCode(definition.applicationCode()).isEmpty()) {
            throw new DomainException(CatalogErrorCode.APPLICATION_NOT_FOUND);
        }
    }

    private RegisteredClient find(String clientId) {
        RegisteredClient client = repository.findByClientId(clientId);
        if (client == null) {
            throw new DomainException(ClientErrorCode.CLIENT_NOT_FOUND);
        }
        return client;
    }

    private String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean isConfidential(RegisteredClient client) {
        return !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE);
    }

    private static OAuthClientView toView(RegisteredClient client) {
        Object audience = client.getClientSettings().getSetting(ClientSettingNames.AUDIENCE);
        Object machine = client.getClientSettings().getSetting(ClientSettingNames.MACHINE_ACCESS);
        Set<String> userScopes = new TreeSet<>(client.getScopes());
        userScopes.remove(RegisteredClientFactory.CATALOG_SYNC_SCOPE);
        return new OAuthClientView(client.getClientId(), client.getClientName(),
                isConfidential(client) ? ClientType.CONFIDENTIAL : ClientType.PUBLIC,
                audience == null ? null : ClientAudience.valueOf(audience.toString()),
                client.getClientSettings().getSetting(ClientSettingNames.APPLICATION),
                new ArrayList<>(client.getRedirectUris()), new ArrayList<>(client.getPostLogoutRedirectUris()),
                userScopes, Boolean.TRUE.equals(machine)
                        && client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS),
                client.getClientIdIssuedAt());
    }

    public record OAuthClientView(
            String clientId,
            String name,
            ClientType type,
            ClientAudience audience,
            String applicationCode,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            Set<String> scopes,
            boolean machineAccess,
            Instant createdAt
    ) {
    }

    /**
     * @param clientSecret en claro, solo en la respuesta de alta o rotación
     */
    public record CreatedClient(OAuthClientView client, String clientSecret) {
    }
}
