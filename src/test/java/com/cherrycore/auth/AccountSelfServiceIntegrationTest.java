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
        assertThat(JsonBodies.<Boolean>read(overview, "$.data.hasPassword")).isTrue();
        // La sesión dice quién soy (el frontend lo usa para marcar "tú" en los listados).
        assertThat(JsonBodies.<String>read(session.get("/api/auth/session"), "$.data.accountId"))
                .isEqualTo(JsonBodies.<String>read(overview, "$.data.id"));

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
    void guessingTheCurrentPasswordLocksTheAccount() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            session.call(HttpMethod.PUT, "/api/account/password", "{\"currentPassword\": \"intento-" + attempt
                    + "-xx\", \"newPassword\": \"" + NEW_PASSWORD + "\"}");
        }
        mailbox.awaitEmail(email, CustomerFixtures.LOCKED_SUBJECT_ES);

        assertThat(new BrowserSession(mockMvc).login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
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

        // El email anterior se enteró ya al pedirse el cambio (sin el enlace de confirmación).
        var notice = mailbox.awaitEmail(email, "Se ha pedido cambiar el email de tu cuenta");
        assertThat(notice.textBody()).contains(newEmail).doesNotContain(token);

        assertThat(new BrowserSession(mockMvc).login(newEmail, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(new BrowserSession(mockMvc).login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    /*
     * Quien tenía la sesión pide cambiar el email al suyo. La víctima recupera
     * la cuenta con "olvidé mi contraseña": el enlace de cambio de email que
     * recibió el atacante ya no debe servir (si no, se quedaría con la cuenta).
     */
    @Test
    void passwordResetCancelsAPendingEmailChange() throws Exception {
        String attackerEmail = CustomerFixtures.uniqueEmail();
        session.call(HttpMethod.POST, "/api/account/email",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newEmail\": \"" + attackerEmail + "\"}");
        String emailChangeToken = mailbox.awaitToken(attackerEmail, "Confirma tu nuevo email");

        BrowserSession victim = new BrowserSession(mockMvc);
        victim.postJson(AuthPaths.CUSTOMER_FORGOT_PASSWORD, "{\"email\": \"" + email + "\"}");
        String resetToken = mailbox.awaitToken(email, CustomerFixtures.RESET_SUBJECT_ES);
        assertThat(victim.postJson(AuthPaths.RESET_PASSWORD, "{\"token\": \"" + resetToken
                + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}").getResponse().getStatus()).isEqualTo(200);

        MvcResult confirm = new BrowserSession(mockMvc).postJson(AuthPaths.EMAIL_CHANGE_CONFIRM,
                "{\"token\": \"" + emailChangeToken + "\"}");
        assertThat(JsonBodies.<String>read(confirm, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_TOKEN");
        assertThat(new BrowserSession(mockMvc).login(email, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    /*
     * Un enlace de "olvidé mi contraseña" enviado al email anterior (quizá
     * comprometido, por eso se cambia) no vale tras cambiar el email, ni tras
     * cambiar la contraseña.
     */
    @Test
    void credentialChangesCancelPendingPasswordResetLinks() throws Exception {
        new BrowserSession(mockMvc).postJson(AuthPaths.CUSTOMER_FORGOT_PASSWORD, "{\"email\": \"" + email + "\"}");
        String oldMailboxReset = mailbox.awaitToken(email, CustomerFixtures.RESET_SUBJECT_ES);

        String newEmail = CustomerFixtures.uniqueEmail();
        session.call(HttpMethod.POST, "/api/account/email",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newEmail\": \"" + newEmail + "\"}");
        String token = mailbox.awaitToken(newEmail, "Confirma tu nuevo email");
        new BrowserSession(mockMvc).postJson(AuthPaths.EMAIL_CHANGE_CONFIRM, "{\"token\": \"" + token + "\"}");

        MvcResult afterEmailChange = new BrowserSession(mockMvc).postJson(AuthPaths.RESET_PASSWORD,
                "{\"token\": \"" + oldMailboxReset + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}");
        assertThat(JsonBodies.<String>read(afterEmailChange, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_TOKEN");

        mailbox.clear();
        new BrowserSession(mockMvc).postJson(AuthPaths.CUSTOMER_FORGOT_PASSWORD,
                "{\"email\": \"" + newEmail + "\"}");
        String resetBeforeChange = mailbox.awaitToken(newEmail, CustomerFixtures.RESET_SUBJECT_ES);
        session.call(HttpMethod.PUT, "/api/account/password",
                "{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}");

        MvcResult afterPasswordChange = new BrowserSession(mockMvc).postJson(AuthPaths.RESET_PASSWORD,
                "{\"token\": \"" + resetBeforeChange + "\", \"newPassword\": \"Otra-mas-2028-xx\"}");
        assertThat(JsonBodies.<String>read(afterPasswordChange, "$.error.code"))
                .isEqualTo("INVALID_OR_EXPIRED_TOKEN");
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

    /*
     * Con una sesión robada se podía activar el MFA con la app del atacante:
     * el dueño se quedaba fuera de su cuenta. Ahora pide la contraseña actual
     * y los fallos cuentan para el bloqueo.
     */
    @Test
    void turningOnTwoStepVerificationRequiresTheCurrentPassword() throws Exception {
        MvcResult withoutPassword = session.call(HttpMethod.POST, "/api/account/mfa/setup", "{}");
        assertThat(JsonBodies.<String>read(withoutPassword, "$.error.code")).isEqualTo("WRONG_CURRENT_PASSWORD");
        for (int attempt = 0; attempt < 4; attempt++) {
            MvcResult wrong = session.call(HttpMethod.POST, "/api/account/mfa/setup",
                    "{\"currentPassword\": \"intento-" + attempt + "-xx\"}");
            assertThat(JsonBodies.<String>read(wrong, "$.error.code")).isEqualTo("WRONG_CURRENT_PASSWORD");
        }
        mailbox.awaitEmail(email, CustomerFixtures.LOCKED_SUBJECT_ES);
        assertThat(JsonBodies.<Boolean>read(session.get("/api/account/mfa"), "$.data.enabled")).isFalse();
    }

    @Test
    void customersCanTurnOnTwoStepVerification() throws Exception {
        String secret = JsonBodies.read(session.call(HttpMethod.POST, "/api/account/mfa/setup",
                "{\"currentPassword\": \"" + PASSWORD + "\"}"), "$.data.secret");
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
