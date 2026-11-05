package com.cherrycore.company.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.TaxId;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;
import com.cherrycore.shared.domain.value.CountryCode;

public interface CompanyRepository {

    Optional<Company> findById(UUID id);

    List<Company> findByIds(Collection<UUID> ids);

    boolean existsByTaxId(CountryCode country, TaxId taxId, UUID excludingCompanyId);

    void save(Company company);

    /**
     * @param status {@code null} = todas
     * @param search texto en razón social, nombre comercial o CIF; {@code null} = sin filtro
     */
    PageResult<Company> search(CompanyStatus status, String search, PageQuery page);

    long countByStatus(CompanyStatus status);
}
