package com.cherrycore.company.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Miembros de la empresa: cambiar rol, suspender, reactivar, quitar.
 *
 * <p>Reglas: solo un OWNER puede dar o quitar el rol OWNER, y siempre debe
 * quedar al menos un OWNER activo.</p>
 */
@Service
public class CompanyMembersService {

    private final CompanyAccessPolicy access;
    private final MembershipRepository memberships;
    private final CompanyRoleRepository roles;
    private final StaffUserRepository staffUsers;
    private final AccountQueryService accounts;

    public CompanyMembersService(CompanyAccessPolicy access, MembershipRepository memberships,
            CompanyRoleRepository roles, StaffUserRepository staffUsers, AccountQueryService accounts) {
        this.access = access;
        this.memberships = memberships;
        this.roles = roles;
        this.staffUsers = staffUsers;
        this.accounts = accounts;
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
    public MemberView changeRole(StaffActor actor, UUID membershipId, UUID newRoleId) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        CompanyRole currentRole = role(membership.roleId());
        CompanyRole newRole = roles.findById(newRoleId)
                .filter(candidate -> candidate.companyId().equals(context.company().id()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.ROLE_NOT_FOUND));

        if ((currentRole.isOwner() || newRole.isOwner()) && !context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        if (currentRole.isOwner() && !newRole.isOwner()) {
            ensureAnotherActiveOwner(currentRole, membership);
        }
        membership.changeRole(newRole.id());
        memberships.save(membership);
        return single(context, membership);
    }

    @Transactional
    public MemberView suspend(StaffActor actor, UUID membershipId) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        protectOwner(context, membership);
        membership.suspend();
        memberships.save(membership);
        return single(context, membership);
    }

    @Transactional
    public MemberView reactivate(StaffActor actor, UUID membershipId) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        if (role(membership.roleId()).isOwner() && !context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        membership.reactivate();
        memberships.save(membership);
        return single(context, membership);
    }

    @Transactional
    public void remove(StaffActor actor, UUID membershipId) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Membership membership = findInCompany(context, membershipId);
        protectOwner(context, membership);
        memberships.delete(membership);
    }

    private void protectOwner(CompanyContext context, Membership membership) {
        CompanyRole role = role(membership.roleId());
        if (role.isOwner()) {
            if (!context.isOwner()) {
                throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
            }
            ensureAnotherActiveOwner(role, membership);
        }
    }

    private void ensureAnotherActiveOwner(CompanyRole ownerRole, Membership leaving) {
        long activeOwners = memberships.countActiveByRole(ownerRole.id());
        long remaining = leaving.isActive() ? activeOwners - 1 : activeOwners;
        if (remaining < 1) {
            throw new DomainException(CompanyErrorCode.LAST_OWNER);
        }
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
