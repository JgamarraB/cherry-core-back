package com.cherrycore.company.application;

import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.identity.domain.AccountDeletionRequested;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Al borrar una cuenta de backoffice: no se permite si es el único OWNER de
 * una empresa viva (antes debe pasar la propiedad); si no, se quitan sus
 * pertenencias y se anonimiza su perfil. Misma transacción que el borrado.
 */
@Component
class StaffAccountDeletionListener {

    private final MembershipRepository memberships;
    private final CompanyRoleRepository roles;
    private final CompanyRepository companies;
    private final StaffUserRepository staffUsers;

    StaffAccountDeletionListener(MembershipRepository memberships, CompanyRoleRepository roles,
            CompanyRepository companies, StaffUserRepository staffUsers) {
        this.memberships = memberships;
        this.roles = roles;
        this.companies = companies;
        this.staffUsers = staffUsers;
    }

    @EventListener
    void on(AccountDeletionRequested event) {
        if (event.type() != AccountType.STAFF) {
            return;
        }
        UUID accountId = event.accountId();
        for (Membership membership : memberships.findByStaffUser(accountId)) {
            CompanyRole role = roles.findById(membership.roleId()).orElseThrow();
            boolean companyAlive = companies.findById(membership.companyId())
                    .map(Company::status)
                    .filter(status -> status != CompanyStatus.REJECTED)
                    .isPresent();
            if (role.isOwner() && companyAlive && memberships.countActiveByRole(role.id()) <= 1) {
                throw new DomainException(CompanyErrorCode.SOLE_OWNER_CANNOT_LEAVE);
            }
        }
        memberships.findByStaffUser(accountId).forEach(memberships::delete);
        staffUsers.findById(accountId).ifPresent(profile -> {
            profile.anonymize();
            staffUsers.save(profile);
            staffUsers.softDelete(profile.id());
        });
    }
}
