package com.cherrycore.auth.infrastructure;

import java.net.URI;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Manda al frontend de login diciéndole qué app lo pide ({@code ?client_id=...}).
 *
 * <p>Así el login puede mostrarse con la marca de esa app y ofrecer el acceso de
 * clientes; si se entra en CherryCore directamente, solo hay acceso de empresa.
 * Solo se añade si el {@code client_id} es de una app registrada (es un dato
 * público: va en la propia URL de autorización).</p>
 */
class AppAwareLoginEntryPoint extends LoginUrlAuthenticationEntryPoint {

    private final RegisteredClientRepository clients;

    AppAwareLoginEntryPoint(URI loginUrl, RegisteredClientRepository clients) {
        super(loginUrl.toString());
        this.clients = clients;
    }

    @Override
    protected String determineUrlToUseForThisRequest(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) {
        String loginUrl = super.determineUrlToUseForThisRequest(request, response, exception);
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId == null || clientId.isBlank() || clients.findByClientId(clientId) == null) {
            return loginUrl;
        }
        return UriComponentsBuilder.fromUriString(loginUrl)
                .queryParam(OAuth2ParameterNames.CLIENT_ID, clientId)
                .encode()
                .toUriString();
    }
}
