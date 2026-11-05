package com.cherrycore.customer.infrastructure.web;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.customer.application.AddressData;
import com.cherrycore.customer.application.CustomerAddressService;
import com.cherrycore.customer.application.CustomerProfile;
import com.cherrycore.customer.application.CustomerProfileService;
import com.cherrycore.customer.application.UpdateCustomerProfileCommand;
import com.cherrycore.customer.infrastructure.web.CustomerDtos.AddressRequest;
import com.cherrycore.customer.infrastructure.web.CustomerDtos.AddressResponse;
import com.cherrycore.customer.infrastructure.web.CustomerDtos.UpdateProfileRequest;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.validation.Valid;

/**
 * Perfil del cliente autenticado (token Bearer). El cliente siempre es el del
 * token: no hay forma de pedir datos de otro.
 */
@RestController
@RequestMapping(CustomerPaths.ME)
@PreAuthorize(AccountAuthorities.HAS_ROLE_CUSTOMER)
public class CustomerMeController {

    private final CustomerProfileService profileService;
    private final CustomerAddressService addressService;

    public CustomerMeController(CustomerProfileService profileService, CustomerAddressService addressService) {
        this.profileService = profileService;
        this.addressService = addressService;
    }

    @GetMapping
    public ApiSuccessResponse<CustomerProfile> get() {
        return ApiSuccessResponse.of(profileService.get(currentCustomerId()));
    }

    @PutMapping
    public ApiSuccessResponse<CustomerProfile> update(@Valid @RequestBody UpdateProfileRequest body) {
        Locale locale = body.locale() == null ? null : Locale.forLanguageTag(body.locale());
        return ApiSuccessResponse.of(profileService.update(currentCustomerId(),
                new UpdateCustomerProfileCommand(body.firstName(), body.lastName(), body.phone(), locale)));
    }

    @GetMapping(CustomerPaths.ADDRESSES)
    public ApiSuccessResponse<List<AddressResponse>> addresses() {
        return ApiSuccessResponse.of(addressService.list(currentCustomerId()).stream()
                .map(AddressResponse::from)
                .toList());
    }

    @PostMapping(CustomerPaths.ADDRESSES)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<AddressResponse> addAddress(@Valid @RequestBody AddressRequest body) {
        return ApiSuccessResponse.of(AddressResponse.from(addressService.add(currentCustomerId(), toData(body))));
    }

    @PutMapping(CustomerPaths.ADDRESS_ID)
    public ApiSuccessResponse<AddressResponse> updateAddress(@PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest body) {
        return ApiSuccessResponse.of(AddressResponse.from(
                addressService.update(currentCustomerId(), addressId, toData(body))));
    }

    @DeleteMapping(CustomerPaths.ADDRESS_ID)
    public ApiSuccessResponse<Void> deleteAddress(@PathVariable UUID addressId) {
        addressService.delete(currentCustomerId(), addressId);
        return ApiSuccessResponse.of(null);
    }

    private static UUID currentCustomerId() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }

    private static AddressData toData(AddressRequest body) {
        return new AddressData(body.line1(), body.line2(), body.city(), body.postalCode(), body.region(),
                body.countryCode(), body.isDefault());
    }
}
