package com.cherrycore.customer.infrastructure.web;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.customer.application.RegisterCustomerCommand;
import com.cherrycore.customer.application.RegisterCustomerService;
import com.cherrycore.customer.infrastructure.web.CustomerDtos.RegisterRequest;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Registro de cliente final. Responde 202 tanto si se crea la cuenta como si
 * el email ya existía (en ese caso se avisa al dueño por email).
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class CustomerRegistrationController {

    private final RegisterCustomerService registerService;

    public CustomerRegistrationController(RegisterCustomerService registerService) {
        this.registerService = registerService;
    }

    @PostMapping(AuthPaths.CUSTOMER_REGISTER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> register(@Valid @RequestBody RegisterRequest body, Locale locale,
            HttpServletRequest request) {
        registerService.register(new RegisterCustomerCommand(body.email(), body.password(), body.firstName(),
                body.lastName(), body.phone(), Boolean.TRUE.equals(body.acceptTerms()), locale),
                RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }
}
