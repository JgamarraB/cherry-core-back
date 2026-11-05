package com.cherrycore.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.port.AccountRepository;
import com.cherrycore.identity.application.port.PasswordHasher;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.domain.value.PersonName;

/**
 * Atajos para el backoffice: alta de empresa, MFA y login completo.
 */
public final class StaffFixtures {

    public static final String PASSWORD = CustomerFixtures.PASSWORD;
    private static final AtomicInteger TAX_SEQUENCE = new AtomicInteger(10_000_000);

    private StaffFixtures() {
    }

    public static String uniqueTaxId() {
        return "B" + TAX_SEQUENCE.incrementAndGet();
    }

    public static String registerCompanyBody(String ownerEmail, String taxId, String tradeName) {
        return """
                {"company": {"legalName": "%s S.L.", "tradeName": "%s", "taxId": "%s", "countryCode": "ES"},
                 "owner": {"email": "%s", "password": "%s", "firstName": "Laura", "lastName": "Martín"},
                 "acceptTerms": true}
                """.formatted(tradeName, tradeName, taxId, ownerEmail, PASSWORD);
    }

    /**
     * Registra la empresa por la API, verifica el email del dueño y devuelve su email.
     */
    public static String registerCompany(BrowserSession browser, CapturingEmailSender mailbox, String tradeName)
            throws Exception {
        String email = CustomerFixtures.uniqueEmail();
        MvcResult result = browser.postJson(AuthPaths.COMPANY_REGISTER,
                registerCompanyBody(email, uniqueTaxId(), tradeName));
        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        String token = mailbox.awaitToken(email, CustomerFixtures.VERIFICATION_SUBJECT_ES);
        browser.postJson(AuthPaths.CONFIRM_EMAIL, "{\"token\": \"" + token + "\"}");
        return email;
    }

    /**
     * Primer login de backoffice: configura el MFA y devuelve el secreto TOTP.
     */
    public static String firstLoginWithMfaSetup(BrowserSession browser, String email) throws Exception {
        MvcResult login = browser.postJson(AuthPaths.STAFF_LOGIN, loginBody(email));
        assertThat(JsonBodies.<String>read(login, "$.data.status")).isEqualTo("MFA_SETUP_REQUIRED");
        MvcResult setup = browser.postJson(AuthPaths.MFA_SETUP, "{}");
        String secret = JsonBodies.read(setup, "$.data.secret");
        MvcResult confirmed = browser.postJson(AuthPaths.MFA_SETUP_CONFIRM,
                "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);
        return secret;
    }

    /**
     * Login con contraseña + código TOTP en una sesión nueva.
     */
    public static BrowserSession login(MockMvc mockMvc, String email, String totpSecret) throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        MvcResult login = browser.postJson(AuthPaths.STAFF_LOGIN, loginBody(email));
        assertThat(JsonBodies.<String>read(login, "$.data.status")).isEqualTo("MFA_REQUIRED");
        MvcResult verified = browser.postJson(AuthPaths.MFA_VERIFY,
                "{\"code\": \"" + TotpCodes.code(totpSecret, 1) + "\"}");
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);
        return browser;
    }

    public static String loginBody(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}";
    }

    public static UUID companyIdOf(BrowserSession owner) throws Exception {
        return UUID.fromString(JsonBodies.read(owner.get("/api/company"), "$.data.id"));
    }

    public static void approve(CompanyReviewService review, UUID companyId) {
        review.approve(UUID.randomUUID(), companyId);
    }

    /**
     * Crea directamente un superadmin (en producción lo crea el arranque).
     */
    public static String createSuperAdmin(AccountRepository accounts, PasswordHasher hasher,
            StaffUserRepository staffUsers) {
        String email = CustomerFixtures.uniqueEmail();
        Account admin = Account.newInvitedStaff(UuidV7.generate(), EmailAddress.of(email), hasher.hash(PASSWORD),
                Locale.forLanguageTag("es"), java.time.Instant.now());
        admin.grantPlatformRole(PlatformRole.SUPER_ADMIN);
        accounts.save(admin);
        staffUsers.save(new StaffUser(admin.id(), PersonName.of("Super"), PersonName.of("Admin"), null));
        return email;
    }

    /**
     * Empresa aprobada con su dueño ya dentro (MFA hecho y empresa activa).
     */
    public static Owner activeCompany(MockMvc mockMvc, CapturingEmailSender mailbox, CompanyReviewService review,
            String tradeName) throws Exception {
        BrowserSession registration = new BrowserSession(mockMvc);
        String email = registerCompany(registration, mailbox, tradeName);
        String secret = firstLoginWithMfaSetup(registration, email);
        UUID companyId = UUID.fromString(JsonBodies.read(registration.get("/api/account/companies"),
                "$.data[0].company.id"));
        approve(review, companyId);
        BrowserSession session = login(mockMvc, email, secret);
        return new Owner(email, secret, session, companyId);
    }

    public record Owner(String email, String totpSecret, BrowserSession session, UUID companyId) {
    }

    public static MvcResult put(BrowserSession browser, String path, String json) throws Exception {
        return browser.call(HttpMethod.PUT, path, json);
    }
}
