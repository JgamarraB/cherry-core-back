package com.cherrycore.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.StaffFixtures.Owner;

/**
 * "Descargar mis datos" (RGPD, derecho de acceso).
 */
@IntegrationTest
class AccountDataExportIntegrationTest {

    private static final String EXPORT = "/api/account/export";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PasswordHasher hasher;

    @Autowired
    private StaffUserRepository staffUsers;

    @Test
    void customerDownloadsTheirDataWithoutSecrets() throws Exception {
        String email = CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);
        BrowserSession customer = new BrowserSession(mockMvc);
        customer.login(email, CustomerFixtures.PASSWORD);

        MvcResult export = customer.get(EXPORT);

        assertThat(export.getResponse().getStatus()).isEqualTo(200);
        assertThat(export.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment").contains("cherrycore-data-");
        assertThat(JsonBodies.<String>read(export, "$.data.account.email")).isEqualTo(email);
        assertThat(JsonBodies.<String>read(export, "$.data.account.type")).isEqualTo("CUSTOMER");
        assertThat(JsonBodies.<String>read(export, "$.data.customer.termsVersion")).isNotBlank();
        assertThat(JsonBodies.<Object>read(export, "$.data.staff")).isNull();
        assertThat(JsonBodies.<List<String>>read(export, "$.data.securityEvents[*].type"))
                .contains("ACCOUNT_REGISTERED", "EMAIL_VERIFIED");
        assertThat(JsonBodies.<List<Boolean>>read(export, "$.data.sessions[*].current")).contains(true);

        String body = export.getResponse().getContentAsString();
        String passwordHash = jdbc.sql("SELECT password_hash FROM accounts WHERE email = ?").param(email)
                .query(String.class).single();
        assertThat(body).doesNotContain(passwordHash, "passwordHash", "secret", "token");

        long audited = jdbc.sql("""
                SELECT COUNT(*) FROM auth_events e JOIN accounts a ON a.id = e.account_id
                 WHERE a.email = ? AND e.type = 'ACCOUNT_DATA_EXPORTED'
                """).param(email).query(Long.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void staffExportIncludesProfileAndCompanies() throws Exception {
        Owner owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Datos Norte");

        MvcResult export = owner.session().get(EXPORT);

        assertThat(JsonBodies.<String>read(export, "$.data.staff.firstName")).isEqualTo("Laura");
        assertThat(JsonBodies.<String>read(export, "$.data.staff.companies[0].company.tradeName"))
                .isEqualTo("Datos Norte");
        assertThat(JsonBodies.<String>read(export, "$.data.staff.companies[0].roleCode")).isEqualTo("OWNER");
        assertThat(JsonBodies.<Boolean>read(export, "$.data.account.mfaEnabled")).isTrue();
        assertThat(export.getResponse().getContentAsString()).doesNotContain(owner.totpSecret());
    }

    /*
     * Las acciones de otra persona sobre mi cuenta (un superadmin, el dueño de
     * la empresa) quedan en mi auditoría con SU IP y SU navegador: no deben
     * salir en mi descarga.
     */
    @Test
    void exportDoesNotRevealTheIpOfWhoActedOnMyAccount() throws Exception {
        String email = CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);
        BrowserSession customer = new BrowserSession(mockMvc);
        customer.login(email, CustomerFixtures.PASSWORD);
        String customerId = StaffFixtures.accountIdOf(customer).toString();

        BrowserSession admin = new BrowserSession(mockMvc);
        StaffFixtures.firstLoginWithMfaSetup(admin,
                StaffFixtures.createSuperAdmin(accounts, hasher, staffUsers));
        admin.call(HttpMethod.POST, "/api/admin/accounts/" + customerId + "/unlock", null);
        admin.call(HttpMethod.POST, "/api/admin/accounts/" + customerId + "/password-reset", null);

        MvcResult export = customer.get(EXPORT);

        assertThat(JsonBodies.<List<Object>>read(export,
                "$.data.securityEvents[?(@.type == 'ACCOUNT_UNLOCKED_BY_ADMIN')].ip")).containsOnlyNulls()
                .hasSize(1);
        assertThat(JsonBodies.<List<Object>>read(export,
                "$.data.securityEvents[?(@.type == 'PASSWORD_RESET_REQUESTED')].ip")).containsOnlyNulls()
                .hasSize(1);
        assertThat(JsonBodies.<List<Object>>read(export,
                "$.data.securityEvents[?(@.type == 'ACCOUNT_UNLOCKED_BY_ADMIN')].userAgent")).containsOnlyNulls();
        // Mis propios eventos sí llevan mi IP.
        assertThat(JsonBodies.<List<Object>>read(export,
                "$.data.securityEvents[?(@.type == 'LOGIN_COMPLETED')].ip")).doesNotContainNull();
    }

    @Test
    void requiresASession() throws Exception {
        assertThat(new BrowserSession(mockMvc).get(EXPORT).getResponse().getStatus()).isEqualTo(401);
    }
}
