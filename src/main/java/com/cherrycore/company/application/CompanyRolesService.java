package com.cherrycore.company.application;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.company.application.port.CompanyApplicationRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.CompanyApplication;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.RoleCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Roles propios de la empresa y permisos de cada rol.
 * Solo se pueden asignar permisos de apps activadas para la empresa.
 */
@Service
public class CompanyRolesService {

    private final CompanyAccessPolicy access;
    private final CompanyRoleRepository roles;
    private final MembershipRepository memberships;
    private final CompanyApplicationRepository companyApplications;
    private final CatalogService catalog;
    private final Clock clock;

    public CompanyRolesService(CompanyAccessPolicy access, CompanyRoleRepository roles,
            MembershipRepository memberships, CompanyApplicationRepository companyApplications,
            CatalogService catalog, Clock clock) {
        this.access = access;
        this.roles = roles;
        this.memberships = memberships;
        this.companyApplications = companyApplications;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoleView> list(StaffActor actor) {
        UUID companyId = access.require(actor, CompanyPermission.MEMBERS_VIEW).company().id();
        List<CompanyRole> companyRoles = roles.findByCompany(companyId);
        Map<UUID, RoleView.PermissionView> permissions = permissionViews(companyRoles.stream()
                .flatMap(role -> role.permissionIds().stream()).collect(Collectors.toSet()));
        return companyRoles.stream().map(role -> toView(role, permissions)).toList();
    }

    @Transactional(readOnly = true)
    public List<AvailablePermissionsView> availablePermissions(StaffActor actor) {
        UUID companyId = access.require(actor, CompanyPermission.MEMBERS_VIEW).company().id();
        List<Application> enabledApps = enabledApplications(companyId);
        Map<UUID, List<ApplicationPermission>> byApp = catalog.permissionsOf(
                enabledApps.stream().map(Application::id).toList()).stream()
                .collect(Collectors.groupingBy(ApplicationPermission::applicationId));
        return enabledApps.stream()
                .map(app -> new AvailablePermissionsView(app.id(), app.code().value(), app.name(),
                        byApp.getOrDefault(app.id(), List.of()).stream()
                                .map(permission -> new RoleView.PermissionView(permission.id(), app.code().value(),
                                        permission.code().value(), permission.description()))
                                .toList()))
                .toList();
    }

    @Transactional
    public RoleView create(StaffActor actor, String code, String name, Set<UUID> permissionIds) {
        UUID companyId = access.require(actor, CompanyPermission.ROLES_MANAGE).company().id();
        RoleCode roleCode = new RoleCode(code);
        if (roles.findByCompanyAndCode(companyId, roleCode).isPresent()) {
            throw new DomainException(CompanyErrorCode.ROLE_CODE_ALREADY_EXISTS);
        }
        validatePermissions(companyId, permissionIds);
        CompanyRole role = new CompanyRole(UuidV7.generate(clock), companyId, roleCode, cleanName(name), false,
                permissionIds);
        roles.save(role);
        return toView(role, permissionViews(role.permissionIds()));
    }

    /**
     * @param name {@code null} para no cambiarlo (obligatorio en roles de sistema)
     */
    @Transactional
    public RoleView update(StaffActor actor, UUID roleId, String name, Set<UUID> permissionIds) {
        UUID companyId = access.require(actor, CompanyPermission.ROLES_MANAGE).company().id();
        CompanyRole role = findInCompany(companyId, roleId);
        if (name != null && !name.strip().equals(role.name())) {
            role.rename(cleanName(name));
        }
        validatePermissions(companyId, permissionIds);
        role.replacePermissions(permissionIds);
        roles.save(role);
        return toView(role, permissionViews(role.permissionIds()));
    }

    @Transactional
    public void delete(StaffActor actor, UUID roleId) {
        UUID companyId = access.require(actor, CompanyPermission.ROLES_MANAGE).company().id();
        CompanyRole role = findInCompany(companyId, roleId);
        role.ensureDeletable();
        if (memberships.countByRole(role.id()) > 0) {
            throw new DomainException(CompanyErrorCode.ROLE_IN_USE);
        }
        roles.delete(role);
    }

    private void validatePermissions(UUID companyId, Collection<UUID> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        Set<UUID> enabledAppIds = enabledApplications(companyId).stream().map(Application::id)
                .collect(Collectors.toSet());
        List<ApplicationPermission> found = catalog.permissionsByIds(permissionIds);
        boolean allValid = found.size() == new HashSet<>(permissionIds).size()
                && found.stream().allMatch(permission -> enabledAppIds.contains(permission.applicationId()));
        if (!allValid) {
            throw new DomainException(CompanyErrorCode.PERMISSION_NOT_AVAILABLE);
        }
    }

    private List<Application> enabledApplications(UUID companyId) {
        Set<UUID> enabled = companyApplications.findByCompany(companyId).stream()
                .filter(CompanyApplication::isEnabled)
                .map(CompanyApplication::applicationId)
                .collect(Collectors.toSet());
        return catalog.listApplications().stream()
                .filter(app -> app.isActive() && enabled.contains(app.id()))
                .toList();
    }

    private CompanyRole findInCompany(UUID companyId, UUID roleId) {
        return roles.findById(roleId)
                .filter(role -> role.companyId().equals(companyId))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.ROLE_NOT_FOUND));
    }

    private Map<UUID, RoleView.PermissionView> permissionViews(Collection<UUID> permissionIds) {
        List<ApplicationPermission> permissions = catalog.permissionsByIds(permissionIds);
        Map<UUID, String> appCodes = catalog.listApplications().stream()
                .collect(Collectors.toMap(Application::id, app -> app.code().value()));
        return permissions.stream().collect(Collectors.toMap(ApplicationPermission::id,
                permission -> new RoleView.PermissionView(permission.id(), appCodes.get(permission.applicationId()),
                        permission.code().value(), permission.description()),
                (first, second) -> first));
    }

    private static RoleView toView(CompanyRole role, Map<UUID, RoleView.PermissionView> permissions) {
        return new RoleView(role.id(), role.code().value(), role.name(), role.isSystem(), role.isOwner(),
                role.permissionIds().stream().map(permissions::get).filter(Objects::nonNull)
                        .sorted(Comparator.comparing(RoleView.PermissionView::code))
                        .toList());
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? null : name.strip();
        if (cleaned == null || cleaned.isEmpty() || cleaned.length() > CompanyRole.MAX_NAME_LENGTH
                || cleaned.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException(CompanyErrorCode.INVALID_ROLE_NAME);
        }
        return cleaned;
    }
}
