package com.cherrycore.company.domain;

import com.cherrycore.shared.domain.error.ErrorCategory;
import com.cherrycore.shared.domain.error.ErrorCode;

public enum CompanyErrorCode implements ErrorCode {

    INVALID_TAX_ID(ErrorCategory.INVALID_INPUT, "Tax id is not valid"),
    INVALID_COMPANY_NAME(ErrorCategory.INVALID_INPUT, "Company name is not valid"),
    INVALID_ROLE_NAME(ErrorCategory.INVALID_INPUT, "Role name is not valid"),
    INVALID_ROLE_CODE(ErrorCategory.INVALID_INPUT, "Role code must be uppercase letters, digits or underscores"),
    REJECTION_REASON_REQUIRED(ErrorCategory.INVALID_INPUT, "A rejection reason is required"),
    LOGO_INVALID_FORMAT(ErrorCategory.INVALID_INPUT, "Logo must be a PNG, JPEG or WEBP image"),
    LOGO_TOO_LARGE(ErrorCategory.INVALID_INPUT, "Logo file is too large"),
    COMPANY_NOT_FOUND(ErrorCategory.NOT_FOUND, "Company not found"),
    ROLE_NOT_FOUND(ErrorCategory.NOT_FOUND, "Role not found"),
    MEMBER_NOT_FOUND(ErrorCategory.NOT_FOUND, "Member not found"),
    INVITATION_NOT_FOUND(ErrorCategory.NOT_FOUND, "Invitation not found"),
    TAX_ID_ALREADY_REGISTERED(ErrorCategory.CONFLICT, "A company with this tax id already exists"),
    ROLE_CODE_ALREADY_EXISTS(ErrorCategory.CONFLICT, "A role with this code already exists"),
    ALREADY_MEMBER(ErrorCategory.CONFLICT, "This person is already a member of the company"),
    INVALID_STATUS_TRANSITION(ErrorCategory.BUSINESS_RULE, "Operation not allowed in the current status"),
    COMPANY_NOT_ACTIVE(ErrorCategory.BUSINESS_RULE, "Company is not active"),
    SYSTEM_ROLE_IMMUTABLE(ErrorCategory.BUSINESS_RULE, "System roles cannot be renamed or deleted"),
    OWNER_PERMISSIONS_FIXED(ErrorCategory.BUSINESS_RULE, "Owner role always has every permission"),
    ROLE_IN_USE(ErrorCategory.BUSINESS_RULE, "Role is assigned to members"),
    OWNER_ROLE_RESERVED(ErrorCategory.FORBIDDEN, "Only an owner can grant or remove the owner role"),
    LAST_OWNER(ErrorCategory.BUSINESS_RULE, "The company must keep at least one active owner"),
    INVALID_OR_EXPIRED_INVITATION(ErrorCategory.INVALID_INPUT, "Invitation is invalid or has expired"),
    INVITATION_FOR_ANOTHER_ACCOUNT(ErrorCategory.FORBIDDEN, "This invitation was sent to another email"),
    PERMISSION_NOT_AVAILABLE(ErrorCategory.INVALID_INPUT, "Permission does not belong to an application of the company"),
    NO_COMPANY_SELECTED(ErrorCategory.FORBIDDEN, "No company selected"),
    COMPANY_PERMISSION_DENIED(ErrorCategory.FORBIDDEN, "You do not have permission for this action in the company");

    private final ErrorCategory category;
    private final String description;

    CompanyErrorCode(ErrorCategory category, String description) {
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
