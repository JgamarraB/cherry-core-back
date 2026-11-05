package com.cherrycore.company.application;

import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.Membership;

/**
 * Empresa, pertenencia y rol de quien hace la operación (ya comprobados).
 */
public record CompanyContext(Company company, Membership membership, CompanyRole role) {

    public boolean isOwner() {
        return role.isOwner();
    }
}
