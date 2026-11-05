package com.cherrycore.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.TotpCodes;

@IntegrationTest
class AdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private StaffUserRepository staffUsers;

    private BrowserSession admin;
    private String adminEmail;
    private String adminTotpSecret;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = StaffFixtures.createSuperAdmin(accounts, hasher, staffUsers);
        admin = new BrowserSession(mockMvc);
        adminTotpSecret = StaffFixtures.firstLoginWithMfaSetup(admin, adminEmail);
    }

    @Test
    void superAdminSessionAndAccessControl() throws Exception {
        MvcResult session = admin.get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.superAdmin")).isTrue();
        assertThat(admin.get("/api/admin/dashboard").getResponse().getStatus()).isEqualTo(200);

        StaffFixtures.Owner owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Sin Admin");
        assertThat(owner.session().get("/api/admin/dashboard").getResponse().getStatus()).isEqualTo(403);

        BrowserSession customer = new BrowserSession(mockMvc);
        customer.login(CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox),
                CustomerFixtures.PASSWORD);
        assertThat(customer.get("/api/admin/accounts").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void reviewsPendingCompanies() throws Exception {
        String ownerEmail = StaffFixtures.registerCompany(new BrowserSession(mockMvc), mailbox, "Pendiente SL");
        mailbox.awaitEmail(adminEmail, "Nueva empresa pendiente de aprobación");

        MvcResult pending = admin.get("/api/admin/companies?status=PENDING_APPROVAL&search=pendiente");
        List<String> ids = JsonBodies.read(pending, "$.data.items[*].id");
        assertThat(ids).hasSize(1);
        assertThat(JsonBodies.<Integer>read(pending, "$.data.totalItems")).isEqualTo(1);

        MvcResult rejectWithoutReason = admin.call(HttpMethod.POST,
                "/api/admin/companies/" + ids.getFirst() + "/reject", "{\"reason\": \"\"}");
        assertThat(rejectWithoutReason.getResponse().getStatus()).isEqualTo(400);

        MvcResult approved = admin.call(HttpMethod.POST, "/api/admin/companies/" + ids.getFirst() + "/approve",
                null);
        assertThat(JsonBodies.<String>read(approved, "$.data.status")).isEqualTo("ACTIVE");
        mailbox.awaitEmail(ownerEmail, "Tu empresa ya está activa");

        MvcResult again = admin.call(HttpMethod.POST, "/api/admin/companies/" + ids.getFirst() + "/approve", null);
        assertThat(JsonBodies.<String>read(again, "$.error.code")).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void managesAccounts() throws Exception {
        String email = CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);
        MvcResult found = admin.get("/api/admin/accounts?type=CUSTOMER&search=" + email);
        String accountId = JsonBodies.<List<String>>read(found, "$.data.items[*].id").getFirst();

        admin.call(HttpMethod.POST, "/api/admin/accounts/" + accountId + "/disable", null);
        assertThat(new BrowserSession(mockMvc).login(email, CustomerFixtures.PASSWORD).getResponse().getStatus())
                .isEqualTo(401);

        MvcResult enabled = admin.call(HttpMethod.POST, "/api/admin/accounts/" + accountId + "/enable", null);
        assertThat(JsonBodies.<String>read(enabled, "$.data.status")).isEqualTo("ACTIVE");
        assertThat(new BrowserSession(mockMvc).login(email, CustomerFixtures.PASSWORD).getResponse().getStatus())
                .isEqualTo(200);

        MvcResult detail = admin.get("/api/admin/accounts/" + accountId);
        assertThat(JsonBodies.<List<String>>read(detail, "$.data.recentEvents[*].type"))
                .contains("ACCOUNT_DISABLED_BY_ADMIN", "ACCOUNT_ENABLED_BY_ADMIN");

        String myId = JsonBodies.<List<String>>read(admin.get("/api/admin/accounts?search=" + adminEmail),
                "$.data.items[*].id").getFirst();
        MvcResult selfDisable = admin.call(HttpMethod.POST, "/api/admin/accounts/" + myId + "/disable", null);
        assertThat(JsonBodies.<String>read(selfDisable, "$.error.code")).isEqualTo("CANNOT_MODIFY_OWN_ACCOUNT");
    }

    @Test
    void registersAnAppWhoseBackendSyncsItsPermissions() throws Exception {
        String appCode = "app-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult app = admin.call(HttpMethod.POST, "/api/admin/applications",
                "{\"code\": \"" + appCode + "\", \"name\": \"Lirgar Admin\"}");
        assertThat(app.getResponse().getStatus()).isEqualTo(201);
        String appId = JsonBodies.read(app, "$.data.id");

        MvcResult badRedirect = admin.call(HttpMethod.POST, "/api/admin/clients", clientBody(appCode + "-x", appCode,
                "http://evil.example.com/callback"));
        assertThat(JsonBodies.<String>read(badRedirect, "$.error.code")).isEqualTo("INVALID_REDIRECT_URI");

        String clientId = appCode + "-backend";
        MvcResult created = admin.call(HttpMethod.POST, "/api/admin/clients", clientBody(clientId, appCode,
                "https://admin.lirgar.example/login/oauth2/code/cherrycore"));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String secret = JsonBodies.read(created, "$.data.clientSecret");
        assertThat(secret).hasSizeGreaterThan(30);
        assertThat(admin.get("/api/admin/clients/" + clientId).getResponse().getContentAsString())
                .doesNotContain(secret);

        String machineToken = machineToken(clientId, secret);
        MvcResult synced = mockMvc.perform(put("/api/apps/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + machineToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": [{\"code\": \"vehicles.manage\", \"description\": \"Manage\"},"
                        + " {\"code\": \"reservations.view\"}]}")).andReturn();
        assertThat(synced.getResponse().getStatus()).isEqualTo(200);

        List<String> codes = JsonBodies.read(admin.get("/api/admin/applications/" + appId + "/permissions"),
                "$.data[*].code");
        assertThat(codes).containsExactly("reservations.view", "vehicles.manage");

        MvcResult rotated = admin.call(HttpMethod.POST, "/api/admin/clients/" + clientId + "/secret", null);
        String newSecret = JsonBodies.read(rotated, "$.data.clientSecret");
        assertThat(tokenRequest(clientId, secret).getResponse().getStatus()).isEqualTo(401);
        assertThat(tokenRequest(clientId, newSecret).getResponse().getStatus()).isEqualTo(200);
    }

    /*
     * La app "cherrycore" es la gestión de empresas: un cliente enlazado a ella
     * con acceso de máquina podría retirar sus permisos del catálogo (y romper
     * la gestión de todas las empresas); uno de backoffice los llevaría en sus tokens.
     */
    @Test
    void clientsCannotBeBoundToTheReservedCherryCoreApplication() throws Exception {
        MvcResult attempt = admin.call(HttpMethod.POST, "/api/admin/clients", clientBody(
                "evil-" + UUID.randomUUID().toString().substring(0, 8), "cherrycore",
                "https://evil.example.com/login/oauth2/code/cherrycore"));

        assertThat(attempt.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonBodies.<String>read(attempt, "$.error.code")).isEqualTo("RESERVED_APPLICATION");
    }

    /*
     * El panel no deja quitar el último superadmin, pero "borrar mi cuenta" sí
     * lo dejaba: la plataforma se quedaba sin nadie que aprobara empresas.
     */
    @Test
    void lastSuperAdminCannotDeleteTheirOwnAccount() throws Exception {
        UUID myId = StaffFixtures.accountIdOf(admin);
        accounts.findSuperAdmins().stream()
                .filter(other -> !other.id().equals(myId))
                .forEach(other -> {
                    other.revokePlatformRole();
                    accounts.save(other);
                });

        MvcResult attempt = admin.call(HttpMethod.POST, "/api/account/delete",
                "{\"currentPassword\": \"" + StaffFixtures.PASSWORD + "\", \"mfaCode\": \""
                        + TotpCodes.code(adminTotpSecret, 1) + "\"}");

        assertThat(JsonBodies.<String>read(attempt, "$.error.code")).isEqualTo("LAST_SUPER_ADMIN");
        assertThat(admin.get("/api/account").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void userTokensCannotSyncPermissions() throws Exception {
        MvcResult attempt = mockMvc.perform(put("/api/apps/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": []}")).andReturn();
        assertThat(attempt.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void listsAuditEvents() throws Exception {
        CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);

        MvcResult events = admin.get("/api/admin/audit-events?type=EMAIL_VERIFIED&size=5");

        assertThat(JsonBodies.<List<String>>read(events, "$.data.items[*].type")).isNotEmpty()
                .allMatch("EMAIL_VERIFIED"::equals);
        assertThat(JsonBodies.<Integer>read(events, "$.data.size")).isEqualTo(5);
    }

    @Test
    void auditEventsCarryTheirContext() throws Exception {
        String email = CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);
        String accountId = JsonBodies.<List<String>>read(admin.get("/api/admin/accounts?search=" + email),
                "$.data.items[*].id").getFirst();
        admin.call(HttpMethod.POST, "/api/admin/accounts/" + accountId + "/disable", null);

        MvcResult disabled = admin.get("/api/admin/audit-events?type=ACCOUNT_DISABLED_BY_ADMIN&accountId="
                + accountId);
        assertThat(JsonBodies.<String>read(disabled, "$.data.items[0].actorEmail")).isEqualTo(adminEmail);
        assertThat(JsonBodies.<String>read(disabled, "$.data.items[0].details.actorAccountId")).isNotBlank();

        StaffFixtures.registerCompany(new BrowserSession(mockMvc), mailbox, "Contexto SL");
        String pendingId = JsonBodies.<List<String>>read(
                admin.get("/api/admin/companies?status=PENDING_APPROVAL&search=contexto"), "$.data.items[*].id")
                .getFirst();
        admin.call(HttpMethod.POST, "/api/admin/companies/" + pendingId + "/approve", null);

        MvcResult approved = admin.get("/api/admin/audit-events?type=COMPANY_APPROVED&size=100");
        List<String> names = JsonBodies.read(approved, "$.data.items[*].details.companyName");
        assertThat(names).contains("Contexto SL");
        List<String> ids = JsonBodies.read(approved, "$.data.items[?(@.details.companyName == 'Contexto SL')]"
                + ".details.companyId");
        assertThat(ids).containsExactly(pendingId);
    }

    private static String clientBody(String clientId, String appCode, String redirectUri) {
        return """
                {"clientId": "%s", "name": "Lirgar backoffice", "type": "CONFIDENTIAL", "audience": "STAFF",
                 "applicationCode": "%s", "redirectUris": ["%s"], "scopes": ["openid"], "machineAccess": true}
                """.formatted(clientId, appCode, redirectUri);
    }

    private String machineToken(String clientId, String secret) throws Exception {
        MvcResult token = tokenRequest(clientId, secret);
        assertThat(token.getResponse().getStatus()).isEqualTo(200);
        return JsonBodies.read(token, "$.access_token");
    }

    private MvcResult tokenRequest(String clientId, String secret) throws Exception {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", "catalog.sync")).andReturn();
    }
}
