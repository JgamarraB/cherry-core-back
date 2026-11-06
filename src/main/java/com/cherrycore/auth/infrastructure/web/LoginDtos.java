package com.cherrycore.auth.infrastructure.web;

import java.util.List;
import java.util.UUID;

import com.cherrycore.company.application.TenantOption;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.infrastructure.web.AuthDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LoginDtos {

    public static final int MAX_CODE_INPUT = 32;

    private LoginDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = EmailAddress.MAX_LENGTH) String email,
            @NotBlank @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String password
    ) {

        @Override
        public String toString() {
            return "LoginRequest[***]";
        }
    }

    /**
     * @param redirectUrl solo con AUTHENTICATED: a dónde debe ir el navegador
     *                    (la app que pidió el login), o {@code null}
     */
    public record LoginResponse(LoginStatus status, String redirectUrl) {
    }

    public record CodeRequest(@NotBlank @Size(max = MAX_CODE_INPUT) String code) {
    }

    public record MfaSetupConfirmed(List<String> recoveryCodes, LoginResponse login) {
    }

    public record TenantRequest(@NotNull UUID companyId) {
    }

    public record TenantsResponse(UUID activeCompanyId, List<TenantOption> tenants) {
    }

    /**
     * @param accountId   cuenta con sesión (para marcar "tú" en listados); null sin sesión
     * @param pendingStep si hay un login a medias: MFA_REQUIRED o MFA_SETUP_REQUIRED
     */
    public record SessionResponse(
            boolean authenticated,
            UUID accountId,
            String accountType,
            boolean superAdmin,
            UUID activeCompanyId,
            LoginStatus pendingStep
    ) {

        public static SessionResponse anonymous(LoginStatus pendingStep) {
            return new SessionResponse(false, null, null, false, null, pendingStep);
        }
    }
}
