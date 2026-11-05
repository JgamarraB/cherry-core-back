package com.cherrycore.admin.infrastructure.web;

import java.util.UUID;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

final class AdminSupport {

    private AdminSupport() {
    }

    static UUID currentAdmin() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }
}
