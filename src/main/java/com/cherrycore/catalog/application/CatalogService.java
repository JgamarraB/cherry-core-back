package com.cherrycore.catalog.application;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.catalog.application.port.ApplicationRepository;
import com.cherrycore.catalog.application.port.PermissionRepository;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationCode;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.catalog.domain.ApplicationStatus;
import com.cherrycore.catalog.domain.CatalogErrorCode;
import com.cherrycore.catalog.domain.PermissionCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Catálogo de apps y permisos.
 *
 * <p>Las apps sincronizan su lista de permisos: lo nuevo se crea, lo que ya
 * existe se actualiza y lo que ya no envían se retira (y se quita de los roles).</p>
 */
@Service
public class CatalogService {

    public static final int MAX_PERMISSIONS_PER_APPLICATION = 500;

    private final ApplicationRepository applications;
    private final PermissionRepository permissions;
    private final Clock clock;

    public CatalogService(ApplicationRepository applications, PermissionRepository permissions, Clock clock) {
        this.applications = applications;
        this.permissions = permissions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Application> listApplications() {
        return applications.findAll();
    }

    @Transactional(readOnly = true)
    public Application get(UUID applicationId) {
        return applications.findById(applicationId)
                .orElseThrow(() -> new DomainException(CatalogErrorCode.APPLICATION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Optional<Application> findByCode(String code) {
        return applications.findByCode(new ApplicationCode(code));
    }

    @Transactional
    public Application create(String code, String name) {
        ApplicationCode applicationCode = new ApplicationCode(code);
        if (applications.findByCode(applicationCode).isPresent()) {
            throw new DomainException(CatalogErrorCode.APPLICATION_CODE_TAKEN);
        }
        Application application = new Application(UuidV7.generate(clock), applicationCode, name,
                ApplicationStatus.ACTIVE);
        applications.save(application);
        return application;
    }

    @Transactional
    public Application update(UUID applicationId, String name, boolean active) {
        Application application = get(applicationId);
        application.rename(name);
        if (active) {
            application.enable();
        } else {
            application.disable();
        }
        applications.save(application);
        return application;
    }

    /**
     * Crea la app si no existe (la usa CherryCore para registrarse a sí misma).
     */
    @Transactional
    public Application ensureApplication(String code, String name) {
        return applications.findByCode(new ApplicationCode(code)).orElseGet(() -> create(code, name));
    }

    @Transactional(readOnly = true)
    public List<ApplicationPermission> permissionsOf(UUID applicationId) {
        return permissions.findByApplication(applicationId);
    }

    @Transactional(readOnly = true)
    public List<ApplicationPermission> permissionsOf(Collection<UUID> applicationIds) {
        return applicationIds.isEmpty() ? List.of() : permissions.findByApplications(applicationIds);
    }

    @Transactional(readOnly = true)
    public List<ApplicationPermission> permissionsByIds(Collection<UUID> permissionIds) {
        return permissionIds.isEmpty() ? List.of() : permissions.findByIds(permissionIds);
    }

    @Transactional
    public List<ApplicationPermission> syncPermissions(String applicationCode, List<PermissionDefinition> declared) {
        if (declared.size() > MAX_PERMISSIONS_PER_APPLICATION) {
            throw new DomainException(CatalogErrorCode.TOO_MANY_PERMISSIONS);
        }
        Application application = applications.findByCode(new ApplicationCode(applicationCode))
                .orElseThrow(() -> new DomainException(CatalogErrorCode.APPLICATION_NOT_FOUND));

        Map<String, PermissionDefinition> wanted = declared.stream()
                .collect(Collectors.toMap(definition -> new PermissionCode(definition.code()).value(),
                        Function.identity(), (first, duplicate) -> first));
        Map<String, ApplicationPermission> current = permissions.findByApplication(application.id()).stream()
                .collect(Collectors.toMap(permission -> permission.code().value(), Function.identity()));

        current.values().stream()
                .filter(existing -> !wanted.containsKey(existing.code().value()))
                .forEach(permissions::retire);

        wanted.forEach((code, definition) -> {
            ApplicationPermission existing = current.get(code);
            UUID id = existing == null ? UuidV7.generate(clock) : existing.id();
            permissions.save(new ApplicationPermission(id, application.id(), new PermissionCode(code),
                    truncate(definition.description())));
        });
        return permissions.findByApplication(application.id());
    }

    private static String truncate(String description) {
        if (description == null) {
            return null;
        }
        String stripped = description.strip();
        return stripped.length() > ApplicationPermission.MAX_DESCRIPTION_LENGTH
                ? stripped.substring(0, ApplicationPermission.MAX_DESCRIPTION_LENGTH)
                : stripped;
    }
}
