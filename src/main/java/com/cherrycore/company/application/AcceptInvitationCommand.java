package com.cherrycore.company.application;

import java.util.Locale;

/**
 * Datos para crear la cuenta al aceptar una invitación (si no tenía cuenta).
 */
public record AcceptInvitationCommand(String rawToken, String firstName, String lastName, String phone,
        String password, Locale locale) {

    @Override
    public String toString() {
        return "AcceptInvitationCommand[***]";
    }
}
