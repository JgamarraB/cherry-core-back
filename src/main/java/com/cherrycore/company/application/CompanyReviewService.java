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
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;
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
    private final CompanyAudit audit;
    private final Clock clock;

    public CompanyReviewService(CompanyRepository companies, CompanyRoleRepository roles,
            MembershipRepository memberships, AccountQueryService accounts, CompanyNotifier notifier,
            CompanyAudit audit, Clock clock) {
        this.companies = companies;
        this.roles = roles;
        this.memberships = memberships;
        this.accounts = accounts;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Company approve(UUID reviewerId, UUID companyId, RequestContext request) {
        Company company = find(companyId);
        company.approve(reviewerId, clock.instant());
        companies.save(company);
        audit.event(AuthEventType.COMPANY_APPROVED, reviewerId, company).log(request);
        notifier.companyApproved(company, owners(company));
        return company;
    }

    @Transactional
    public Company reject(UUID reviewerId, UUID companyId, String reason, RequestContext request) {
        Company company = find(companyId);
        company.reject(reviewerId, clock.instant(), reason);
        companies.save(company);
        audit.event(AuthEventType.COMPANY_REJECTED, reviewerId, company)
                .detail(AuditDetail.REASON, company.rejectionReason()).log(request);
        notifier.companyRejected(company, owners(company));
        return company;
    }

    @Transactional
    public Company suspend(UUID adminId, UUID companyId, RequestContext request) {
        Company company = find(companyId);
        company.suspend();
        companies.save(company);
        audit.event(AuthEventType.COMPANY_SUSPENDED, adminId, company).log(request);
        return company;
    }

    @Transactional
    public Company reactivate(UUID adminId, UUID companyId, RequestContext request) {
        Company company = find(companyId);
        company.reactivate();
        companies.save(company);
        audit.event(AuthEventType.COMPANY_REACTIVATED, adminId, company).log(request);
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
