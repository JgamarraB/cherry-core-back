package com.cherrycore.company.application;

import java.time.Duration;

/**
 * @param invitationTtl  validez de los enlaces de invitación
 * @param maxLogoBytes   tamaño máximo del logo
 * @param termsVersion   versión de los términos aceptados al registrar una empresa
 */
public record CompanySettings(Duration invitationTtl, long maxLogoBytes, String termsVersion) {
}
