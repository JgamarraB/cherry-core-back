package com.cherrycore.auth.infrastructure.social;

import java.time.Clock;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.security.SecurityHeadersCustomizer;

/**
 * CherryCore como cliente OIDC de Google y Apple.
 *
 * <p>Los proveedores se activan con las propiedades estándar de Spring
 * ({@code spring.security.oauth2.client.registration.*}). Sin ellas no hay
 * login social y los endpoints responden que no está disponible.</p>
 */
@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class SocialLoginConfig {

    public static final int SOCIAL_CHAIN_ORDER = 1;
    public static final String AUTHORIZATION_BASE = "/oauth2/authorization";
    public static final String CALLBACK_PATTERN = "/login/oauth2/code/*";

    private static final String RESPONSE_MODE = "response_mode";
    private static final String FORM_POST = "form_post";

    @Bean
    SocialClientRegistrations clientRegistrationRepository(OAuth2ClientProperties properties) {
        Map<String, ClientRegistration> registrations = new OAuth2ClientPropertiesMapper(properties)
                .asClientRegistrations();
        return new SocialClientRegistrations(registrations);
    }

    @Bean
    @Order(SOCIAL_CHAIN_ORDER)
    SecurityFilterChain socialLoginSecurityFilterChain(HttpSecurity http, SocialClientRegistrations registrations,
            SocialLoginHandlers handlers, CherryCoreProperties properties, SecurityHeadersCustomizer securityHeaders,
            Clock clock) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, AUTHORIZATION_BASE);
        // Apple exige response_mode=form_post para devolver el email.
        resolver.setAuthorizationRequestCustomizer(request -> request.attributes(attributes -> {
            if (SocialProvider.APPLE.registrationId().equals(attributes.get(OAuth2ParameterNames.REGISTRATION_ID))) {
                request.additionalParameters(parameters -> parameters.put(RESPONSE_MODE, FORM_POST));
            }
        }));

        RestClientAuthorizationCodeTokenResponseClient tokenClient = new RestClientAuthorizationCodeTokenResponseClient();
        ClientRegistration apple = registrations.findByRegistrationId(SocialProvider.APPLE.registrationId());
        CherryCoreProperties.Apple appleSettings = properties.social() == null ? null : properties.social().apple();
        if (apple != null && appleSettings != null && appleSettings.isConfigured()) {
            AppleClientSecret appleSecret = new AppleClientSecret(appleSettings, apple.getClientId(), clock);
            tokenClient.setParametersCustomizer(parameters -> {
                if (apple.getClientId().equals(parameters.getFirst(OAuth2ParameterNames.CLIENT_ID))) {
                    parameters.set(OAuth2ParameterNames.CLIENT_SECRET, appleSecret.current());
                }
            });
        }

        return http
                .securityMatcher(AUTHORIZATION_BASE + "/*", CALLBACK_PATTERN)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2Login(login -> login
                        .clientRegistrationRepository(registrations)
                        .authorizationEndpoint(endpoint -> endpoint.baseUri(AUTHORIZATION_BASE)
                                .authorizationRequestResolver(resolver))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_PATTERN))
                        .tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(tokenClient))
                        .successHandler(handlers)
                        .failureHandler(handlers))
                // Apple (form_post): POST entre sitios -> GET con la cookie de sesión.
                .addFilterBefore(new FormPostCallbackRedirectFilter(), OAuth2LoginAuthenticationFilter.class)
                // La sesión de Google/Apple nunca se guarda como sesión de CherryCore.
                .securityContext(context -> context.securityContextRepository(
                        new RequestAttributeSecurityContextRepository()))
                // El parámetro "state" de OAuth protege la vuelta contra CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(securityHeaders::apply)
                .build();
    }
}
