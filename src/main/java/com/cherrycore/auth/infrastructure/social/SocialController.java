package com.cherrycore.auth.infrastructure.social;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.auth.infrastructure.web.LoginDtos.LoginResponse;
import com.cherrycore.auth.infrastructure.web.LoginFlow;
import com.cherrycore.auth.infrastructure.web.PrimaryFactor;
import com.cherrycore.customer.application.CustomerSocialSignupService;
import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.identity.domain.SocialProfile;
import com.cherrycore.identity.domain.SocialProvider;
import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Login social para el frontend de CherryCore.
 *
 * <ol>
 *   <li>{@code GET /providers}: qué botones mostrar.</li>
 *   <li>Navegar a {@code /{provider}/start?type=CUSTOMER|STAFF}.</li>
 *   <li>Si es un cliente nuevo, vuelve a la pantalla de completar registro:
 *       {@code GET /pending} (datos para rellenar) y {@code POST /complete}.</li>
 * </ol>
 */
@RestController
@RequestMapping(AuthPaths.BASE + SocialController.BASE)
public class SocialController {

    static final String BASE = "/social";
    static final String PROVIDERS = "/providers";
    static final String START = "/{provider}/start";
    static final String PENDING = "/pending";
    static final String COMPLETE = "/complete";
    private static final String TYPE_PARAM = "type";
    private static final int MAX_PHONE_INPUT = 30;

    private final SocialClientRegistrations registrations;
    private final CustomerSocialSignupService signup;
    private final LoginFlow loginFlow;

    SocialController(SocialClientRegistrations registrations, CustomerSocialSignupService signup,
            LoginFlow loginFlow) {
        this.registrations = registrations;
        this.signup = signup;
        this.loginFlow = loginFlow;
    }

    @GetMapping(PROVIDERS)
    public ApiSuccessResponse<List<String>> providers() {
        return ApiSuccessResponse.of(registrations.registrationIds().stream()
                .filter(id -> SocialProvider.fromRegistrationId(id).isPresent())
                .toList());
    }

    /**
     * Redirige al proveedor. El tipo de cuenta se guarda en la sesión para la vuelta.
     */
    @GetMapping(START)
    public void start(@PathVariable String provider, @RequestParam(TYPE_PARAM) AccountType type,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        SocialProvider socialProvider = SocialProvider.fromRegistrationId(provider)
                .filter(candidate -> registrations.findByRegistrationId(candidate.registrationId()) != null)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.SOCIAL_PROVIDER_NOT_AVAILABLE));
        request.getSession(true).setAttribute(SocialSessionAttributes.ACCOUNT_TYPE, type);
        response.sendRedirect(request.getContextPath() + SocialLoginConfig.AUTHORIZATION_BASE + "/"
                + socialProvider.registrationId());
    }

    @GetMapping(PENDING)
    public ApiSuccessResponse<PendingSignupView> pending(HttpServletRequest request) {
        SocialProfile profile = pendingProfile(request);
        return ApiSuccessResponse.of(new PendingSignupView(profile.provider().registrationId(), profile.email(),
                profile.givenName(), profile.familyName()));
    }

    @PostMapping(COMPLETE)
    public ApiSuccessResponse<LoginResponse> complete(@Valid @RequestBody CompleteSignupRequest body, Locale locale,
            HttpServletRequest request, HttpServletResponse response) {
        SocialProfile profile = pendingProfile(request);
        AuthenticatedAccount account = signup.complete(profile, body.firstName(), body.lastName(), body.phone(),
                Boolean.TRUE.equals(body.acceptTerms()), locale, RequestContextResolver.from(request));
        request.getSession().removeAttribute(SocialSessionAttributes.PENDING_SIGNUP);
        return ApiSuccessResponse.of(loginFlow.afterPrimaryFactor(account, PrimaryFactor.SOCIAL, request, response));
    }

    private static SocialProfile pendingProfile(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(SocialSessionAttributes.PENDING_SIGNUP)
                instanceof SocialProfile profile) {
            return profile;
        }
        throw new DomainException(IdentityErrorCode.NO_PENDING_SOCIAL_SIGNUP);
    }

    public record PendingSignupView(String provider, String email, String firstName, String lastName) {
    }

    public record CompleteSignupRequest(
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String firstName,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @NotNull Boolean acceptTerms
    ) {
    }
}
