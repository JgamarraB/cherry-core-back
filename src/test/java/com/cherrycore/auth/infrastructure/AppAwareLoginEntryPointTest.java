package com.cherrycore.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class AppAwareLoginEntryPointTest {

    private static final String LOGIN = "http://localhost:4200/login";

    private final AppAwareLoginEntryPoint entryPoint = new AppAwareLoginEntryPoint(URI.create(LOGIN),
            new InMemoryRegisteredClientRepository(RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("lirgar-web")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost:8081/callback")
                    .build()));

    @Test
    void addsTheClientIdOfARegisteredApp() throws Exception {
        assertThat(redirectFor("lirgar-web")).isEqualTo(LOGIN + "?client_id=lirgar-web");
    }

    @Test
    void ignoresUnknownClients() throws Exception {
        assertThat(redirectFor("<script>")).isEqualTo(LOGIN);
    }

    @Test
    void keepsThePlainLoginWithoutClient() throws Exception {
        assertThat(redirectFor(null)).isEqualTo(LOGIN);
    }

    private String redirectFor(String clientId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        if (clientId != null) {
            request.setParameter("client_id", clientId);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new InsufficientAuthenticationException("login"));
        return response.getRedirectedUrl();
    }
}
