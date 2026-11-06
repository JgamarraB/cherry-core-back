package com.cherrycore.company.application;

import java.util.List;

/**
 * Datos de la persona de backoffice para la exportación RGPD: perfil y sus
 * empresas con su rol y el estado de su pertenencia.
 */
public record StaffDataExport(String firstName, String lastName, String phone, List<MyCompanyView> companies) {
}
