package com.cherrycore.customer.infrastructure.web;

import java.util.UUID;

import com.cherrycore.customer.domain.CustomerAddress;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.customer.domain.PostalAddress;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.identity.infrastructure.web.AuthDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    public static final int MAX_PHONE_INPUT = 30;
    public static final int MAX_LOCALE_INPUT = 10;
    public static final int COUNTRY_CODE_LENGTH = 2;

    private CustomerDtos() {
    }

    /**
     * El idioma de los emails se toma de la cabecera Accept-Language.
     */
    public record RegisterRequest(
            @NotBlank @Size(max = EmailAddress.MAX_LENGTH) String email,
            @NotBlank @Size(max = AuthDtos.MAX_PASSWORD_INPUT) String password,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String firstName,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @NotNull Boolean acceptTerms
    ) {

        @Override
        public String toString() {
            return "RegisterRequest[***]";
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String firstName,
            @NotBlank @Size(max = PersonName.MAX_LENGTH) String lastName,
            @Size(max = MAX_PHONE_INPUT) String phone,
            @Size(max = MAX_LOCALE_INPUT) String locale
    ) {
    }

    public record AddressRequest(
            @NotBlank @Size(max = PostalAddress.MAX_LINE_LENGTH) String line1,
            @Size(max = PostalAddress.MAX_LINE_LENGTH) String line2,
            @NotBlank @Size(max = PostalAddress.MAX_CITY_LENGTH) String city,
            @NotBlank @Size(max = PostalAddress.MAX_POSTAL_CODE_LENGTH) String postalCode,
            @Size(max = PostalAddress.MAX_REGION_LENGTH) String region,
            @NotBlank @Size(min = COUNTRY_CODE_LENGTH, max = COUNTRY_CODE_LENGTH) String countryCode,
            boolean isDefault
    ) {
    }

    public record AddressResponse(
            UUID id,
            String line1,
            String line2,
            String city,
            String postalCode,
            String region,
            String countryCode,
            boolean isDefault
    ) {

        public static AddressResponse from(CustomerAddress address) {
            PostalAddress data = address.address();
            return new AddressResponse(address.id(), data.line1(), data.line2(), data.city(), data.postalCode(),
                    data.region(), data.country().value(), address.isDefault());
        }
    }
}
