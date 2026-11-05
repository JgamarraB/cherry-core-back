package com.cherrycore.catalog.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ApplicationJpaRepository extends JpaRepository<ApplicationJpaEntity, UUID> {

    Optional<ApplicationJpaEntity> findByCode(String code);

    List<ApplicationJpaEntity> findAllByOrderByNameAsc();
}

interface ApplicationPermissionJpaRepository extends JpaRepository<ApplicationPermissionJpaEntity, UUID> {

    List<ApplicationPermissionJpaEntity> findByApplicationIdOrderByCodeAsc(UUID applicationId);

    List<ApplicationPermissionJpaEntity> findByApplicationIdInOrderByCodeAsc(Collection<UUID> applicationIds);
}
