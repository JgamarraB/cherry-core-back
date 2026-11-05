package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Consultas de empresas para el panel de superadmin.
 */
@Service
public class CompanyAdminService {

    private final CompanyRepository companies;
    private final MembershipRepository memberships;
    private final CompanyRoleRepository roles;
    private final StaffUserRepository staffUsers;
    private final AccountQueryService accounts;
    private final CompanyLogos logos;

    public CompanyAdminService(CompanyRepository companies, MembershipRepository memberships,
            CompanyRoleRepository roles, StaffUserRepository staffUsers, AccountQueryService accounts,
            CompanyLogos logos) {
        this.companies = companies;
        this.memberships = memberships;
        this.roles = roles;
        this.staffUsers = staffUsers;
        this.accounts = accounts;
        this.logos = logos;
    }

    @Transactional(readOnly = true)
    public PageResult<CompanyView> search(CompanyStatus status, String search, PageQuery page) {
        String cleaned = search == null || search.isBlank() ? null : search.strip();
        return companies.search(status, cleaned, page).map(logos::view);
    }

    @Transactional(readOnly = true)
    public CompanyView get(UUID companyId) {
        return logos.view(find(companyId));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID companyId) {
        find(companyId);
        List<CompanyRole> companyRoles = roles.findByCompany(companyId);
        return memberships.findByCompany(companyId).stream().map(membership -> {
            CompanyRole role = companyRoles.stream().filter(r -> r.id().equals(membership.roleId())).findFirst()
                    .orElseThrow();
            StaffUser profile = staffUsers.findById(membership.staffUserId()).orElse(null);
            String email = accounts.findById(membership.staffUserId()).map(a -> a.email().value()).orElse(null);
            return toView(membership, role, profile, email);
        }).toList();
    }

    @Transactional(readOnly = true)
    public long countByStatus(CompanyStatus status) {
        return companies.countByStatus(status);
    }

    private Company find(UUID companyId) {
        return companies.findById(companyId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    private static MemberView toView(Membership membership, CompanyRole role, StaffUser profile, String email) {
        return new MemberView(membership.id(), membership.staffUserId(), email,
                profile == null ? null : profile.firstName().value(),
                profile == null ? null : profile.lastName().value(),
                role.id(), role.code().value(), role.name(), membership.status());
    }
}
