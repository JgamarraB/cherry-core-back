package com.cherrycore.auth.infrastructure.web;

import java.util.List;
import java.util.UUID;

import com.cherrycore.identity.application.MfaStatus;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.infrastructure.web.AuthDtos;
import com.cherrycore.shared.domain.value.PersonName;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    public static final int MAX_PHONE_INPUT = 30;
    public static final int MAX_LOCALE_INPUT = 10;
    public static final int MAX_CODE_INPUT = 32;

    private AccountDtos() {
    }

    /**
     * Mi cuenta: datos comunes + perfil según el tipo.
     */
    public record AccountOverview(
            UUID id,
            String type,
            String email,
            boolean emailVerified,
            String locale,
            String firstName,
            String lastName,
            String phone,
            boolean superAdmin,
            MfaStatus mfa
    ) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String firstName,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @Size(max = MAX_LOCALE_INPUT) String locale
    ) {
    }

    public record ChangePasswordRequest(
            @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String currentPassword,
            @NotBlank @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String newPassword
    ) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    public record ChangeEmailRequest(
            @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String currentPassword,
            @NotBlank @Size(max = EmailAddress.MAX_LENGTH) String newEmail
    ) {

        @Override
        public String toString() {
            return "ChangeEmailRequest[***]";
        }
    }

    public record DeleteAccountRequest(
            @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String currentPassword,
            @Size(max = MAX_CODE_INPUT) String mfaCode
    ) {

        @Override
        public String toString() {
            return "DeleteAccountRequest[***]";
        }
    }

    public record RecoveryCodesResponse(List<String> recoveryCodes) {
    }
}
