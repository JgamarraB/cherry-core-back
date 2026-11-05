package com.cherrycore.company.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.RoleCode;

public interface CompanyRoleRepository {

    Optional<CompanyRole> findById(UUID id);

    List<CompanyRole> findByCompany(UUID companyId);

    Optional<CompanyRole> findByCompanyAndCode(UUID companyId, RoleCode code);

    void save(CompanyRole role);

    void delete(CompanyRole role);
}
