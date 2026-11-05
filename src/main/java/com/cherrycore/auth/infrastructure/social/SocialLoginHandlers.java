package com.cherrycore.auth.infrastructure.social;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginResponse;
import com.cherrycore.auth.infrastructure.web.LoginFlow;
import com.cherrycore.auth.infrastructure.web.LoginStatus;
import com.cherrycore.auth.infrastructure.web.PrimaryFactor;
import com.cherrycore.identity.application.SocialLoginResult;
import com.cherrycore.identity.application.SocialLoginService;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.SocialProfile;
import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Vuelta de Google/Apple. Es una redirección del navegador (no JSON), así que
 * el resultado se comunica redirigiendo:
 * <ul>
 *   <li>Login completo y venía de una app: a la app (flujo OAuth).</li>
 *   <li>Falta MFA o elegir empresa: al login con {@code ?status=...}.</li>
 *   <li>Cliente nuevo: a la pantalla de completar registro.</li>
 *   <li>Error: al login con {@code ?error=CODIGO}.</li>
 * </ul>
 */
@Component
class SocialLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocialLoginHandlers.class);
    private static final String LOG_SOCIAL_FAILURE = "Social login failed [{}]";
    private static final String STATUS_PARAM = "status";
    private static final String ERROR_PARAM = "error";
    private static final String SOCIAL_LOGIN_FAILED = "SOCIAL_LOGIN_FAILED";
    private static final String TRUE = "true";
    /** Claim de Google con el dominio de Google Workspace de la cuenta. */
    private static final String GOOGLE_HOSTED_DOMAIN = "hd";
    private static final Set<String> GMAIL_DOMAINS = Set.of("gmail.com", "googlemail.com");
    private static final char EMAIL_DOMAIN_SEPARATOR = '@';

    private final SocialLoginService socialLogin;
    private final LoginFlow loginFlow;
    private final CherryCoreProperties.Frontend frontend;

    SocialLoginHandlers(SocialLoginService socialLogin, LoginFlow loginFlow, CherryCoreProperties properties) {
        this.socialLogin = socialLogin;
        this.loginFlow = loginFlow;
        this.frontend = properties.frontend();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        // La autenticación OAuth2 de Google/Apple NO es una sesión de CherryCore.
        SecurityContextHolder.clearContext();
        try {
            SocialProfile profile = profileOf((OAuth2AuthenticationToken) authentication);
            SocialLoginResult result = socialLogin.signIn(profile, requestedType(request),
                    RequestContextResolver.from(request));
            switch (result) {
                case SocialLoginResult.SignedIn signedIn -> redirectAfterLogin(loginFlow.afterPrimaryFactor(
                        signedIn.account(), PrimaryFactor.SOCIAL, request, response), response);
                case SocialLoginResult.SignupRequired signup -> {
                    request.getSession().setAttribute(SocialSessionAttributes.PENDING_SIGNUP, signup.profile());
                    response.sendRedirect(frontend.socialSignupUrl().toString());
                }
            }
        } catch (DomainException refused) {
            redirectToLogin(response, ERROR_PARAM, refused.getErrorCode().code());
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        LOGGER.warn(LOG_SOCIAL_FAILURE, exception.getClass().getSimpleName());
        redirectToLogin(response, ERROR_PARAM, SOCIAL_LOGIN_FAILED);
    }

    private void redirectAfterLogin(LoginResponse login, HttpServletResponse response) throws IOException {
        if (login.status() == LoginStatus.AUTHENTICATED && login.redirectUrl() != null) {
            response.sendRedirect(login.redirectUrl());
        } else {
            redirectToLogin(response, STATUS_PARAM, login.status().name());
        }
    }

    private void redirectToLogin(HttpServletResponse response, String param, String value) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUri(frontend.loginUrl()).queryParam(param, value).build()
                .toUriString());
    }

    private static AccountType requestedType(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object type = session == null ? null : session.getAttribute(SocialSessionAttributes.ACCOUNT_TYPE);
        if (session != null) {
            session.removeAttribute(SocialSessionAttributes.ACCOUNT_TYPE);
        }
        return type instanceof AccountType accountType ? accountType : AccountType.CUSTOMER;
    }

    /*
     * Google solo garantiza que el email es HOY de esa persona si es una
     * cuenta de Gmail o de un dominio de Google Workspace (claim "hd"). Una
     * cuenta de Google creada con otro email (p. ej. @empresa.com) lo verificó
     * una vez, pero quien lo tenía entonces puede no ser quien lo tiene ahora:
     * se trata como email NO verificado (no se vincula ni se crea cuenta con él).
     */
    static boolean googleIsAuthoritative(String email, Map<String, Object> attributes) {
        Object hostedDomain = attributes.get(GOOGLE_HOSTED_DOMAIN);
        if (hostedDomain instanceof String domain && !domain.isBlank()) {
            return true;
        }
        if (email == null) {
            return false;
        }
        int at = email.lastIndexOf(EMAIL_DOMAIN_SEPARATOR);
        return at >= 0 && GMAIL_DOMAINS.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }

    static SocialProfile profileOf(OAuth2AuthenticationToken token) {
        SocialProvider provider = SocialProvider.fromRegistrationId(token.getAuthorizedClientRegistrationId())
                .orElseThrow(() -> new DomainException(IdentityErrorCode.SOCIAL_PROVIDER_NOT_AVAILABLE));
        OAuth2User user = token.getPrincipal();
        Map<String, Object> attributes = user.getAttributes();
        Object verified = attributes.get(StandardClaimNames.EMAIL_VERIFIED);
        String email = (String) attributes.get(StandardClaimNames.EMAIL);
        // Apple envía email_verified como texto ("true"), Google como booleano.
        boolean emailVerified = (Boolean.TRUE.equals(verified) || TRUE.equals(String.valueOf(verified)))
                && (provider != SocialProvider.GOOGLE || googleIsAuthoritative(email, attributes));
        return new SocialProfile(provider,
                String.valueOf(attributes.get(StandardClaimNames.SUB)),
                email,
                emailVerified,
                (String) attributes.get(StandardClaimNames.GIVEN_NAME),
                (String) attributes.get(StandardClaimNames.FAMILY_NAME));
    }
}
