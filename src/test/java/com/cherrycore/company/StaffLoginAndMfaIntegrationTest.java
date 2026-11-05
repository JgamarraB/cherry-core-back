package com.cherrycore.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.cherrycore.support.TotpCodes;

@IntegrationTest
class StaffLoginAndMfaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    private JdbcClient jdbc;

    private BrowserSession browser;

    @BeforeEach
    void setUp() {
        browser = new BrowserSession(mockMvc);
    }

    @Test
    void firstStaffLoginForcesMfaSetupAndReturnsRecoveryCodes() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Uno");

        MvcResult login = browser.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(JsonBodies.<String>read(login, "$.data.status")).isEqualTo("MFA_SETUP_REQUIRED");

        // Sin completar el MFA no hay sesión.
        MvcResult session = browser.get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.authenticated")).isFalse();
        assertThat(JsonBodies.<String>read(session, "$.data.pendingStep")).isEqualTo("MFA_SETUP_REQUIRED");
        assertThat(browser.get("/api/company").getResponse().getStatus()).isEqualTo(401);

        MvcResult setup = browser.postJson(AuthPaths.MFA_SETUP, "{}");
        String secret = JsonBodies.read(setup, "$.data.secret");
        assertThat(JsonBodies.<String>read(setup, "$.data.otpauthUri")).startsWith("otpauth://totp/CherryCore:");

        MvcResult confirmed = browser.postJson(AuthPaths.MFA_SETUP_CONFIRM,
                "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        assertThat(JsonBodies.<List<String>>read(confirmed, "$.data.recoveryCodes")).hasSize(10);
        // La empresa está pendiente de aprobación: entra, pero sin empresa activa.
        assertThat(JsonBodies.<String>read(confirmed, "$.data.login.status")).isEqualTo("AUTHENTICATED");

        String stored = jdbc.sql("""
                SELECT f.secret_encrypted FROM mfa_factors f JOIN accounts a ON a.id = f.account_id WHERE a.email = ?
                """).param(email).query(String.class).single();
        assertThat(stored).doesNotContain(secret);
    }

    /*
     * Un superadmin desactiva una cuenta comprometida mientras el atacante
     * está en el paso del código: cerrar sus sesiones no borra el login a
     * medias (aún no tiene usuario), así que el segundo paso debe comprobarlo.
     */
    @Test
    void accountDisabledDuringTheSecondStepCannotFinishTheLogin() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Bloqueado");
        String secret = StaffFixtures.firstLoginWithMfaSetup(browser, email);

        BrowserSession attacker = new BrowserSession(mockMvc);
        MvcResult password = attacker.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(JsonBodies.<String>read(password, "$.data.status")).isEqualTo("MFA_REQUIRED");

        jdbc.sql("UPDATE accounts SET status = 'DISABLED' WHERE email = ?").param(email).update();

        MvcResult code = attacker.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + TotpCodes.code(secret, 1) + "\"}");
        assertThat(code.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonBodies.<String>read(code, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
        MvcResult session = attacker.get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<Boolean>read(session, "$.data.authenticated")).isFalse();
        assertThat(JsonBodies.<String>read(session, "$.data.pendingStep")).isNull();
    }

    /*
     * Los códigos de recuperación tienen ~49 bits: con un SHA-256 sin clave,
     * una copia de la base de datos bastaba para sacarlos por fuerza bruta.
     * Ahora son HMAC con una clave que no está en la base de datos.
     */
    @Test
    void recoveryCodesAreStoredAsKeyedHashes() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Hash");
        StaffFixtures.MfaSetup mfa = StaffFixtures.mfaSetup(browser, email);

        List<String> stored = jdbc.sql("""
                SELECT r.code_hash FROM mfa_recovery_codes r JOIN accounts a ON a.id = r.account_id
                 WHERE a.email = ? AND r.deleted_at IS NULL
                """).param(email).query(String.class).list();
        List<String> unkeyed = mfa.recoveryCodes().stream()
                .map(code -> sha256(code.replace("-", "").toLowerCase(java.util.Locale.ROOT))).toList();
        assertThat(stored).hasSize(10).doesNotContainAnyElementsOf(unkeyed);

        // Y siguen sirviendo para entrar (una sola vez).
        BrowserSession next = new BrowserSession(mockMvc);
        next.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        MvcResult recovered = next.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + mfa.recoveryCodes().getFirst() + "\"}");
        assertThat(recovered.getResponse().getStatus()).isEqualTo(200);
    }

    /*
     * Rotación de la clave de cifrado: cada secreto lleva el id de su clave
     * ("k1:..."). Los de antes del id (sin prefijo) se siguen leyendo y, al
     * usarse, se vuelven a cifrar con la clave actual.
     */
    @Test
    void mfaSecretsCarryTheirKeyIdAndLegacyOnesAreReencryptedOnUse() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Clave");
        String secret = StaffFixtures.firstLoginWithMfaSetup(browser, email);
        String select = """
                SELECT f.secret_encrypted FROM mfa_factors f JOIN accounts a ON a.id = f.account_id
                 WHERE a.email = ? AND f.deleted_at IS NULL
                """;
        String stored = jdbc.sql(select).param(email).query(String.class).single();
        assertThat(stored).startsWith("k1:");

        // Formato antiguo (sin id de clave): mismo cifrado, sin prefijo.
        jdbc.sql("""
                UPDATE mfa_factors SET secret_encrypted = ? WHERE account_id = (SELECT id FROM accounts WHERE email = ?)
                """).param(stored.substring("k1:".length())).param(email).update();

        StaffFixtures.login(mockMvc, email, secret);

        assertThat(jdbc.sql(select).param(email).query(String.class).single()).startsWith("k1:")
                .isNotEqualTo(stored);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Test
    void approvedCompanyIsSelectedAutomaticallyAfterMfa() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Dos");
        String secret = StaffFixtures.firstLoginWithMfaSetup(browser, email);
        UUID companyId = companyOf(email);
        StaffFixtures.approve(review, companyId, StaffFixtures.accountIdOf(browser));
        mailbox.awaitEmail(email, "Tu empresa ya está activa");

        BrowserSession owner = StaffFixtures.login(mockMvc, email, secret);

        MvcResult session = owner.get(AuthPaths.BASE + AuthPaths.SESSION);
        assertThat(JsonBodies.<String>read(session, "$.data.activeCompanyId")).isEqualTo(companyId.toString());
        assertThat(JsonBodies.<String>read(owner.get("/api/company"), "$.data.tradeName")).isEqualTo("Taller Dos");
    }

    @Test
    void wrongCodesAreLimitedAndAReusedCodeIsRejected() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Tres");
        String secret = StaffFixtures.firstLoginWithMfaSetup(browser, email);

        BrowserSession attacker = new BrowserSession(mockMvc);
        attacker.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        // El código usado al configurar el MFA no se puede reutilizar.
        MvcResult reused = attacker.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        assertThat(JsonBodies.<String>read(reused, "$.error.code")).isEqualTo("INVALID_MFA_CODE");

        for (int attempt = 0; attempt < 4; attempt++) {
            attacker.postJson(AuthPaths.MFA_VERIFY, "{\"code\": \"000000\"}");
        }
        MvcResult afterLimit = attacker.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + TotpCodes.code(secret, 1) + "\"}");
        assertThat(JsonBodies.<String>read(afterLimit, "$.error.code")).isEqualTo("NO_PENDING_LOGIN");
    }

    @Test
    void mfaFailuresAcrossSeveralLoginsLockTheAccount() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Seis");
        StaffFixtures.firstLoginWithMfaSetup(browser, email);

        // 3 fallos en un login y 2 en otro: el contador es de la cuenta, no del intento.
        for (int round = 0; round < 2; round++) {
            BrowserSession attacker = new BrowserSession(mockMvc);
            attacker.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
            for (int attempt = 0; attempt < (round == 0 ? 3 : 2); attempt++) {
                attacker.postJson(AuthPaths.MFA_VERIFY, "{\"code\": \"000000\"}");
            }
        }
        mailbox.awaitEmail(email, CustomerFixtures.LOCKED_SUBJECT_ES);

        // Bloqueada: ni con la contraseña correcta.
        MvcResult login = new BrowserSession(mockMvc).postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(JsonBodies.<String>read(login, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void companyRegistrationDoesNotRevealWhetherAnEmailExists() throws Exception {
        String existingOwner = StaffFixtures.registerCompany(browser, mailbox, "Taller Siete");
        String takenTaxId = jdbc.sql("SELECT c.tax_id FROM companies c JOIN memberships m ON m.company_id = c.id"
                + " JOIN accounts a ON a.id = m.staff_user_id WHERE a.email = ?")
                .param(existingOwner).query(String.class).single();

        MvcResult withExistingEmail = browser.postJson(AuthPaths.COMPANY_REGISTER,
                StaffFixtures.registerCompanyBody(existingOwner, takenTaxId, "Copia"));
        MvcResult withNewEmail = browser.postJson(AuthPaths.COMPANY_REGISTER,
                StaffFixtures.registerCompanyBody(CustomerFixtures.uniqueEmail(), takenTaxId, "Copia"));

        assertThat(withExistingEmail.getResponse().getContentAsString())
                .isEqualTo(withNewEmail.getResponse().getContentAsString());
        assertThat(JsonBodies.<String>read(withNewEmail, "$.error.code")).isEqualTo("TAX_ID_ALREADY_REGISTERED");
    }

    @Test
    void recoveryCodeWorksOnlyOnce() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Cuatro");
        MvcResult login = browser.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String secret = JsonBodies.read(browser.postJson(AuthPaths.MFA_SETUP, "{}"), "$.data.secret");
        MvcResult confirmed = browser.postJson(AuthPaths.MFA_SETUP_CONFIRM,
                "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        String recoveryCode = JsonBodies.<List<String>>read(confirmed, "$.data.recoveryCodes").getFirst();

        BrowserSession first = new BrowserSession(mockMvc);
        first.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(first.postJson(AuthPaths.MFA_VERIFY, "{\"code\": \"" + recoveryCode.toUpperCase() + "\"}")
                .getResponse().getStatus()).isEqualTo(200);

        BrowserSession second = new BrowserSession(mockMvc);
        second.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        assertThat(JsonBodies.<String>read(second.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + recoveryCode + "\"}"), "$.error.code")).isEqualTo("INVALID_MFA_CODE");
    }

    @Test
    void customerAndStaffAccountsAreSeparate() throws Exception {
        String email = StaffFixtures.registerCompany(browser, mailbox, "Taller Cinco");

        MvcResult asCustomer = new BrowserSession(mockMvc).login(email, StaffFixtures.PASSWORD);

        assertThat(JsonBodies.<String>read(asCustomer, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
    }

    private UUID companyOf(String ownerEmail) {
        return jdbc.sql("""
                SELECT m.company_id FROM memberships m JOIN accounts a ON a.id = m.staff_user_id WHERE a.email = ?
                """).param(ownerEmail).query(UUID.class).single();
    }
}
