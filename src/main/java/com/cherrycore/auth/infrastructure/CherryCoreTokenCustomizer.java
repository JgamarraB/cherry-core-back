package com.cherrycore.auth.infrastructure;

import java.util.ArrayList;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.company.application.TenantAccess;
import com.cherrycore.customer.application.CustomerProfile;
import com.cherrycore.customer.application.CustomerProfileService;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.PlatformRole;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.TokenClaims;

/**
 * Contenido de los JWT que emite CherryCore.
 *
 * <ul>
 *   <li><b>sub</b>: id (UUID) de la cuenta. <b>account_type</b>: CUSTOMER / STAFF.</li>
 *   <li>Backoffice: <b>tenant_id</b>, <b>tenant_name</b>, <b>tenant_role</b> y
 *       <b>permissions</b> (los de esa app en esa empresa).</li>
 *   <li>ID token: email y nombre según los scopes pedidos.</li>
 * </ul>
 *
 * <p>Se ejecuta también en cada refresh: si la cuenta se ha desactivado, ya no
 * es miembro de la empresa, la empresa está suspendida o la app desactivada,
 * no se emiten más tokens. Un cliente de clientes finales nunca recibe tokens
 * de backoffice (ni al revés).</p>
 */
@Component
class CherryCoreTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final String ACCOUNT_INACTIVE = "Account is not active";
    private static final String AUDIENCE_MISMATCH = "Account type not allowed for this client";
    private static final String NO_TENANT = "No company selected";
    private static final String NO_TENANT_ACCESS = "No access to this application in the selected company";

    private final AccountQueryService accountQuery;
    private final CustomerProfileService customerProfiles;
    private final StaffTenantService tenants;

    CherryCoreTokenCustomizer(AccountQueryService accountQuery, CustomerProfileService customerProfiles,
            StaffTenantService tenants) {
        this.accountQuery = accountQuery;
        this.customerProfiles = customerProfiles;
        this.tenants = tenants;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            // Token de máquina (backend de una app): sin usuario, solo la app.
            Object application = context.getRegisteredClient().getClientSettings()
                    .getSetting(ClientSettingNames.APPLICATION);
            if (application != null) {
                context.getClaims().claim(TokenClaims.APPLICATION, application.toString());
            }
            return;
        }
        Account account = activeAccount(context.getPrincipal());
        checkAudience(context.getRegisteredClient(), account);
        TenantAccess tenantAccess = account.type() == AccountType.STAFF
                ? tenantAccess(context.getPrincipal(), context.getRegisteredClient(), account)
                : null;

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claim(TokenClaims.ACCOUNT_TYPE, account.type().name());
            if (tenantAccess != null) {
                context.getClaims()
                        .claim(TokenClaims.TENANT_ID, tenantAccess.companyId().toString())
                        .claim(TokenClaims.TENANT_NAME, tenantAccess.companyName())
                        .claim(TokenClaims.TENANT_ROLE, tenantAccess.roleCode())
                        // ArrayList: es un tipo permitido al guardar la autorización en BD (JSON con lista blanca).
                        .claim(TokenClaims.PERMISSIONS, new ArrayList<>(tenantAccess.permissions()));
            }
            if (account.platformRole() == PlatformRole.SUPER_ADMIN) {
                context.getClaims().claim(TokenClaims.PLATFORM_ROLE, PlatformRole.SUPER_ADMIN.name());
            }
        } else if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            addIdentityClaims(context, account);
            if (tenantAccess != null) {
                context.getClaims().claim(TokenClaims.TENANT_ID, tenantAccess.companyId().toString());
            }
        }
    }

    private Account activeAccount(Authentication principal) {
        return accountQuery.findActive(UUID.fromString(principal.getName()))
                .orElseThrow(() -> oauthError(OAuth2ErrorCodes.INVALID_GRANT, ACCOUNT_INACTIVE));
    }

    private static void checkAudience(RegisteredClient client, Account account) {
        Object audience = client.getClientSettings().getSetting(ClientSettingNames.AUDIENCE);
        if (!account.type().name().equals(audience)) {
            throw oauthError(OAuth2ErrorCodes.ACCESS_DENIED, AUDIENCE_MISMATCH);
        }
    }

    private TenantAccess tenantAccess(Authentication principal, RegisteredClient client, Account account) {
        UUID companyId = AccountAuthorities.tenantOf(principal.getAuthorities())
                .orElseThrow(() -> oauthError(OAuth2ErrorCodes.ACCESS_DENIED, NO_TENANT));
        String applicationCode = client.getClientSettings().getSetting(ClientSettingNames.APPLICATION);
        return tenants.accessFor(account.id(), companyId, applicationCode)
                .orElseThrow(() -> oauthError(OAuth2ErrorCodes.ACCESS_DENIED, NO_TENANT_ACCESS));
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
