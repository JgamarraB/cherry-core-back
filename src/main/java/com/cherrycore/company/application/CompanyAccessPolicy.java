package com.cherrycore.company.application;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Comprueba que quien opera pertenece (activo) a una empresa activa y que su
 * rol tiene el permiso. Se consulta siempre la base de datos: un cambio de
 * rol o una suspensión surten efecto en la siguiente petición.
 */
@Component
public class CompanyAccessPolicy {

    private final CompanyRepository companies;
    private final MembershipRepository memberships;
    private final CompanyRoleRepository roles;
    private final CatalogService catalog;

    public CompanyAccessPolicy(CompanyRepository companies, MembershipRepository memberships,
            CompanyRoleRepository roles, CatalogService catalog) {
        this.companies = companies;
        this.memberships = memberships;
        this.roles = roles;
        this.catalog = catalog;
    }

    /**
     * Miembro activo de la empresa activa, sin exigir un permiso concreto.
     */
    public CompanyContext requireMember(StaffActor actor) {
        if (actor.companyId() == null) {
            throw new DomainException(CompanyErrorCode.NO_COMPANY_SELECTED);
        }
        Company company = companies.findById(actor.companyId())
                .filter(Company::isActive)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED));
        Membership membership = memberships.findByCompanyAndStaffUser(company.id(), actor.accountId())
                .filter(Membership::isActive)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED));
        CompanyRole role = roles.findById(membership.roleId())
                .orElseThrow(() -> new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED));
        return new CompanyContext(company, membership, role);
    }

    /**
     * Mi rol y mis permisos de CherryCore en la empresa activa.
     */
    public MyCompanyAccess myAccess(StaffActor actor) {
        CompanyContext context = requireMember(actor);
        List<String> granted = Arrays.stream(CompanyPermission.values())
                .filter(permission -> context.isOwner()
                        || context.role().permissionIds().contains(permissionId(permission)))
                .map(CompanyPermission::code)
                .toList();
        return new MyCompanyAccess(context.company().id(), context.membership().id(), context.role().code().value(),
                context.role().name(), context.isOwner(), granted);
    }

    public CompanyContext require(StaffActor actor, CompanyPermission permission) {
        CompanyContext context = requireMember(actor);
        if (!context.isOwner() && !context.role().permissionIds().contains(permissionId(permission))) {
            throw new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED);
        }
        return context;
    }

    /**
     * Quien no es OWNER solo puede dar permisos que él mismo tiene (al asignar
     * un rol, invitar con un rol o editar los permisos de un rol). Si no,
     * con "gestionar miembros" o "gestionar roles" podría ascenderse a sí
     * mismo o a otra cuenta suya (p. ej. a ADMIN, o con permisos de otras apps).
     */
    public void requireCanGrant(CompanyContext context, Collection<UUID> permissionIds) {
        if (!context.isOwner() && !context.role().permissionIds().containsAll(permissionIds)) {
            throw new DomainException(CompanyErrorCode.ROLE_ABOVE_OWN_PERMISSIONS);
        }
    }

    private UUID permissionId(CompanyPermission permission) {
        Application cherryCore = catalog.findByCode(CompanyPermission.APPLICATION_CODE)
                .orElseThrow(() -> new IllegalStateException("CherryCore application not registered"));
        return catalog.permissionsOf(cherryCore.id()).stream()
                .filter(candidate -> candidate.code().value().equals(permission.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Permission not registered: " + permission.code()))
                .id();
    }
}
