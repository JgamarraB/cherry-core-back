package com.cherrycore.company.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyNotifier;
import com.cherrycore.company.application.port.CompanyNotifier.Recipient;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.SystemRole;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Acciones del superadmin sobre empresas: aprobar, rechazar, suspender, reactivar.
 */
@Service
public class CompanyReviewService {

    private final CompanyRepository companies;
    private final CompanyRoleRepository roles;
    private final MembershipRepository memberships;
    private final AccountQueryService accounts;
    private final CompanyNotifier notifier;
    private final Clock clock;

    public CompanyReviewService(CompanyRepository companies, CompanyRoleRepository roles,
            MembershipRepository memberships, AccountQueryService accounts, CompanyNotifier notifier, Clock clock) {
        this.companies = companies;
        this.roles = roles;
        this.memberships = memberships;
        this.accounts = accounts;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional
    public Company approve(UUID reviewerId, UUID companyId) {
        Company company = find(companyId);
        company.approve(reviewerId, clock.instant());
        companies.save(company);
        notifier.companyApproved(company, owners(company));
        return company;
    }

    @Transactional
    public Company reject(UUID reviewerId, UUID companyId, String reason) {
        Company company = find(companyId);
        company.reject(reviewerId, clock.instant(), reason);
        companies.save(company);
        notifier.companyRejected(company, owners(company));
        return company;
    }

    @Transactional
    public Company suspend(UUID companyId) {
        Company company = find(companyId);
        company.suspend();
        companies.save(company);
        return company;
    }

    @Transactional
    public Company reactivate(UUID companyId) {
        Company company = find(companyId);
        company.reactivate();
        companies.save(company);
        return company;
    }

    private Company find(UUID companyId) {
        return companies.findById(companyId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    private List<Recipient> owners(Company company) {
        UUID ownerRoleId = roles.findByCompanyAndCode(company.id(), SystemRole.OWNER.code())
                .orElseThrow(() -> new IllegalStateException("Company without owner role"))
                .id();
        return memberships.findByCompany(company.id()).stream()
                .filter(membership -> membership.roleId().equals(ownerRoleId))
                .map(Membership::staffUserId)
                .flatMap(accountId -> accounts.findById(accountId).stream())
                .map(account -> new Recipient(account.email().value(), account.locale()))
                .toList();
    }
}
