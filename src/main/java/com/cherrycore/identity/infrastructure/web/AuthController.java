package com.cherrycore.identity.infrastructure.web;


import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.identity.application.EmailVerificationService;
import com.cherrycore.identity.application.PasswordResetService;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.infrastructure.web.AuthDtos.CsrfResponse;
import com.cherrycore.identity.infrastructure.web.AuthDtos.EmailRequest;
import com.cherrycore.identity.infrastructure.web.AuthDtos.ResetPasswordRequest;
import com.cherrycore.identity.infrastructure.web.AuthDtos.TokenRequest;
import com.cherrycore.shared.infrastructure.security.CsrfSettings;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * API pública de cuenta: CSRF, verificación de email y contraseña olvidada.
 * El login está en {@code LoginController} (módulo auth). Usa cookie y CSRF.
 *
 * <p>Los endpoints que reciben un email ("reenviar verificación", "olvidé mi
 * contraseña") responden siempre 202, exista o no la cuenta.</p>
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class AuthController {

    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public AuthController(EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService) {
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
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

    @PostMapping(AuthPaths.STAFF_RESEND_VERIFICATION)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> resendStaffVerification(@Valid @RequestBody EmailRequest body) {
        emailVerificationService.resend(AccountType.STAFF, body.email());
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.STAFF_FORGOT_PASSWORD)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> staffForgotPassword(@Valid @RequestBody EmailRequest body,
            HttpServletRequest request) {
        passwordResetService.request(AccountType.STAFF, body.email(), RequestContextResolver.from(request));
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
