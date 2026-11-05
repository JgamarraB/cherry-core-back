package com.cherrycore.customer.application;

public record AddressData(
        String line1,
        String line2,
        String city,
        String postalCode,
        String region,
        String countryCode,
        boolean makeDefault
) {
}
