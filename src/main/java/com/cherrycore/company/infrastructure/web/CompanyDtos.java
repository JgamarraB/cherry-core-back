package com.cherrycore.company.infrastructure.web;

import java.util.Set;
import java.util.UUID;

import com.cherrycore.company.application.CompanyDataCommand;
import com.cherrycore.company.domain.CompanyName;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.infrastructure.web.AuthDtos;
import com.cherrycore.shared.domain.value.PersonName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CompanyDtos {

    public static final int MAX_TAX_ID_INPUT = 40;
    public static final int MAX_PHONE_INPUT = 30;
    public static final int COUNTRY_CODE_LENGTH = 2;
    public static final int MAX_ROLE_CODE = 50;
    public static final int MAX_PERMISSIONS_PER_ROLE = 500;

    private CompanyDtos() {
    }

    public record CompanyData(
            @NotBlank @Size(max = CompanyName.MAX_LEGAL_LENGTH) String legalName,
            @NotBlank @Size(max = CompanyName.MAX_TRADE_LENGTH) String tradeName,
            @NotBlank @Size(max = MAX_TAX_ID_INPUT) String taxId,
            @Size(max = EmailAddress.MAX_LENGTH) String email,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @NotBlank @Size(min = COUNTRY_CODE_LENGTH, max = COUNTRY_CODE_LENGTH) String countryCode
    ) {

        public CompanyDataCommand toCommand() {
            return new CompanyDataCommand(legalName, tradeName, taxId, email, phone, countryCode);
        }
    }

    public record OwnerData(
            @NotBlank @Size(max = EmailAddress.MAX_LENGTH) String email,
            @NotBlank @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String password,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String firstName,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone
    ) {

        @Override
        public String toString() {
            return "OwnerData[***]";
        }
    }

    public record RegisterCompanyRequest(
            @NotNull @Valid CompanyData company,
            @NotNull @Valid OwnerData owner,
            @NotNull Boolean acceptTerms
    ) {
    }

    public record ChangeRoleRequest(@NotNull UUID roleId) {
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = MAX_ROLE_CODE) String code,
            @NotBlank @Size(max = CompanyRole.MAX_NAME_LENGTH) String name,
            @NotNull @Size(max = MAX_PERMISSIONS_PER_ROLE) Set<UUID> permissionIds
    ) {
    }

    /**
     * @param name {@code null} para no cambiarlo (los roles de sistema no se renombran)
     */
    public record UpdateRoleRequest(
            @Size(max = CompanyRole.MAX_NAME_LENGTH) String name,
            @NotNull @Size(max = MAX_PERMISSIONS_PER_ROLE) Set<UUID> permissionIds
    ) {
    }

    public record InviteRequest(
            @NotBlank @Size(max = EmailAddress.MAX_LENGTH) String email,
            @NotNull UUID roleId
    ) {
    }

    public record InvitationTokenRequest(@NotBlank @Size(max = AuthDtos.MAX_TOKEN_INPUT) String token) {
    }

    /**
     * Si ya tiene sesión de backoffice con el email invitado, basta el token.
     * Si no tiene cuenta, se crea con estos datos.
     */
    public record AcceptInvitationRequest(
            @NotBlank @Size(max = AuthDtos.MAX_TOKEN_INPUT) String token,
            @Size(max = PersonName.MAX_LENGTH) String firstName,
            @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String password
    ) {

        @Override
        public String toString() {
            return "AcceptInvitationRequest[***]";
        }
    }
}
