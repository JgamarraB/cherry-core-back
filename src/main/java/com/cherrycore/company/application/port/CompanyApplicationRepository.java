package com.cherrycore.company.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.CompanyApplication;

public interface CompanyApplicationRepository {

    List<CompanyApplication> findByCompany(UUID companyId);

    Optional<CompanyApplication> findByCompanyAndApplication(UUID companyId, UUID applicationId);

    void save(CompanyApplication companyApplication);
}
