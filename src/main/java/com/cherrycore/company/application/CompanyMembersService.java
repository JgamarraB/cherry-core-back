package com.cherrycore.company.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyNotifier;
import com.cherrycore.company.application.port.CompanyNotifier.Recipient;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.company.domain.SystemRole;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Miembros de la empresa: cambiar rol, suspender, reactivar, quitar, pasar la
 * propiedad y salir de una empresa.
 *
 * <p>Reglas: solo un OWNER puede dar o quitar el rol OWNER, y siempre debe
 * quedar al menos un OWNER activo. Todo queda en la auditoría.</p>
 */
@Service
public class CompanyMembersService {

    private final CompanyAccessPolicy access;
    private final MembershipRepository memberships;
    private final CompanyRoleRepository roles;
    private final CompanyRepository companies;
    private final StaffUserRepository staffUsers;
    private final AccountQueryService accounts;
    private final CompanyNotifier notifier;
    private final CompanyAudit audit;

    public CompanyMembersService(CompanyAccessPolicy access, MembershipRepository memberships,
            CompanyRoleRepository roles, CompanyRepository companies, StaffUserRepository staffUsers,
            AccountQueryService accounts, CompanyNotifier notifier, CompanyAudit audit) {
        this.access = access;
        this.memberships = memberships;
        this.roles = roles;
        this.companies = companies;
        this.staffUsers = staffUsers;
        this.accounts = accounts;
        this.notifier = notifier;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(StaffActor actor) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_VIEW);
        UUID companyId = context.company().id();
        List<Membership> members = memberships.findByCompany(companyId);
        Map<UUID, CompanyRole> rolesById = roles.findByCompany(companyId).stream()
                .collect(Collectors.toMap(CompanyRole::id, Function.identity()));
        Map<UUID, StaffUser> profiles = staffUsers.findByIds(members.stream().map(Membership::staffUserId).toList())
                .stream().collect(Collectors.toMap(StaffUser::id, Function.identity()));
        return members.stream().map(membership -> toView(membership, rolesById, profiles)).toList();
    }

    @Transactional
    public MemberView changeRole(StaffActor actor, UUID membershipId, UUID newRoleId, RequestContext request) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        CompanyRole currentRole = role(membership.roleId());
        CompanyRole newRole = roles.findById(newRoleId)
                .filter(candidate -> candidate.companyId().equals(context.company().id()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.ROLE_NOT_FOUND));

        if ((currentRole.isOwner() || newRole.isOwner()) && !context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        access.requireCanGrant(context, newRole.permissionIds());
        if (currentRole.isOwner() && !newRole.isOwner()) {
            ensureAnotherActiveOwner(currentRole, membership);
        }
        membership.changeRole(newRole.id());
        memberships.save(membership);
        if (!currentRole.id().equals(newRole.id())) {
            audit.event(AuthEventType.MEMBER_ROLE_CHANGED, membership.staffUserId(), context.company())
                    .actor(actor.accountId())
                    .detail(AuditDetail.PREVIOUS_ROLE, currentRole.code().value())
                    .detail(AuditDetail.ROLE, newRole.code().value())
                    .log(request);
        }
        return single(context, membership);
    }

    @Transactional
    public MemberView suspend(StaffActor actor, UUID membershipId, RequestContext request) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        protectOwner(context, membership);
        membership.suspend();
        memberships.save(membership);
        audit.event(AuthEventType.MEMBER_SUSPENDED, membership.staffUserId(), context.company())
                .actor(actor.accountId()).log(request);
        return single(context, membership);
    }

    @Transactional
    public MemberView reactivate(StaffActor actor, UUID membershipId, RequestContext request) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        if (role(membership.roleId()).isOwner() && !context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        membership.reactivate();
        memberships.save(membership);
        audit.event(AuthEventType.MEMBER_REACTIVATED, membership.staffUserId(), context.company())
                .actor(actor.accountId()).log(request);
        return single(context, membership);
    }

    @Transactional
    public void remove(StaffActor actor, UUID membershipId, RequestContext request) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        CompanyRole role = protectOwner(context, membership);
        memberships.delete(membership);
        audit.event(AuthEventType.MEMBER_REMOVED, membership.staffUserId(), context.company())
                .actor(actor.accountId())
                .detail(AuditDetail.ROLE, role.code().value())
                .log(request);
    }

    /**
     * El OWNER pasa la propiedad a otro miembro activo: ese miembro pasa a
     * OWNER y quien la cede pasa a ADMIN. Solo lo puede hacer un OWNER.
     * Se avisa por email al nuevo dueño.
     */
    @Transactional
    public MemberView transferOwnership(StaffActor actor, UUID membershipId, RequestContext request) {
        CompanyContext context = access.requireMember(actor);
        if (!context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        Company company = context.company();
        Membership target = findInCompany(context, membershipId);
        CompanyRole targetRole = role(target.roleId());
        if (target.id().equals(context.membership().id()) || !target.isActive() || targetRole.isOwner()) {
            throw new DomainException(CompanyErrorCode.INVALID_OWNERSHIP_TRANSFER);
        }
        Account newOwner = accounts.findActive(target.staffUserId())
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVALID_OWNERSHIP_TRANSFER));
        CompanyRole adminRole = roles.findByCompanyAndCode(company.id(), SystemRole.ADMIN.code())
                .orElseThrow(() -> new IllegalStateException("Company without admin role"));

        target.changeRole(context.role().id());
        memberships.save(target);
        Membership previousOwner = context.membership();
        previousOwner.changeRole(adminRole.id());
        memberships.save(previousOwner);

        audit.event(AuthEventType.OWNERSHIP_TRANSFERRED, actor.accountId(), company)
                .target(target.staffUserId())
                .detail(AuditDetail.PREVIOUS_ROLE, targetRole.code().value())
                .log(request);
        notifier.ownershipTransferred(company, new Recipient(newOwner.email().value(), newOwner.locale()),
                displayName(actor.accountId()));
        return single(context, target);
    }

    /**
     * La persona sale de una empresa (cualquier estado). No se permite si es
     * el último OWNER activo de una empresa viva: antes debe pasar la propiedad.
     */
    @Transactional
    public void leave(UUID accountId, UUID companyId, RequestContext request) {
        Membership membership = memberships.findByCompanyAndStaffUser(companyId, accountId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.MEMBER_NOT_FOUND));
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.MEMBER_NOT_FOUND));
        CompanyRole role = role(membership.roleId());
        boolean companyAlive = company.status() != CompanyStatus.REJECTED;
        if (role.isOwner() && companyAlive && remainingActiveOwners(role, membership) < 1) {
            throw new DomainException(CompanyErrorCode.SOLE_OWNER_CANNOT_LEAVE);
        }
        memberships.delete(membership);
        audit.event(AuthEventType.COMPANY_LEFT, accountId, company)
                .detail(AuditDetail.ROLE, role.code().value())
                .log(request);
    }

    private CompanyRole protectOwner(CompanyContext context, Membership membership) {
        CompanyRole role = role(membership.roleId());
        if (role.isOwner()) {
            if (!context.isOwner()) {
                throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
            }
            ensureAnotherActiveOwner(role, membership);
        }
        return role;
    }

    private void ensureAnotherActiveOwner(CompanyRole ownerRole, Membership leaving) {
        if (remainingActiveOwners(ownerRole, leaving) < 1) {
            throw new DomainException(CompanyErrorCode.LAST_OWNER);
        }
    }

    private long remainingActiveOwners(CompanyRole ownerRole, Membership leaving) {
        companies.lockForUpdate(ownerRole.companyId());
        long activeOwners = memberships.countActiveByRole(ownerRole.id());
        return leaving.isActive() ? activeOwners - 1 : activeOwners;
    }

    private String displayName(UUID accountId) {
        return staffUsers.findById(accountId)
                .map(profile -> profile.firstName().value() + " " + profile.lastName().value())
                .orElse(null);
    }

    private Membership findInCompany(CompanyContext context, UUID membershipId) {
        return memberships.findById(membershipId)
                .filter(membership -> membership.companyId().equals(context.company().id()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.MEMBER_NOT_FOUND));
    }

    private CompanyRole role(UUID roleId) {
        return roles.findById(roleId).orElseThrow(() -> new DomainException(CompanyErrorCode.ROLE_NOT_FOUND));
    }

    private MemberView single(CompanyContext context, Membership membership) {
        Map<UUID, CompanyRole> rolesById = roles.findByCompany(context.company().id()).stream()
                .collect(Collectors.toMap(CompanyRole::id, Function.identity()));
        Map<UUID, StaffUser> profiles = staffUsers.findByIds(List.of(membership.staffUserId())).stream()
                .collect(Collectors.toMap(StaffUser::id, Function.identity()));
        return toView(membership, rolesById, profiles);
    }

    private MemberView toView(Membership membership, Map<UUID, CompanyRole> rolesById,
            Map<UUID, StaffUser> profiles) {
        CompanyRole role = rolesById.get(membership.roleId());
        StaffUser profile = profiles.get(membership.staffUserId());
        String email = accounts.findById(membership.staffUserId()).map(account -> account.email().value())
                .orElse(null);
        return new MemberView(membership.id(), membership.staffUserId(), email,
                profile == null ? null : profile.firstName().value(),
                profile == null ? null : profile.lastName().value(),
                role.id(), role.code().value(), role.name(), membership.status());
    }
}
