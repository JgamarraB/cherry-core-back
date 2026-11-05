package com.cherrycore.customer.domain;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

public enum CustomerErrorCode implements ErrorCode {

    TERMS_NOT_ACCEPTED(ErrorCategory.INVALID_INPUT, "Terms and conditions must be accepted"),
    INVALID_NAME(ErrorCategory.INVALID_INPUT, "Name is not valid"),
    INVALID_PHONE(ErrorCategory.INVALID_INPUT, "Phone number must be in international format, e.g. +34600111222"),
    INVALID_COUNTRY_CODE(ErrorCategory.INVALID_INPUT, "Country code must be ISO 3166-1 alpha-2, e.g. ES"),
    INVALID_ADDRESS(ErrorCategory.INVALID_INPUT, "Address is not valid"),
    CUSTOMER_NOT_FOUND(ErrorCategory.NOT_FOUND, "Customer not found"),
    ADDRESS_NOT_FOUND(ErrorCategory.NOT_FOUND, "Address not found"),
    ADDRESS_LIMIT_REACHED(ErrorCategory.BUSINESS_RULE, "Maximum number of addresses reached");

    private final ErrorCategory category;
    private final String description;

    CustomerErrorCode(ErrorCategory category, String description) {
        this.category = category;
        this.description = description;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ErrorCategory category() {
        return category;
    }
}
