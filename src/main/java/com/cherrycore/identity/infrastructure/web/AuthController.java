package com.cherrycore.identity.infrastructure.web;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.identity.application.AuthenticateAccountService;
import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.EmailVerificationService;
import com.cherrycore.identity.application.PasswordResetService;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.infrastructure.web.AuthDtos.CsrfResponse;
import com.cherrycore.identity.infrastructure.web.AuthDtos.EmailRequest;
import com.cherrycore.identity.infrastructure.web.AuthDtos.LoginRequest;
import com.cherrycore.identity.infrastructure.web.AuthDtos.LoginResponse;
import com.cherrycore.identity.infrastructure.web.AuthDtos.ResetPasswordRequest;
import com.cherrycore.identity.infrastructure.web.AuthDtos.SessionResponse;
import com.cherrycore.identity.infrastructure.web.AuthDtos.TokenRequest;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.CsrfSettings;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * API del frontend de login. Usa la sesión de CherryCore (cookie) y CSRF.
 *
 * <p>Los endpoints que reciben un email ("reenviar verificación", "olvidé mi
 * contraseña") responden siempre 202, exista o no la cuenta.</p>
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class AuthController {

    private final AuthenticateAccountService authenticateService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final SessionLoginHandler sessionLoginHandler;
    private final AuthEventLogger eventLogger;

    public AuthController(AuthenticateAccountService authenticateService,
            EmailVerificationService emailVerificationService, PasswordResetService passwordResetService,
            SessionLoginHandler sessionLoginHandler, AuthEventLogger eventLogger) {
        this.authenticateService = authenticateService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.sessionLoginHandler = sessionLoginHandler;
        this.eventLogger = eventLogger;
    }

    /**
     * El frontend lo llama al arrancar para recibir la cookie XSRF-TOKEN.
     * Después envía el valor de esa cookie en la cabecera X-XSRF-TOKEN.
     */
    @GetMapping(AuthPaths.CSRF)
    public ApiSuccessResponse<CsrfResponse> csrf(CsrfToken csrfToken) {
        // Leer el token obliga a Spring a generarlo y a enviar la cookie.
        csrfToken.getToken();
        return ApiSuccessResponse.of(new CsrfResponse(CsrfSettings.COOKIE_NAME, CsrfSettings.HEADER_NAME));
    }

    @GetMapping(AuthPaths.SESSION)
    public ApiSuccessResponse<SessionResponse> session(Authentication authentication) {
        if (CurrentAccount.id().isEmpty() || authentication == null) {
            return ApiSuccessResponse.of(SessionResponse.anonymous());
        }
        String accountType = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith(AccountAuthorities.ROLE_PREFIX))
                .map(name -> name.substring(AccountAuthorities.ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);
        return ApiSuccessResponse.of(new SessionResponse(true, accountType));
    }

    @PostMapping(AuthPaths.CUSTOMER_LOGIN)
    public ApiSuccessResponse<LoginResponse> customerLogin(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedAccount account = authenticateService.authenticate(AccountType.CUSTOMER, body.email(),
                body.password(), RequestContextResolver.from(request));
        String redirectUrl = sessionLoginHandler.login(account, request, response);
        return ApiSuccessResponse.of(new LoginResponse(redirectUrl));
    }

    @PostMapping(AuthPaths.LOGOUT)
    public ApiSuccessResponse<Void> logout(HttpServletRequest request) {
        Optional<UUID> accountId = CurrentAccount.id();
        sessionLoginHandler.logout(request);
        accountId.ifPresent(id -> eventLogger.log(AuthEventType.LOGOUT, id, RequestContextResolver.from(request)));
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.CONFIRM_EMAIL)
    public ApiSuccessResponse<Void> confirmEmail(@Valid @RequestBody TokenRequest body, HttpServletRequest request) {
        emailVerificationService.confirm(body.token(), RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.CUSTOMER_RESEND_VERIFICATION)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> resendCustomerVerification(@Valid @RequestBody EmailRequest body) {
        emailVerificationService.resend(AccountType.CUSTOMER, body.email());
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.CUSTOMER_FORGOT_PASSWORD)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> customerForgotPassword(@Valid @RequestBody EmailRequest body,
            HttpServletRequest request) {
        passwordResetService.request(AccountType.CUSTOMER, body.email(), RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.RESET_PASSWORD)
    public ApiSuccessResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body,
            HttpServletRequest request) {
        passwordResetService.reset(body.token(), body.newPassword(), RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }
}
