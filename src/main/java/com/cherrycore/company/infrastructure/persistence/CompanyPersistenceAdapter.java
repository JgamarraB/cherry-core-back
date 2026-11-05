package com.cherrycore.company.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.cherrycore.company.application.port.CompanyApplicationRepository;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyApplication;
import com.cherrycore.company.domain.CompanyName;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.TaxId;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;
import com.cherrycore.shared.domain.value.CountryCode;
import com.cherrycore.shared.domain.value.PhoneNumber;

import jakarta.persistence.criteria.Predicate;

@Repository
class CompanyPersistenceAdapter implements CompanyRepository, CompanyApplicationRepository {

    private static final String CREATED_AT = "createdAt";
    private static final String STATUS = "status";
    private static final String LEGAL_NAME = "legalName";
    private static final String TRADE_NAME = "tradeName";
    private static final String TAX_ID = "taxId";
    private static final String LIKE = "%";

    private final CompanyJpaRepository companies;
    private final CompanyApplicationJpaRepository companyApplications;

    CompanyPersistenceAdapter(CompanyJpaRepository companies, CompanyApplicationJpaRepository companyApplications) {
        this.companies = companies;
        this.companyApplications = companyApplications;
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return companies.findById(id).map(CompanyPersistenceAdapter::toDomain);
    }

    @Override
    public List<Company> findByIds(Collection<UUID> ids) {
        return companies.findAllById(ids).stream().map(CompanyPersistenceAdapter::toDomain).toList();
    }

    @Override
    public boolean existsByTaxId(CountryCode country, TaxId taxId, UUID excludingCompanyId) {
        return excludingCompanyId == null
                ? companies.existsByCountryCodeAndTaxId(country.value(), taxId.value())
                : companies.existsByCountryCodeAndTaxIdAndIdNot(country.value(), taxId.value(), excludingCompanyId);
    }

    @Override
    public void lockForUpdate(UUID companyId) {
        companies.findWithLockById(companyId);
    }

    @Override
    public void save(Company company) {
        CompanyJpaEntity entity = companies.findById(company.id()).orElseGet(() -> new CompanyJpaEntity(company.id()));
        CompanyProfileData profile = company.profile();
        entity.setLegalName(profile.legalName().value());
        entity.setTradeName(profile.tradeName().value());
        entity.setTaxId(profile.taxId().value());
        entity.setEmail(profile.email() == null ? null : profile.email().value());
        entity.setPhone(profile.phone() == null ? null : profile.phone().value());
        entity.setCountryCode(profile.country().value());
        entity.setStatus(company.status());
        entity.setReviewedAt(company.reviewedAt());
        entity.setReviewedBy(company.reviewedBy());
        entity.setRejectionReason(company.rejectionReason());
        entity.setLogoKey(company.logoKey());
        companies.save(entity);
    }

    @Override
    public PageResult<Company> search(CompanyStatus status, String search, PageQuery page) {
        Specification<CompanyJpaEntity> filter = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get(STATUS), status));
            }
            if (search != null) {
                String pattern = LIKE + search.toLowerCase(Locale.ROOT) + LIKE;
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get(LEGAL_NAME)), pattern),
                        builder.like(builder.lower(root.get(TRADE_NAME)), pattern),
                        builder.like(builder.lower(root.get(TAX_ID)), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<CompanyJpaEntity> result = companies.findAll(filter,
                PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, CREATED_AT)));
        return new PageResult<>(result.getContent().stream().map(CompanyPersistenceAdapter::toDomain).toList(),
                page.page(), page.size(), result.getTotalElements());
    }

    @Override
    public long countByStatus(CompanyStatus status) {
        return companies.countByStatus(status);
    }

    @Override
    public List<CompanyApplication> findByCompany(UUID companyId) {
        return companyApplications.findByCompanyId(companyId).stream()
                .map(CompanyPersistenceAdapter::toDomain).toList();
    }

    @Override
    public Optional<CompanyApplication> findByCompanyAndApplication(UUID companyId, UUID applicationId) {
        return companyApplications.findByCompanyIdAndApplicationId(companyId, applicationId)
                .map(CompanyPersistenceAdapter::toDomain);
    }

    @Override
    public void save(CompanyApplication companyApplication) {
        CompanyApplicationJpaEntity entity = companyApplications.findById(companyApplication.id())
                .orElseGet(() -> new CompanyApplicationJpaEntity(companyApplication.id(),
                        companyApplication.companyId(), companyApplication.applicationId()));
        entity.setStatus(companyApplication.status());
        companyApplications.save(entity);
    }

    private static Company toDomain(CompanyJpaEntity entity) {
        CompanyProfileData profile = new CompanyProfileData(
                new CompanyName(entity.getLegalName()),
                new CompanyName(entity.getTradeName()),
                new TaxId(entity.getTaxId()),
                entity.getEmail() == null ? null : new EmailAddress(entity.getEmail()),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()),
                new CountryCode(entity.getCountryCode()));
        return Company.restore(entity.getId(), profile, entity.getStatus(), entity.getReviewedAt(),
                entity.getReviewedBy(), entity.getRejectionReason(), entity.getLogoKey());
    }

    private static CompanyApplication toDomain(CompanyApplicationJpaEntity entity) {
        return new CompanyApplication(entity.getId(), entity.getCompanyId(), entity.getApplicationId(),
                entity.getStatus());
    }
}
