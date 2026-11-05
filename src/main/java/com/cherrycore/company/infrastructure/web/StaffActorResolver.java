package com.cherrycore.company.infrastructure.web;

import com.cherrycore.company.application.StaffActor;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

/**
 * La cuenta y la empresa activa salen SIEMPRE de la sesión, nunca de la
 * petición: no se puede operar sobre otra empresa cambiando un id.
 */
final class StaffActorResolver {

    private StaffActorResolver() {
    }

    static StaffActor current() {
        return new StaffActor(
                CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED)),
                CurrentAccount.activeCompanyId()
                        .orElseThrow(() -> new DomainException(CompanyErrorCode.NO_COMPANY_SELECTED)));
    }
}
