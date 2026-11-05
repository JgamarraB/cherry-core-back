package com.cherrycore.auth;

import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static com.cherrycore.support.CustomerFixtures.RESET_SUBJECT_ES;
import static com.cherrycore.support.CustomerFixtures.registerVerified;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.OAuthClientSimulator;

/**
 * Flujo completo tal y como lo hará una app real:
 * app → /oauth2/authorize → login en el frontend de CherryCore → código →
 * tokens → API con Bearer → refresh.
 */
@IntegrationTest
class OAuth2AuthorizationCodeFlowIntegrationTest {

    private static final String BFF_REDIRECT = "http://localhost:8081/login/oauth2/code/cherrycore";
    private static final String STAFF_REDIRECT = "http://localhost:8082/login/oauth2/code/cherrycore";
    private static final String LOGIN_PAGE = "http://localhost:4200/login";
    private static final String SCOPES = "openid profile email";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private JwtDecoder jwtDecoder;

    private BrowserSession browser;
    private OAuthClientSimulator bff;

    @BeforeEach
    void setUp() {
        browser = new BrowserSession(mockMvc);
        bff = new OAuthClientSimulator(mockMvc, "test-bff", "test-bff-secret", BFF_REDIRECT);
    }

    @Test
    void fullLoginIssuesTokensUsableAgainstTheApi() throws Exception {
        String email = registerVerified(browser, mailbox);
        Tokens tokens = loginThroughAuthorizationServer(email);

        Jwt accessToken = jwtDecoder.decode(tokens.access());
        assertThat(accessToken.getIssuer()).hasToString("http://localhost");
        assertThat(accessToken.getClaimAsString("account_type")).isEqualTo("CUSTOMER");
        assertThat(accessToken.getSubject()).matches("[0-9a-f\\-]{36}");

        Jwt idToken = jwtDecoder.decode(tokens.id());
        assertThat(idToken.getClaimAsString("email")).isEqualTo(email);
        assertThat(idToken.getClaimAsBoolean("email_verified")).isTrue();
        assertThat(idToken.getClaimAsString("given_name")).isEqualTo("Ana");

        MvcResult me = mockMvc.perform(get("/api/customers/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.access())).andReturn();
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(me, "$.data.email")).isEqualTo(email);
        assertThat(JsonBodies.<String>read(me, "$.data.phone")).isEqualTo("+34600111222");
    }

    @Test
    void refreshTokensRotateAndOldOnesAreRejected() throws Exception {
        String email = registerVerified(browser, mailbox);
        Tokens tokens = loginThroughAuthorizationServer(email);

        MvcResult refreshed = bff.refresh(tokens.refresh());
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        String newRefresh = JsonBodies.read(refreshed, "$.refresh_token");
        assertThat(newRefresh).isNotEqualTo(tokens.refresh());

        MvcResult reused = bff.refresh(tokens.refresh());
        assertThat(reused.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(reused, "$.error")).isEqualTo("invalid_grant");
    }

    @Test
    void passwordResetRevokesRefreshTokens() throws Exception {
        String email = registerVerified(browser, mailbox);
        Tokens tokens = loginThroughAuthorizationServer(email);

        BrowserSession other = new BrowserSession(mockMvc);
        other.postJson(AuthPaths.CUSTOMER_FORGOT_PASSWORD, "{\"email\": \"" + email + "\"}");
        String token = mailbox.awaitToken(email, RESET_SUBJECT_ES);
        other.postJson(AuthPaths.RESET_PASSWORD,
                "{\"token\": \"" + token + "\", \"newPassword\": \"Nueva-clave-segura-2027\"}");

        assertThat(bff.refresh(tokens.refresh()).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void customerAccountsCannotGetTokensForStaffApps() throws Exception {
        String email = registerVerified(browser, mailbox);
        browser.login(email, PASSWORD);
        OAuthClientSimulator staffApp = new OAuthClientSimulator(mockMvc, "test-staff-app", "test-staff-secret",
                STAFF_REDIRECT);

        MvcResult authorized = browser.get(staffApp.authorizationUrl("openid"), MediaType.TEXT_HTML);
        String code = OAuthClientSimulator.codeFrom(authorized.getResponse().getRedirectedUrl());
        MvcResult token = staffApp.exchangeCode(code);

        assertThat(token.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(token, "$.error")).isEqualTo("access_denied");
    }

    @Test
    void forgedOrMissingTokensAreRejectedByTheApi() throws Exception {
        assertThat(mockMvc.perform(get("/api/customers/me")).andReturn().getResponse().getStatus()).isEqualTo(401);
        MvcResult forged = mockMvc.perform(get("/api/customers/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.")).andReturn();
        assertThat(forged.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonBodies.<String>read(forged, "$.error.code")).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void discoveryAndPublicKeysArePublished() throws Exception {
        MvcResult discovery = mockMvc.perform(get("/.well-known/openid-configuration")).andReturn();
        assertThat(discovery.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(discovery, "$.issuer")).isEqualTo("http://localhost");

        MvcResult jwks = mockMvc.perform(get("/oauth2/jwks")).andReturn();
        assertThat(JsonBodies.<String>read(jwks, "$.keys[0].kty")).isEqualTo("RSA");
        assertThat(jwks.getResponse().getContentAsString()).doesNotContain("\"d\"");
    }

    private Tokens loginThroughAuthorizationServer(String email) throws Exception {
        // 1. La app manda al navegador a /oauth2/authorize; sin sesión → frontend de login.
        String authorizationUrl = bff.authorizationUrl(SCOPES);
        MvcResult toLogin = browser.get(authorizationUrl, MediaType.TEXT_HTML);
        assertThat(toLogin.getResponse().getRedirectedUrl()).isEqualTo(LOGIN_PAGE);

        // 2. El frontend hace login y recibe a dónde volver.
        MvcResult login = browser.login(email, PASSWORD);
        String redirectUrl = JsonBodies.read(login, "$.data.redirectUrl");
        assertThat(redirectUrl).startsWith("http://localhost/oauth2/authorize");

        // 3. Con sesión, /oauth2/authorize devuelve el código a la app.
        MvcResult authorized = browser.get(redirectUrl, MediaType.TEXT_HTML);
        String location = authorized.getResponse().getRedirectedUrl();
        assertThat(location).startsWith(BFF_REDIRECT).contains("state=" + OAuthClientSimulator.STATE);

        // 4. El backend de la app canjea el código (con su secreto y el PKCE).
        MvcResult tokenResponse = bff.exchangeCode(OAuthClientSimulator.codeFrom(location));
        assertThat(tokenResponse.getResponse().getStatus()).isEqualTo(200);
        return new Tokens(JsonBodies.read(tokenResponse, "$.access_token"),
                JsonBodies.read(tokenResponse, "$.refresh_token"),
                JsonBodies.read(tokenResponse, "$.id_token"));
    }

    private record Tokens(String access, String refresh, String id) {
    }
}
