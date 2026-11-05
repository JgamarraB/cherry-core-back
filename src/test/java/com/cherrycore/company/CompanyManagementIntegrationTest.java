package com.cherrycore.company;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
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
class CompanyManagementIntegrationTest {

    private static final String INVITATION_SUBJECT_ES = "Te han invitado a unirte a un equipo";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    private Owner owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Rent Sur");
    }

    @Test
    void ownerInvitesAMemberWhoCreatesTheirAccount() throws Exception {
        String staffRoleId = roleId("STAFF");
        String invitee = CustomerFixtures.uniqueEmail();

        MvcResult invited = owner.session().call(HttpMethod.POST, "/api/company/invitations",
                "{\"email\": \"" + invitee + "\", \"roleId\": \"" + staffRoleId + "\"}");
        assertThat(invited.getResponse().getStatus()).isEqualTo(201);

        String token = mailbox.awaitToken(invitee, INVITATION_SUBJECT_ES);
        BrowserSession guest = new BrowserSession(mockMvc);
        MvcResult info = guest.postJson(AuthPaths.INVITATION_LOOKUP, "{\"token\": \"" + token + "\"}");
        assertThat(JsonBodies.<String>read(info, "$.data.companyName")).isEqualTo("Rent Sur");
        assertThat(JsonBodies.<Boolean>read(info, "$.data.accountExists")).isFalse();

        MvcResult accepted = guest.postJson(AuthPaths.INVITATION_ACCEPT, """
                {"token": "%s", "firstName": "Pablo", "lastName": "Ruiz", "password": "%s"}
                """.formatted(token, StaffFixtures.PASSWORD));
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);

        List<String> emails = JsonBodies.read(owner.session().get("/api/company/members"), "$.data[*].email");
        assertThat(emails).contains(owner.email(), invitee);

        // El enlace no se puede usar otra vez.
        MvcResult again = new BrowserSession(mockMvc).postJson(AuthPaths.INVITATION_LOOKUP,
                "{\"token\": \"" + token + "\"}");
        assertThat(JsonBodies.<String>read(again, "$.error.code")).isEqualTo("INVALID_OR_EXPIRED_INVITATION");
    }

    @Test
    void membersOnlyDoWhatTheirRoleAllows() throws Exception {
        BrowserSession member = addMember("STAFF");

        // El dueño lo puede todo; STAFF, nada por defecto (y así lo ve el frontend).
        MvcResult ownerAccess = owner.session().get("/api/company/me");
        assertThat(JsonBodies.<Boolean>read(ownerAccess, "$.data.owner")).isTrue();
        assertThat(JsonBodies.<List<String>>read(ownerAccess, "$.data.permissions")).hasSize(5);
        MvcResult memberAccess = member.get("/api/company/me");
        assertThat(JsonBodies.<String>read(memberAccess, "$.data.roleCode")).isEqualTo("STAFF");
        assertThat(JsonBodies.<List<String>>read(memberAccess, "$.data.permissions")).isEmpty();

        // STAFF no tiene permisos por defecto.
        MvcResult denied = member.get("/api/company/members");
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonBodies.<String>read(denied, "$.error.code")).isEqualTo("COMPANY_PERMISSION_DENIED");

        // El dueño le da "ver miembros" al rol STAFF.
        String viewMembers = permissionId("company.members.view");
        MvcResult updated = owner.session().call(HttpMethod.PUT, "/api/company/roles/" + roleId("STAFF"),
                "{\"permissionIds\": [\"" + viewMembers + "\"]}");
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);

        assertThat(member.get("/api/company/members").getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonBodies.<List<String>>read(member.get("/api/company/me"), "$.data.permissions"))
                .containsExactly("company.members.view");
        // Pero sigue sin poder invitar.
        MvcResult invite = member.call(HttpMethod.POST, "/api/company/invitations",
                "{\"email\": \"x@example.com\", \"roleId\": \"" + roleId("STAFF") + "\"}");
        assertThat(invite.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void ownerRulesAreProtected() throws Exception {
        String ownerMembership = membershipIdOf(owner.email());

        MvcResult suspendSelf = owner.session().call(HttpMethod.POST,
                "/api/company/members/" + ownerMembership + "/suspend", null);
        assertThat(JsonBodies.<String>read(suspendSelf, "$.error.code")).isEqualTo("LAST_OWNER");

        // Un ADMIN no puede darse ni dar el rol OWNER.
        BrowserSession admin = addMember("ADMIN");
        String adminMembership = membershipIdOf(admin);
        MvcResult escalate = admin.call(HttpMethod.PUT, "/api/company/members/" + adminMembership,
                "{\"roleId\": \"" + roleId("OWNER") + "\"}");
        assertThat(JsonBodies.<String>read(escalate, "$.error.code")).isEqualTo("OWNER_ROLE_RESERVED");

        MvcResult renameSystem = owner.session().call(HttpMethod.PUT, "/api/company/roles/" + roleId("ADMIN"),
                "{\"name\": \"Jefe\", \"permissionIds\": []}");
        assertThat(JsonBodies.<String>read(renameSystem, "$.error.code")).isEqualTo("SYSTEM_ROLE_IMMUTABLE");
    }

    @Test
    void customRolesCanBeCreatedAndDeletedWhenUnused() throws Exception {
        MvcResult created = owner.session().call(HttpMethod.POST, "/api/company/roles",
                "{\"code\": \"FRONT_DESK\", \"name\": \"Mostrador\", \"permissionIds\": [\""
                        + permissionId("company.members.view") + "\"]}");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String roleId = JsonBodies.read(created, "$.data.id");

        MvcResult duplicate = owner.session().call(HttpMethod.POST, "/api/company/roles",
                "{\"code\": \"FRONT_DESK\", \"name\": \"Otro\", \"permissionIds\": []}");
        assertThat(JsonBodies.<String>read(duplicate, "$.error.code")).isEqualTo("ROLE_CODE_ALREADY_EXISTS");

        MvcResult foreignPermission = owner.session().call(HttpMethod.PUT, "/api/company/roles/" + roleId,
                "{\"permissionIds\": [\"" + UUID.randomUUID() + "\"]}");
        assertThat(JsonBodies.<String>read(foreignPermission, "$.error.code")).isEqualTo("PERMISSION_NOT_AVAILABLE");

        MvcResult deleted = owner.session().call(HttpMethod.DELETE, "/api/company/roles/" + roleId, null);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoIsValidatedByContentAndServedPublicly() throws Exception {
        MvcResult svg = owner.session().upload("/api/company/logo", "file",
                "<svg onload=\"alert(1)\"/>".getBytes());
        assertThat(JsonBodies.<String>read(svg, "$.error.code")).isEqualTo("LOGO_INVALID_FORMAT");

        MvcResult uploaded = owner.session().upload("/api/company/logo", "file", PNG);
        assertThat(uploaded.getResponse().getStatus()).isEqualTo(200);
        String logoUrl = JsonBodies.read(uploaded, "$.data.logoUrl");
        assertThat(logoUrl).contains("/companies/" + owner.companyId() + "/public/branding/logo-").endsWith(".png");

        HttpResponse<byte[]> download = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(logoUrl)).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PNG);
    }

    @Test
    void myCompaniesShowTheRoleName() throws Exception {
        MvcResult mine = owner.session().get("/api/account/companies");
        assertThat(JsonBodies.<String>read(mine, "$.data[0].roleCode")).isEqualTo("OWNER");
        assertThat(JsonBodies.<String>read(mine, "$.data[0].roleName")).isNotBlank();
    }

    @Test
    void cannotActOnAnotherCompany() throws Exception {
        Owner other = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Otra Empresa");
        String foreignMembership = membershipIdOf(other.email(), other.session());

        MvcResult attempt = owner.session().call(HttpMethod.POST,
                "/api/company/members/" + foreignMembership + "/suspend", null);

        assertThat(attempt.getResponse().getStatus()).isEqualTo(404);
    }

    private BrowserSession addMember(String roleCode) throws Exception {
        String email = CustomerFixtures.uniqueEmail();
        owner.session().call(HttpMethod.POST, "/api/company/invitations",
                "{\"email\": \"" + email + "\", \"roleId\": \"" + roleId(roleCode) + "\"}");
        String token = mailbox.awaitToken(email, INVITATION_SUBJECT_ES);
        BrowserSession guest = new BrowserSession(mockMvc);
        guest.postJson(AuthPaths.INVITATION_ACCEPT, """
                {"token": "%s", "firstName": "Ana", "lastName": "López", "password": "%s"}
                """.formatted(token, StaffFixtures.PASSWORD));
        BrowserSession member = new BrowserSession(mockMvc);
        member.postJson(AuthPaths.STAFF_LOGIN, StaffFixtures.loginBody(email));
        String secret = JsonBodies.read(member.postJson(AuthPaths.MFA_SETUP, "{}"), "$.data.secret");
        member.postJson(AuthPaths.MFA_SETUP_CONFIRM, "{\"code\": \"" + TotpCodes.code(secret, 0) + "\"}");
        return member;
    }

    private String roleId(String code) throws Exception {
        List<String> ids = JsonBodies.read(owner.session().get("/api/company/roles"),
                "$.data[?(@.code == '" + code + "')].id");
        return ids.getFirst();
    }

    private String permissionId(String code) throws Exception {
        List<String> ids = JsonBodies.read(owner.session().get("/api/company/permissions"),
                "$.data[*].permissions[?(@.code == '" + code + "')].id");
        return ids.getFirst();
    }

    private String membershipIdOf(String email) throws Exception {
        return membershipIdOf(email, owner.session());
    }

    private String membershipIdOf(String email, BrowserSession session) throws Exception {
        List<String> ids = JsonBodies.read(session.get("/api/company/members"),
                "$.data[?(@.email == '" + email + "')].membershipId");
        return ids.getFirst();
    }

    private String membershipIdOf(BrowserSession member) throws Exception {
        assertThat(member.get("/api/company").getResponse().getStatus()).isEqualTo(200);
        List<String> ids = JsonBodies.read(owner.session().get("/api/company/members"),
                "$.data[?(@.roleCode == 'ADMIN')].membershipId");
        return ids.getFirst();
    }
}
