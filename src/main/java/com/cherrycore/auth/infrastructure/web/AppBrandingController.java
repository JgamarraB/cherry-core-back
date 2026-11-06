package com.cherrycore.auth.infrastructure.web;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.auth.infrastructure.ClientSettingNames;
import com.cherrycore.auth.infrastructure.clients.ClientErrorCode;
import com.cherrycore.catalog.application.ApplicationBranding;
import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

/**
 * Público (sin sesión): nombre y logo de la app que manda al usuario al login
 * ({@code /login?client_id=...}).
 *
 * <p>Solo devuelve la marca de la app del catálogo enlazada al cliente OAuth,
 * si está activa. Cliente desconocido, sin app o con la app desactivada dan el
 * mismo 404: no se revela nada más del cliente (ni URLs, ni scopes, ni tipo).</p>
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class AppBrandingController {

    /* Mismo formato que exige RegisteredClientFactory: evita consultas con basura. */
    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{2,99}$");

    private final RegisteredClientRepository clients;
    private final CatalogService catalog;

    AppBrandingController(RegisteredClientRepository clients, CatalogService catalog) {
        this.clients = clients;
        this.catalog = catalog;
    }

    @GetMapping(AuthPaths.APP_BRANDING)
    public ApiSuccessResponse<ApplicationBranding> branding(@PathVariable String clientId) {
        return ApiSuccessResponse.of(Optional.ofNullable(clientId)
                .filter(id -> CLIENT_ID.matcher(id).matches())
                .map(clients::findByClientId)
                .map(AppBrandingController::applicationCode)
                .flatMap(catalog::brandingOf)
                .orElseThrow(() -> new DomainException(ClientErrorCode.CLIENT_NOT_FOUND)));
    }

    private static String applicationCode(RegisteredClient client) {
        Object code = client.getClientSettings().getSetting(ClientSettingNames.APPLICATION);
        return code == null ? null : code.toString();
    }
}
