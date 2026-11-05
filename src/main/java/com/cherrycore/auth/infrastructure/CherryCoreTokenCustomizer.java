package com.cherrycore.auth.infrastructure;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import com.cherrycore.customer.application.CustomerProfile;
import com.cherrycore.customer.application.CustomerProfileService;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;

/**
 * Contenido de los JWT que emite CherryCore.
 *
 * <ul>
 *   <li><b>sub</b>: id (UUID) de la cuenta.</li>
 *   <li><b>account_type</b>: CUSTOMER / STAFF (en el access token).</li>
 *   <li>ID token: email y nombre según los scopes pedidos.</li>
 * </ul>
 *
 * <p>Se ejecuta también en cada refresh: si la cuenta se ha desactivado o
 * borrado, no se emiten más tokens. Y un cliente de clientes finales nunca
 * recibe tokens de una cuenta de backoffice (ni al revés).</p>
 */
@Component
class CherryCoreTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final String ACCOUNT_INACTIVE = "Account is not active";
    private static final String AUDIENCE_MISMATCH = "Account type not allowed for this client";

    private final AccountQueryService accountQuery;
    private final CustomerProfileService customerProfiles;

    CherryCoreTokenCustomizer(AccountQueryService accountQuery, CustomerProfileService customerProfiles) {
        this.accountQuery = accountQuery;
        this.customerProfiles = customerProfiles;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        Account account = activeAccount(context.getPrincipal());
        checkAudience(context, account);

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claim(AccountAuthorities.ACCOUNT_TYPE_CLAIM, account.type().name());
        } else if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            addIdentityClaims(context, account);
        }
    }

    private Account activeAccount(Authentication principal) {
        return accountQuery.findActive(UUID.fromString(principal.getName()))
                .orElseThrow(() -> oauthError(OAuth2ErrorCodes.INVALID_GRANT, ACCOUNT_INACTIVE));
    }

    private static void checkAudience(JwtEncodingContext context, Account account) {
        Object audience = context.getRegisteredClient().getClientSettings().getSetting(ClientSettingNames.AUDIENCE);
        if (!account.type().name().equals(audience)) {
            throw oauthError(OAuth2ErrorCodes.ACCESS_DENIED, AUDIENCE_MISMATCH);
        }
    }

    private void addIdentityClaims(JwtEncodingContext context, Account account) {
        if (context.getAuthorizedScopes().contains(OidcScopes.EMAIL)) {
            context.getClaims()
                    .claim(StandardClaimNames.EMAIL, account.email().value())
                    .claim(StandardClaimNames.EMAIL_VERIFIED, account.emailVerifiedAt() != null);
        }
        if (context.getAuthorizedScopes().contains(OidcScopes.PROFILE)) {
            context.getClaims().claim(StandardClaimNames.LOCALE, account.locale().toLanguageTag());
            if (account.type() == AccountType.CUSTOMER) {
                CustomerProfile profile = customerProfiles.get(account.id());
                context.getClaims()
                        .claim(StandardClaimNames.GIVEN_NAME, profile.firstName())
                        .claim(StandardClaimNames.FAMILY_NAME, profile.lastName());
            }
        }
    }

    private static OAuth2AuthenticationException oauthError(String errorCode, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(errorCode, description, null));
    }
}
