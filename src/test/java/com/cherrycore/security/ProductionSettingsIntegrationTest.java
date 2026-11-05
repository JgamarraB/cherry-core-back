package com.cherrycore.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.TotpCodes;

/**
 * Ajustes de producción: sin redirect URIs http://localhost y con un login a
 * medias que caduca solo (aquí en 2 segundos para no esperar 10 minutos).
 * Los clientes de configuración se sustituyen por uno https (los del perfil
 * test usan http://localhost y no arrancarían con estos ajustes).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "cherrycore.oauth.allow-insecure-redirect-uris=false",
        "cherrycore.oauth.clients[0].client-id=prod-like-app",
        "cherrycore.oauth.clients[0].client-name=Prod Like",
        "cherrycore.oauth.clients[0].type=CONFIDENTIAL",
        "cherrycore.oauth.clients[0].client-secret=prod-like-secret",
        "cherrycore.oauth.clients[0].audience=CUSTOMER",
        "cherrycore.oauth.clients[0].redirect-uris=https://app.example.com/login/oauth2/code/cherrycore",
        "cherrycore.oauth.clients[0].post-logout-redirect-uris=https://app.example.com/",
        "cherrycore.oauth.clients[0].scopes=openid",
        "cherrycore.security.login-protection.pending-login-ttl=2s"
})
class ProductionSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private StaffUserRepository staffUsers;

    @Test
    void adminPanelRejectsHttpRedirectUrisInProduction() throws Exception {
        BrowserSession admin = new BrowserSession(mockMvc);
        StaffFixtures.firstLoginWithMfaSetup(admin, StaffFixtures.createSuperAdmin(accounts, hasher, staffUsers));

        MvcResult localhost = admin.call(HttpMethod.POST, "/api/admin/clients",
                clientBody("local-app", "http://localhost:8081/callback", "https://app.example.com/"));
        assertThat(JsonBodies.<String>read(localhost, "$.error.code")).isEqualTo("INVALID_REDIRECT_URI");

        MvcResult httpLogout = admin.call(HttpMethod.POST, "/api/admin/clients",
                clientBody("logout-app", "https://app.example.com/callback", "http://localhost:4321/"));
        assertThat(JsonBodies.<String>read(httpLogout, "$.error.code")).isEqualTo("INVALID_REDIRECT_URI");

        MvcResult withCredentials = admin.call(HttpMethod.POST, "/api/admin/clients",
                clientBody("cred-app", "https://user:pass@app.example.com/callback", "https://app.example.com/"));
        assertThat(JsonBodies.<String>read(withCredentials, "$.error.code")).isEqualTo("INVALID_REDIRECT_URI");

        MvcResult https = admin.call(HttpMethod.POST, "/api/admin/clients",
                clientBody("secure-app", "https://app.example.com/callback", "https://app.example.com/"));
        assertThat(https.getResponse().getStatus()).isEqualTo(201);
    }

    /*
     * Un login a medias (contraseña bien, falta el código) duraba lo que la
     * sesión: con actividad, indefinidamente. Ahora caduca solo.
     */
    @Test
    void halfFinishedLoginExpires() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        String email = StaffFixtures.registerCompany(browser, mailbox, "Caduca SL");
        String secret = StaffFixtures.firstLoginWithMfaSetup(browser, email);

        BrowserSession late = new BrowserSession(mockMvc);
        MvcResult password = late.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(JsonBodies.<String>read(password, "$.data.status")).isEqualTo("MFA_REQUIRED");

        Awaitility.await().pollDelay(Duration.ofMillis(2100)).until(() -> true);

        assertThat(JsonBodies.<Object>read(late.get(AuthPaths.BASE + AuthPaths.SESSION), "$.data.pendingStep"))
                .isNull();
        MvcResult code = late.postJson(AuthPaths.MFA_VERIFY, "{\"code\": \"" + TotpCodes.code(secret, 1) + "\"}");
        assertThat(JsonBodies.<String>read(code, "$.error.code")).isEqualTo("NO_PENDING_LOGIN");
    }

    private static String clientBody(String clientId, String redirectUri, String postLogoutUri) {
        return """
                {"clientId": "%s", "name": "App", "type": "CONFIDENTIAL", "audience": "CUSTOMER",
                 "redirectUris": ["%s"], "postLogoutRedirectUris": ["%s"], "scopes": ["openid"],
                 "machineAccess": false}
                """.formatted(clientId, redirectUri, postLogoutUri);
    }
}
