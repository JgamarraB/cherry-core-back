package com.cherrycore.shared.infrastructure.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Toda la configuración propia de CherryCore, en un único sitio.
 *
 * <p>Los valores se definen en {@code application.yaml} bajo {@code cherrycore:}
 * (y se sobrescriben por perfil o variable de entorno). Si falta alguno
 * obligatorio o es inválido, la aplicación no arranca.</p>
 */
@Validated
@ConfigurationProperties(prefix = CherryCoreProperties.PREFIX)
public record CherryCoreProperties(
        @NotNull URI issuer,
        @NotNull @Valid Frontend frontend,
        @NotNull @Valid Legal legal,
        @NotNull @Valid Customers customers,
        @NotNull @Valid Companies companies,
        @NotNull @Valid I18n i18n,
        @NotNull @Valid Mail mail,
        @NotNull @Valid Security security,
        @NotNull @Valid Cors cors,
        @NotNull @Valid OAuth oauth,
        @NotNull @Valid Branding branding,
        @NotNull @Valid Storage storage,
        @Valid Social social,
        @Valid Bootstrap bootstrap
) {

    public static final String PREFIX = "cherrycore";

    /**
     * URLs del frontend de login. Se usan para redirigir y para los enlaces de los emails.
     */
    public record Frontend(
            @NotNull URI loginUrl,
            @NotNull URI emailVerificationUrl,
            @NotNull URI passwordResetUrl,
            @NotNull URI invitationUrl,
            @NotNull URI emailChangeUrl,
            @NotNull URI adminUrl,
            @NotNull URI socialSignupUrl
    ) {
    }

    /**
     * Versión vigente de los términos y condiciones que acepta el cliente al registrarse.
     */
    public record Legal(@NotBlank String termsVersion) {
    }

    /**
     * Límites del perfil de cliente.
     */
    public record Customers(@Positive int maxAddresses) {
    }

    /**
     * @param invitationTtl validez de las invitaciones de personal
     */
    public record Companies(@NotNull Duration invitationTtl) {
    }

    public record I18n(
            @NotNull Locale defaultLocale,
            @NotEmpty Set<Locale> supportedLocales
    ) {

        @AssertTrue(message = "default-locale must be one of supported-locales")
        public boolean isDefaultSupported() {
            return supportedLocales == null || supportedLocales.contains(defaultLocale);
        }
    }

    public record Mail(@NotBlank @Email String from) {
    }

    /**
     * @param encryptionKey clave AES de 256 bits en Base64 para cifrar secretos
     *                      (MFA). Generarla con: {@code openssl rand -base64 32}
     */
    public record Security(
            @NotBlank String encryptionKey,
            @NotNull @Valid Tokens tokens,
            @NotNull @Valid SigningKey signingKey,
            @NotNull @Valid Password password,
            @NotNull @Valid LoginProtection loginProtection,
            @NotNull @Valid OneTimeTokens oneTimeTokens,
            @NotNull @Valid RateLimit rateLimit,
            @NotNull @Valid Session session
    ) {
    }

    /**
     * Sesión de login de CherryCore (cookie del navegador en el dominio de login).
     *
     * @param secureCookie {@code true} siempre que haya HTTPS (producción)
     * @param sameSite     {@code Lax}: la cookie viaja al volver de otra web por
     *                     un enlace (necesario para el flujo OAuth) pero no en
     *                     peticiones POST de otras webs
     */
    public record Session(
            @NotBlank String cookieName,
            boolean secureCookie,
            @NotBlank String sameSite,
            @NotNull Duration timeout
    ) {
    }

    /**
     * Duración de los tokens emitidos.
     */
    public record Tokens(
            @NotNull Duration accessTokenTtl,
            @NotNull Duration customerRefreshTokenTtl,
            @NotNull Duration staffRefreshTokenTtl,
            @NotNull Duration authorizationCodeTtl
    ) {
    }

    /**
     * Clave RSA con la que se firman los JWT.
     *
     * <p>En producción siempre ficheros PEM (montados como secreto).
     * {@code generateEphemeral} crea una clave en memoria al arrancar: solo
     * para desarrollo y tests, porque al reiniciar se invalidan todos los tokens.</p>
     */
    public record SigningKey(
            @NotBlank String keyId,
            Resource privateKeyLocation,
            Resource publicKeyLocation,
            boolean generateEphemeral
    ) {

        @AssertTrue(message = "signing key: configure PEM locations or enable generate-ephemeral (dev only)")
        public boolean isConfigured() {
            return generateEphemeral || (privateKeyLocation != null && publicKeyLocation != null);
        }
    }

    /**
     * Política de contraseñas (recomendación NIST: longitud, sin reglas de composición).
     */
    public record Password(
            @Min(8) int minLength,
            @Max(256) int maxLength
    ) {

        @AssertTrue(message = "password max-length must be greater than min-length")
        public boolean isRangeValid() {
            return maxLength > minLength;
        }
    }

    /**
     * Bloqueo temporal de una cuenta tras varios intentos fallidos.
     */
    public record LoginProtection(
            @Positive int maxFailedAttempts,
            @NotNull Duration lockDuration
    ) {
    }

    public record OneTimeTokens(
            @NotNull Duration emailVerificationTtl,
            @NotNull Duration passwordResetTtl
    ) {
    }

    /**
     * Límite de peticiones por IP en los endpoints públicos de autenticación.
     */
    public record RateLimit(
            boolean enabled,
            @Positive int capacity,
            @NotNull Duration refillPeriod
    ) {
    }

    /**
     * Orígenes (frontends) que pueden llamar a la API desde el navegador.
     * Nunca se usa {@code *}.
     */
    public record Cors(
            @NotEmpty List<String> allowedOrigins,
            @NotNull Duration maxAge
    ) {

        @AssertTrue(message = "cors allowed-origins must not contain *")
        public boolean isWildcardFree() {
            return allowedOrigins == null || !allowedOrigins.contains("*");
        }
    }

    /**
     * Marca de la plataforma en los emails que no pertenecen a una empresa.
     */
    public record Branding(@NotBlank String platformName) {
    }

    /**
     * Almacenamiento de ficheros (servidor compatible con S3). Cada empresa tiene
     * su carpeta: {@code companies/{companyId}/...}.
     *
     * @param publicBaseUrl URL pública desde la que se sirven los ficheros
     *                      públicos (logos). Normalmente el dominio de media.
     */
    public record Storage(
            @NotNull URI endpoint,
            @NotBlank String accessKey,
            @NotBlank String secretKey,
            @NotBlank String bucket,
            @NotNull URI publicBaseUrl,
            @NotNull DataSize maxImageSize
    ) {
    }

    /**
     * Login social. Google se configura solo con las propiedades estándar de
     * Spring ({@code spring.security.oauth2.client.registration.google}).
     * Apple necesita además los datos para generar su "client secret" firmado.
     */
    public record Social(@Valid Apple apple) {
    }

    /**
     * @param teamId             Team ID de la cuenta de desarrollador de Apple
     * @param keyId              id de la clave "Sign in with Apple"
     * @param privateKeyLocation fichero .p8 de esa clave
     */
    public record Apple(String teamId, String keyId, Resource privateKeyLocation) {

        public boolean isConfigured() {
            return teamId != null && !teamId.isBlank() && keyId != null && !keyId.isBlank()
                    && privateKeyLocation != null;
        }
    }

    /**
     * @param superAdminEmail email del primer superadmin (solo se usa si aún no hay ninguno)
     */
    public record Bootstrap(@Email String superAdminEmail) {
    }

    public record OAuth(@NotNull List<@Valid Client> clients) {
    }

    /**
     * Aplicación cliente registrada en CherryCore (p. ej. el backend de Lirgar).
     *
     * <p>{@code CONFIDENTIAL}: un backend (patrón BFF) con secreto; recibe
     * refresh tokens. {@code PUBLIC}: una app sin secreto (SPA/móvil); sin
     * refresh tokens. PKCE es obligatorio en ambos.</p>
     */
    public record Client(
            @NotBlank String clientId,
            @NotBlank String clientName,
            @NotNull ClientType type,
            String clientSecret,
            @NotNull ClientAudience audience,
            String applicationCode,
            @NotEmpty List<URI> redirectUris,
            List<URI> postLogoutRedirectUris,
            @NotEmpty Set<String> scopes,
            boolean machineAccess
    ) {

        @AssertTrue(message = "staff clients need an application-code (the app whose permissions go in the token)")
        public boolean isApplicationConsistent() {
            return audience != ClientAudience.STAFF || (applicationCode != null && !applicationCode.isBlank());
        }

        @AssertTrue(message = "confidential clients need a client-secret; public clients must not have one")
        public boolean isSecretConsistent() {
            boolean hasSecret = clientSecret != null && !clientSecret.isBlank();
            return type == null || (type == ClientType.CONFIDENTIAL) == hasSecret;
        }
    }

    public enum ClientType {
        CONFIDENTIAL,
        PUBLIC
    }

    /**
     * Tipo de cuenta que puede iniciar sesión en ese cliente.
     */
    public enum ClientAudience {
        CUSTOMER,
        STAFF
    }
}
