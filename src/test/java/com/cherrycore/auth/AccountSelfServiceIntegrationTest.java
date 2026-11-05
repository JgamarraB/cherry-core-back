package com.cherrycore.auth;

import static com.cherrycore.support.CustomerFixtures.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;
import com.cherrycore.support.StaffFixtures.Owner;
import com.cherrycore.support.TotpCodes;

@IntegrationTest
class AccountSelfServiceIntegrationTest {

    private static final String NEW_PASSWORD = "Otra-clave-segura-2027";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private JdbcClient jdbc;

    private String email;
    private BrowserSession session;

    @BeforeEach
    void setUp() throws Exception {
        BrowserSession registration = new BrowserSession(mockMvc);
        email = CustomerFixtures.registerVerified(registration, mailbox);
        session = new BrowserSession(mockMvc);
        session.login(email, PASSWORD);
    }

    @Test
    void showsAndUpdatesMyAccount() throws Exception {
        MvcResult overview = session.get("/api/account");
        assertThat(JsonBodies.<String>read(overview, "$.data.type")).isEqualTo("CUSTOMER");
        assertThat(JsonBodies.<String>read(overview, "$.data.email")).isEqualTo(email);
        assertThat(JsonBodies.<Boolean>read(overview, "$.data.mfa.required")).isFalse();

        MvcResult updated = session.call(HttpMethod.PUT, "/api/account/profile",
                "{\"firstName\": \"Eva\", \"lastName\": \"Sanz\", \"locale\": \"en\"}");
        assertThat(JsonBodies.<String>read(updated, "$.data.firstName")).isEqualTo("Eva");
        assertThat(JsonBodies.<String>read(updated, "$.data.locale")).isEqualTo("en");
    }

    @Test
    void changingPasswordKeepsThisSessionAndClosesTheOthers() throws Exception {
        BrowserSession otherDevice = new BrowserSession(mockMvc);
        otherDevice.login(email, PASSWORD);

        MvcResult wrong = session.call(HttpMethod.PUT, "/api/account/password",
                "{\"currentPassword\": \"incorrecta-123\", \"newPassword\": \"" + NEW_PASSWORD + "\"}");
        assertThat(JsonBodies.<String>read(wrong, "$.error.code")).isEqualTo("WRONG_CURRENT_PASSWORD");

        MvcResult changed = session.call(HttpMethod.PUT, "/api/account/password",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}");
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);

        assertThat(session.get("/api/account").getResponse().getStatus()).isEqualTo(200);
        assertThat(otherDevice.get("/api/account").getResponse().getStatus()).isEqualTo(401);
        assertThat(new BrowserSession(mockMvc).login(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void emailChangeIsConfirmedFromTheNewAddress() throws Exception {
        String newEmail = CustomerFixtures.uniqueEmail();

        MvcResult requested = session.call(HttpMethod.POST, "/api/account/email",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newEmail\": \"" + newEmail + "\"}");
        assertThat(requested.getResponse().getStatus()).isEqualTo(202);

        String token = mailbox.awaitToken(newEmail, "Confirma tu nuevo email");
        MvcResult confirmed = new BrowserSession(mockMvc).postJson(AuthPaths.EMAIL_CHANGE_CONFIRM,
                "{\"token\": \"" + token + "\"}");
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        mailbox.awaitEmail(email, "El email de tu cuenta ha cambiado");

        assertThat(new BrowserSession(mockMvc).login(newEmail, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(new BrowserSession(mockMvc).login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void listsSessionsAndClosesOneRemotely() throws Exception {
        BrowserSession otherDevice = new BrowserSession(mockMvc);
        otherDevice.login(email, PASSWORD);

        MvcResult list = session.get("/api/account/sessions");
        List<String> others = JsonBodies.read(list, "$.data[?(@.current == false)].id");
        assertThat(others).hasSize(1);
        assertThat(JsonBodies.<List<String>>read(list, "$.data[*].userAgent")).contains("cherrycore-tests");

        session.call(HttpMethod.DELETE, "/api/account/sessions/" + others.getFirst(), null);

        assertThat(otherDevice.get("/api/account").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void customersCanTurnOnTwoStepVerification() throws Exception {
        String secret = JsonBodies.read(session.call(HttpMethod.POST, "/api/account/mfa/setup", null),
                "$.data.secret");
        MvcResult enabled = session.call(HttpMethod.POST, "/api/account/mfa/setup/confirm",
                "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        assertThat(JsonBodies.<List<String>>read(enabled, "$.data.recoveryCodes")).hasSize(10);

        BrowserSession next = new BrowserSession(mockMvc);
        assertThat(JsonBodies.<String>read(next.login(email, PASSWORD), "$.data.status")).isEqualTo("MFA_REQUIRED");

        MvcResult disabled = session.call(HttpMethod.POST, "/api/account/mfa/disable",
                "{\"code\": \"" + TotpCodes.code(secret, 1) + "\"}");
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<String>read(new BrowserSession(mockMvc).login(email, PASSWORD), "$.data.status"))
                .isEqualTo("AUTHENTICATED");
    }

    @Test
    void deletingTheAccountAnonymizesDataAndFreesTheEmail() throws Exception {
        MvcResult deleted = session.call(HttpMethod.POST, "/api/account/delete",
                "{\"currentPassword\": \"" + PASSWORD + "\"}");
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        mailbox.awaitEmail(email, "Tu cuenta se ha eliminado");

        assertThat(session.get("/api/account").getResponse().getStatus()).isEqualTo(401);
        assertThat(new BrowserSession(mockMvc).login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        long remaining = jdbc.sql("SELECT COUNT(*) FROM accounts WHERE email = ?").param(email)
                .query(Long.class).single();
        assertThat(remaining).isZero();
        String anonymizedName = jdbc.sql("""
                SELECT c.first_name FROM customers c JOIN accounts a ON a.id = c.id
                 WHERE a.email LIKE 'deleted-%' AND c.deleted_at IS NOT NULL ORDER BY c.updated_at DESC LIMIT 1
                """).query(String.class).single();
        assertThat(anonymizedName).isEqualTo("Deleted");

        // El email queda libre para registrarse de nuevo.
        mailbox.clear();
        CustomerFixtures.register(new BrowserSession(mockMvc), email);
        mailbox.awaitEmail(email, CustomerFixtures.VERIFICATION_SUBJECT_ES);
    }

    @Test
    void soleOwnerCannotDeleteTheirAccount() throws Exception {
        Owner owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Unico Dueño");

        MvcResult attempt = owner.session().call(HttpMethod.POST, "/api/account/delete",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"mfaCode\": \""
                        + owner.recoveryCodes().getFirst() + "\"}");

        assertThat(JsonBodies.<String>read(attempt, "$.error.code")).isEqualTo("SOLE_OWNER_CANNOT_LEAVE");
        assertThat(owner.session().get("/api/account").getResponse().getStatus()).isEqualTo(200);
    }
}
