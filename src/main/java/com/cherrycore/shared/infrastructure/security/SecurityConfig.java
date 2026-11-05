package com.cherrycore.shared.infrastructure.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.web.ApiErrorWriter;

/**
 * Cadenas de seguridad de la API (la del Authorization Server está en
 * {@code AuthorizationServerConfig} y va primero).
 *
 * <ol>
 *   <li><b>API del frontend de CherryCore</b> ({@code /api/auth}, {@code /api/account},
 *       {@code /api/company}, {@code /api/admin}): sesión con cookie + CSRF.
 *       Login con límite de peticiones; admin solo superadmin; company solo backoffice.</li>
 *   <li><b>Resto de la API</b>: token Bearer (JWT) obligatorio, sin sesión
 *       ni cookies (por eso no aplica CSRF). Denegar por defecto.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final int AUTH_API_ORDER = 2;
    public static final int RESOURCE_API_ORDER = 3;

    private static final List<String> CORS_ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name());

    private static final List<String> CORS_ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT_LANGUAGE, CsrfSettings.HEADER_NAME);

    @Bean
    @Order(AUTH_API_ORDER)
    SecurityFilterChain authApiSecurityFilterChain(HttpSecurity http, CherryCoreProperties properties,
            ApiErrorWriter errorWriter, JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler, SecurityHeadersCustomizer securityHeaders) throws Exception {
        http
                .securityMatcher(SecurityPaths.AUTH_API, SecurityPaths.ACCOUNT_API, SecurityPaths.COMPANY_API_ROOT,
                        SecurityPaths.COMPANY_API, SecurityPaths.ADMIN_API)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityPaths.AUTH_API).permitAll()
                        .requestMatchers(SecurityPaths.ADMIN_API)
                        .hasRole(AccountAuthorities.SUPER_ADMIN)
                        .requestMatchers(SecurityPaths.COMPANY_API_ROOT, SecurityPaths.COMPANY_API)
                        .hasRole(AccountAuthorities.STAFF)
                        .anyRequest().authenticated())
                // Cookie XSRF-TOKEN + cabecera X-XSRF-TOKEN (Angular lo hace solo).
                .csrf(csrf -> csrf.spa())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(
                        new HttpSessionSecurityContextRepository()))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(securityHeaders::apply);

        CherryCoreProperties.RateLimit rateLimit = properties.security().rateLimit();
        if (rateLimit.enabled()) {
            http.addFilterBefore(new RateLimitFilter(rateLimit, errorWriter), CsrfFilter.class);
        }
        return http.build();
    }

    @Bean
    @Order(RESOURCE_API_ORDER)
    SecurityFilterChain resourceApiSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
            JsonAuthenticationEntryPoint authenticationEntryPoint, JsonAccessDeniedHandler accessDeniedHandler,
            SecurityHeadersCustomizer securityHeaders) throws Exception {
        return http
                .securityMatcher(SecurityPaths.ALL_PATHS)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, SecurityPaths.HEALTH).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(new JwtAccountAuthenticationConverter()))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .headers(securityHeaders::apply)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CherryCoreProperties properties) {
        CherryCoreProperties.Cors cors = properties.cors();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(cors.allowedOrigins());
        configuration.setAllowedMethods(CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(cors.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(SecurityPaths.ALL_PATHS, configuration);
        return source;
    }
}
