package com.cherrycore.company.application;

/**
 * Lo que ve quien abre el enlace de invitación.
 *
 * @param accountExists si ya tiene cuenta de backoffice (entonces solo inicia
 *                      sesión y acepta; si no, crea la cuenta)
 */
public record InvitationInfo(String companyName, String companyLogoUrl, String email, String roleName,
        boolean accountExists) {
}
