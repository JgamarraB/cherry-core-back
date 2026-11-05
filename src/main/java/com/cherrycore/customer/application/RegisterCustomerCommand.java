package com.cherrycore.customer.application;

import java.util.Locale;

public record RegisterCustomerCommand(
        String email,
        String password,
        String firstName,
        String lastName,
        String phone,
        boolean termsAccepted,
        Locale locale
) {

    /**
     * La contraseña nunca aparece en logs aunque alguien imprima el comando.
     */
    @Override
    public String toString() {
        return "RegisterCustomerCommand[email=***, password=***]";
    }
}
