package com.cherrycore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;

/**
 * Logo de las apps del catálogo y marca pública que ve el login ({@code ?client_id=}).
 */
@IntegrationTest
class AppBrandingIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private StaffUserRepository staffUsers;

    private BrowserSession admin;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = StaffFixtures.createSuperAdmin(accounts, hasher, staffUsers);
        admin = new BrowserSession(mockMvc);
        StaffFixtures.firstLoginWithMfaSetup(admin, adminEmail);
    }

    @Test
    void loginShowsTheNameAndLogoOfTheAppLinkedToTheClient() throws Exception {
        String appCode = "brand-" + UUID.randomUUID().toString().substring(0, 8);
        String appId = JsonBodies.read(admin.call(HttpMethod.POST, "/api/admin/applications",
                "{\"code\": \"" + appCode + "\", \"name\": \"Lirgar\"}"), "$.data.id");
        String clientId = appCode + "-web";
        MvcResult client = admin.call(HttpMethod.POST, "/api/admin/clients", """
                {"clientId": "%s", "name": "Lirgar web", "type": "CONFIDENTIAL", "audience": "CUSTOMER",
                 "applicationCode": "%s", "redirectUris": ["https://lirgar.example/login/oauth2/code/cherrycore"],
                 "scopes": ["openid"], "machineAccess": false}
                """.formatted(clientId, appCode));
        assertThat(client.getResponse().getStatus()).isEqualTo(201);

        MvcResult svg = admin.upload("/api/admin/applications/" + appId + "/logo", "file",
                "<svg onload=\"alert(1)\"/>".getBytes());
        assertThat(JsonBodies.<String>read(svg, "$.error.code")).isEqualTo("LOGO_INVALID_FORMAT");
        MvcResult uploaded = admin.upload("/api/admin/applications/" + appId + "/logo", "file", PNG);
        String logoUrl = JsonBodies.read(uploaded, "$.data.logoUrl");
        assertThat(logoUrl).contains("/applications/" + appId + "/public/branding/logo-").endsWith(".png");

        // Sin sesión: solo nombre y logo.
        MvcResult branding = new BrowserSession(mockMvc).get("/api/auth/apps/" + clientId);
        assertThat(branding.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(branding, "$.data.name")).isEqualTo("Lirgar");
        assertThat(JsonBodies.<String>read(branding, "$.data.logoUrl")).isEqualTo(logoUrl);
        assertThat(branding.getResponse().getContentAsString()).doesNotContain("redirect", "secret", appCode);

        MvcResult removed = admin.call(HttpMethod.DELETE, "/api/admin/applications/" + appId + "/logo", null);
        assertThat(JsonBodies.<Object>read(removed, "$.data.logoUrl")).isNull();

        // App desactivada: igual que si no existiera.
        admin.call(HttpMethod.PUT, "/api/admin/applications/" + appId, "{\"name\": \"Lirgar\", \"active\": false}");
        assertNotFound(new BrowserSession(mockMvc).get("/api/auth/apps/" + clientId));
    }

    @Test
    void unknownClientsOrClientsWithoutAppAreNotFound() throws Exception {
        BrowserSession anonymous = new BrowserSession(mockMvc);
        assertNotFound(anonymous.get("/api/auth/apps/does-not-exist"));
        assertNotFound(anonymous.get("/api/auth/apps/NOT_VALID!"));
        // test-bff (perfil test) no tiene app del catálogo enlazada.
        assertNotFound(anonymous.get("/api/auth/apps/test-bff"));
    }

    @Test
    void onlySuperAdminsManageAppLogos() throws Exception {
        MvcResult anonymous = new BrowserSession(mockMvc).upload(
                "/api/admin/applications/" + UUID.randomUUID() + "/logo", "file", PNG);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
    }

    private static void assertNotFound(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(JsonBodies.<String>read(result, "$.error.code")).isEqualTo("CLIENT_NOT_FOUND");
    }
}
