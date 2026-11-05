package com.cherrycore.customer.application;

import java.time.Instant;
import java.util.List;

/**
 * Datos del cliente para la exportación RGPD: perfil, términos aceptados y direcciones.
 */
public record CustomerDataExport(
        String firstName,
        String lastName,
        String phone,
        Instant termsAcceptedAt,
        String termsVersion,
        List<AddressData> addresses
) {

    public record AddressData(String line1, String line2, String city, String postalCode, String region,
            String countryCode, boolean isDefault) {
    }
}
