package com.cherrycore.company.infrastructure.web;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.company.application.AcceptInvitationCommand;
import com.cherrycore.company.application.CompanyRegistrationService;
import com.cherrycore.company.application.InvitationInfo;
import com.cherrycore.company.application.RegisterCompanyCommand;
import com.cherrycore.company.application.StaffInvitationService;
import com.cherrycore.company.infrastructure.web.CompanyDtos.AcceptInvitationRequest;
import com.cherrycore.company.infrastructure.web.CompanyDtos.InvitationTokenRequest;
import com.cherrycore.company.infrastructure.web.CompanyDtos.RegisterCompanyRequest;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Endpoints públicos de empresas: alta y aceptación de invitaciones.
 * El token de invitación va en el cuerpo, no en la URL (no queda en logs).
 */
@RestController
@RequestMapping(AuthPaths.BASE)
public class CompanyAuthController {

    private final CompanyRegistrationService registration;
    private final StaffInvitationService invitations;

    public CompanyAuthController(CompanyRegistrationService registration, StaffInvitationService invitations) {
        this.registration = registration;
        this.invitations = invitations;
    }

    /**
     * 202 siempre que los datos sean válidos (también si el email ya tenía
     * cuenta: en ese caso se avisa al dueño por email).
     */
    @PostMapping(AuthPaths.COMPANY_REGISTER)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiSuccessResponse<Void> register(@Valid @RequestBody RegisterCompanyRequest body, Locale locale,
            HttpServletRequest request) {
        registration.registerWithNewOwner(new RegisterCompanyCommand(body.company().toCommand(),
                body.owner().email(), body.owner().password(), body.owner().firstName(), body.owner().lastName(),
                body.owner().phone(), Boolean.TRUE.equals(body.acceptTerms()), locale),
                RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @PostMapping(AuthPaths.INVITATION_LOOKUP)
    public ApiSuccessResponse<InvitationInfo> lookup(@Valid @RequestBody InvitationTokenRequest body) {
        return ApiSuccessResponse.of(invitations.info(body.token()));
    }

    @PostMapping(AuthPaths.INVITATION_ACCEPT)
    public ApiSuccessResponse<Void> accept(@Valid @RequestBody AcceptInvitationRequest body, Locale locale,
            HttpServletRequest request) {
        Optional<UUID> loggedIn = CurrentAccount.id();
        if (loggedIn.isPresent()) {
            invitations.acceptWithExistingAccount(body.token(), loggedIn.get(), RequestContextResolver.from(request));
        } else {
            invitations.acceptWithNewAccount(new AcceptInvitationCommand(body.token(), body.firstName(),
                    body.lastName(), body.phone(), body.password(), locale), RequestContextResolver.from(request));
        }
        return ApiSuccessResponse.of(null);
    }
}
