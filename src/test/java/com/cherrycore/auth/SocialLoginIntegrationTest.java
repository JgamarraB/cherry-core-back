package com.cherrycore.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.cherrycore.auth.infrastructure.social.SocialController;
import com.cherrycore.company.application.CompanyReviewService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.application.SocialLoginResult;
import com.cherrycore.identity.application.SocialLoginService;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.SocialProfile;
import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.support.BrowserSession;
import com.cherrycore.support.CapturingEmailSender;
import com.cherrycore.support.CustomerFixtures;
import com.cherrycore.support.IntegrationTest;
import com.cherrycore.support.JsonBodies;
import com.cherrycore.support.StaffFixtures;

@IntegrationTest
class SocialLoginIntegrationTest {

    private static final RequestContext CONTEXT = new RequestContext("127.0.0.1", "tests");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocialLoginService socialLogin;

    @Autowired
    private CapturingEmailSender mailbox;

    @Autowired
    private CompanyReviewService review;

    @Autowired
    @Qualifier("socialLoginHandlers")
    private AuthenticationSuccessHandler successHandler;

    @Autowired
    private SocialController socialController;

    @Test
    void listsConfiguredProvidersAndRedirectsToThem() throws Exception {
        BrowserSession browser = new BrowserSession(mockMvc);
        assertThat(JsonBodies.<List<String>>read(browser.get("/api/auth/social/providers"), "$.data"))
                .containsExactly("google");

        MvcResult start = browser.get("/api/auth/social/google/start?type=CUSTOMER");
        assertThat(start.getResponse().getRedirectedUrl()).isEqualTo("/oauth2/authorization/google");

        MvcResult toGoogle = browser.get("/oauth2/authorization/google");
        assertThat(toGoogle.getResponse().getRedirectedUrl())
                .startsWith("https://accounts.example.test/o/oauth2/auth")
                .contains("client_id=test-google-client", "state=", "nonce=",
                        "redirect_uri=http://localhost/login/oauth2/code/google");

        MvcResult unknown = browser.get("/api/auth/social/apple/start?type=CUSTOMER");
        assertThat(JsonBodies.<String>read(unknown, "$.error.code")).isEqualTo("SOCIAL_PROVIDER_NOT_AVAILABLE");
    }

    @Test
    void newCustomerMustCompleteSignupAndThenSignsIn() throws Exception {
        String email = CustomerFixtures.uniqueEmail();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response, googleToken("g-" + UUID.randomUUID(), email, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/register/complete");
        assertThat(socialController.pending(request).data().email()).isEqualTo(email);

        var login = socialController.complete(new SocialController.CompleteSignupRequest("Lucía", "Gil", null, true),
                Locale.forLanguageTag("es"), request, new MockHttpServletResponse());
        assertThat(login.data().status().name()).isEqualTo("AUTHENTICATED");

        // La cuenta no tiene contraseña: no se puede entrar con email + contraseña.
        assertThat(new BrowserSession(mockMvc).login(email, CustomerFixtures.PASSWORD).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void existingAccountIsLinkedOnlyWhenTheProviderVerifiedTheEmail() throws Exception {
        String email = CustomerFixtures.registerVerified(new BrowserSession(mockMvc), mailbox);
        String subject = "g-" + UUID.randomUUID();

        assertThatThrownBy(() -> socialLogin.signIn(profile(subject, email, false), AccountType.CUSTOMER, CONTEXT))
                .isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode().code())
                .isEqualTo("SOCIAL_EMAIL_NOT_VERIFIED");

        assertThat(socialLogin.signIn(profile(subject, email, true), AccountType.CUSTOMER, CONTEXT))
                .isInstanceOf(SocialLoginResult.SignedIn.class);
        // Después entra por el id del proveedor aunque el email haya cambiado en Google.
        assertThat(socialLogin.signIn(profile(subject, "otro-" + email, false), AccountType.CUSTOMER, CONTEXT))
                .isInstanceOf(SocialLoginResult.SignedIn.class);
    }

    @Test
    void staffAccountsAreNeverCreatedBySocialLoginAndStillNeedMfa() throws Exception {
        assertThatThrownBy(() -> socialLogin.signIn(profile("g-" + UUID.randomUUID(),
                CustomerFixtures.uniqueEmail(), true), AccountType.STAFF, CONTEXT))
                .extracting(error -> ((DomainException) error).getErrorCode().code())
                .isEqualTo("SOCIAL_ACCOUNT_NOT_FOUND");

        StaffFixtures.Owner owner = StaffFixtures.activeCompany(mockMvc, mailbox, review, "Social Staff");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("cherrycore.social.accountType", AccountType.STAFF);
        MockHttpServletResponse response = new MockHttpServletResponse();

        successHandler.onAuthenticationSuccess(request, response,
                googleToken("g-" + UUID.randomUUID(), owner.email(), true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/login?status=MFA_REQUIRED");
    }

    private static SocialProfile profile(String subject, String email, boolean verified) {
        return new SocialProfile(SocialProvider.GOOGLE, subject, email, verified, "Ana", "Pérez");
    }

    private static OAuth2AuthenticationToken googleToken(String subject, String email, boolean verified) {
        DefaultOAuth2User user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                Map.of("sub", subject, "email", email, "email_verified", verified, "given_name", "Ana",
                        "family_name", "Pérez"), "sub");
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }
}
