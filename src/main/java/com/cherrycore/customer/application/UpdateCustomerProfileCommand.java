package com.cherrycore.customer.application;

import java.util.Locale;

public record UpdateCustomerProfileCommand(String firstName, String lastName, String phone, Locale locale) {
}
