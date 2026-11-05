package com.cherrycore.auth.infrastructure;

import java.net.URI;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

import com.cherrycore.auth.infrastructure.clients.ClientDefinition;
import com.cherrycore.auth.infrastructure.clients.RegisteredClientFactory;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.Client;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;

/**
 * Da de alta (o actualiza) al arrancar los clientes definidos en
 * {@code cherrycore.oauth.clients}. Pensado para desarrollo y tests: en
 * producción los clientes se gestionan desde el panel de superadmin.
 */
@Component
class RegisteredClientsInitializer implements ApplicationRunner {

    private final RegisteredClientRepository repository;
    private final RegisteredClientFactory factory;
    private final PasswordEncoder passwordEncoder;
    private final CherryCoreProperties properties;

    RegisteredClientsInitializer(RegisteredClientRepository repository, RegisteredClientFactory factory,
            PasswordEncoder passwordEncoder, CherryCoreProperties properties) {
        this.repository = repository;
        this.factory = factory;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        properties.oauth().clients().forEach(client -> {
            ClientDefinition definition = toDefinition(client);
            factory.validate(definition);
            String secret = client.type() == ClientType.CONFIDENTIAL ? passwordEncoder.encode(client.clientSecret())
                    : null;
            repository.save(factory.build(definition, secret));
        });
    }

    private static ClientDefinition toDefinition(Client client) {
        return new ClientDefinition(client.clientId(), client.clientName(), client.type(), client.audience(),
                client.applicationCode(), toStrings(client.redirectUris()), toStrings(client.postLogoutRedirectUris()),
                client.scopes(), client.machineAccess());
    }

    private static List<String> toStrings(List<URI> uris) {
        return uris == null ? List.of() : uris.stream().map(URI::toString).toList();
    }
}
