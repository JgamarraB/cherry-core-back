package com.cherrycore.company.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.RoleCode;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class RolePersistenceAdapter implements CompanyRoleRepository {

    private final CompanyRoleJpaRepository roles;
    private final RolePermissionJpaRepository rolePermissions;
    private final Clock clock;

    RolePersistenceAdapter(CompanyRoleJpaRepository roles, RolePermissionJpaRepository rolePermissions, Clock clock) {
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.clock = clock;
    }

    @Override
    public Optional<CompanyRole> findById(UUID id) {
        return roles.findById(id).map(entity -> toDomain(entity, permissionsOf(entity.getId())));
    }

    @Override
    public List<CompanyRole> findByCompany(UUID companyId) {
        List<CompanyRoleJpaEntity> entities = roles.findByCompanyIdOrderByCreatedAtAsc(companyId);
        Map<UUID, Set<UUID>> permissions = rolePermissions.findByRoleIdIn(
                entities.stream().map(CompanyRoleJpaEntity::getId).toList()).stream()
                .collect(Collectors.groupingBy(RolePermissionJpaEntity::getRoleId,
                        Collectors.mapping(RolePermissionJpaEntity::getPermissionId, Collectors.toSet())));
        return entities.stream()
                .map(entity -> toDomain(entity, permissions.getOrDefault(entity.getId(), Set.of())))
                .toList();
    }

    @Override
    public Optional<CompanyRole> findByCompanyAndCode(UUID companyId, RoleCode code) {
        return roles.findByCompanyIdAndCode(companyId, code.value())
                .map(entity -> toDomain(entity, permissionsOf(entity.getId())));
    }

    /**
     * Guarda el rol y sincroniza sus permisos: borra (lógicamente) los que ya
     * no tiene y añade los nuevos.
     */
    @Override
    public void save(CompanyRole role) {
        CompanyRoleJpaEntity entity = roles.findById(role.id()).orElseGet(() -> new CompanyRoleJpaEntity(role.id(),
                role.companyId(), role.code().value(), role.isSystem()));
        entity.setName(role.name());
        roles.saveAndFlush(entity);

        List<RolePermissionJpaEntity> current = rolePermissions.findByRoleId(role.id());
        Set<UUID> currentIds = current.stream().map(RolePermissionJpaEntity::getPermissionId)
                .collect(Collectors.toSet());
        current.stream()
                .filter(assignment -> !role.permissionIds().contains(assignment.getPermissionId()))
                .forEach(assignment -> {
                    assignment.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
                    rolePermissions.save(assignment);
                });
        role.permissionIds().stream()
                .filter(permissionId -> !currentIds.contains(permissionId))
                .forEach(permissionId -> rolePermissions.save(
                        new RolePermissionJpaEntity(UuidV7.generate(clock), role.id(), permissionId)));
        rolePermissions.flush();
    }

    @Override
    public void delete(CompanyRole role) {
        roles.findById(role.id()).ifPresent(entity -> {
            rolePermissions.findByRoleId(role.id()).forEach(assignment -> {
                assignment.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
                rolePermissions.save(assignment);
            });
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
            roles.saveAndFlush(entity);
        });
    }

    private Set<UUID> permissionsOf(UUID roleId) {
        return rolePermissions.findByRoleId(roleId).stream().map(RolePermissionJpaEntity::getPermissionId)
                .collect(Collectors.toSet());
    }

    private static CompanyRole toDomain(CompanyRoleJpaEntity entity, Set<UUID> permissionIds) {
        return new CompanyRole(entity.getId(), entity.getCompanyId(), new RoleCode(entity.getCode()),
                entity.getName(), entity.isSystem(), permissionIds);
    }
}
