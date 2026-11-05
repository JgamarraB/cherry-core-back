package com.cherrycore.catalog.application.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.cherrycore.catalog.domain.ApplicationPermission;

public interface PermissionRepository {

    List<ApplicationPermission> findByApplication(UUID applicationId);

    List<ApplicationPermission> findByApplications(Collection<UUID> applicationIds);

    List<ApplicationPermission> findByIds(Collection<UUID> ids);

    void save(ApplicationPermission permission);

    /**
     * Borrado lógico del permiso y de sus asignaciones a roles.
     */
    void retire(ApplicationPermission permission);
}
