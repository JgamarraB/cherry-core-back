package com.cherrycore.catalog.infrastructure.persistence;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.cherrycore.catalog.application.port.ApplicationRepository;
import com.cherrycore.catalog.application.port.PermissionRepository;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationCode;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.catalog.domain.PermissionCode;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class CatalogPersistenceAdapter implements ApplicationRepository, PermissionRepository {

    private static final String RETIRE_ROLE_ASSIGNMENTS_SQL = """
            UPDATE company_role_permissions
               SET deleted_at = :now, deleted_by = :actor
             WHERE permission_id = :permissionId AND deleted_at IS NULL
            """;

    private final ApplicationJpaRepository applications;
    private final ApplicationPermissionJpaRepository permissions;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    CatalogPersistenceAdapter(ApplicationJpaRepository applications, ApplicationPermissionJpaRepository permissions,
            JdbcClient jdbcClient, Clock clock) {
        this.applications = applications;
        this.permissions = permissions;
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    @Override
    public Optional<Application> findById(UUID id) {
        return applications.findById(id).map(CatalogPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Application> findByCode(ApplicationCode code) {
        return applications.findByCode(code.value()).map(CatalogPersistenceAdapter::toDomain);
    }

    @Override
    public List<Application> findAll() {
        return applications.findAllByOrderByNameAsc().stream().map(CatalogPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void save(Application application) {
        ApplicationJpaEntity entity = applications.findById(application.id())
                .orElseGet(() -> new ApplicationJpaEntity(application.id(), application.code().value()));
        entity.setName(application.name());
        entity.setStatus(application.status());
        applications.save(entity);
    }

    @Override
    public List<ApplicationPermission> findByApplication(UUID applicationId) {
        return permissions.findByApplicationIdOrderByCodeAsc(applicationId).stream()
                .map(CatalogPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<ApplicationPermission> findByApplications(Collection<UUID> applicationIds) {
        return permissions.findByApplicationIdInOrderByCodeAsc(applicationIds).stream()
                .map(CatalogPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<ApplicationPermission> findByIds(Collection<UUID> ids) {
        return permissions.findAllById(ids).stream().map(CatalogPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void save(ApplicationPermission permission) {
        ApplicationPermissionJpaEntity entity = permissions.findById(permission.id())
                .orElseGet(() -> new ApplicationPermissionJpaEntity(permission.id(), permission.applicationId(),
                        permission.code().value()));
        entity.setDescription(permission.description());
        permissions.save(entity);
    }

    @Override
    public void retire(ApplicationPermission permission) {
        UUID actor = CurrentAccount.id().orElse(null);
        permissions.findById(permission.id()).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), actor);
            permissions.saveAndFlush(entity);
        });
        jdbcClient.sql(RETIRE_ROLE_ASSIGNMENTS_SQL)
                .param("now", java.sql.Timestamp.from(clock.instant()))
                .param("actor", actor)
                .param("permissionId", permission.id())
                .update();
    }

    private static Application toDomain(ApplicationJpaEntity entity) {
        return new Application(entity.getId(), new ApplicationCode(entity.getCode()), entity.getName(),
                entity.getStatus());
    }

    private static ApplicationPermission toDomain(ApplicationPermissionJpaEntity entity) {
        return new ApplicationPermission(entity.getId(), entity.getApplicationId(),
                new PermissionCode(entity.getCode()), entity.getDescription());
    }
}
