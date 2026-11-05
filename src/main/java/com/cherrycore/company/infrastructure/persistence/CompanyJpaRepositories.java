package com.cherrycore.company.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.MembershipStatus;

import jakarta.persistence.LockModeType;

interface CompanyJpaRepository extends JpaRepository<CompanyJpaEntity, UUID>,
        JpaSpecificationExecutor<CompanyJpaEntity> {

    boolean existsByCountryCodeAndTaxId(String countryCode, String taxId);

    boolean existsByCountryCodeAndTaxIdAndIdNot(String countryCode, String taxId, UUID id);

    long countByStatus(CompanyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompanyJpaEntity> findWithLockById(UUID id);
}

interface CompanyRoleJpaRepository extends JpaRepository<CompanyRoleJpaEntity, UUID> {

    List<CompanyRoleJpaEntity> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    Optional<CompanyRoleJpaEntity> findByCompanyIdAndCode(UUID companyId, String code);
}

interface RolePermissionJpaRepository extends JpaRepository<RolePermissionJpaEntity, UUID> {

    List<RolePermissionJpaEntity> findByRoleId(UUID roleId);

    List<RolePermissionJpaEntity> findByRoleIdIn(List<UUID> roleIds);
}

interface MembershipJpaRepository extends JpaRepository<MembershipJpaEntity, UUID> {

    Optional<MembershipJpaEntity> findByCompanyIdAndStaffUserId(UUID companyId, UUID staffUserId);

    List<MembershipJpaEntity> findByStaffUserId(UUID staffUserId);

    List<MembershipJpaEntity> findByCompanyIdOrderByCreatedAtAsc(UUID companyId);

    long countByRoleId(UUID roleId);

    long countByRoleIdAndStatus(UUID roleId, MembershipStatus status);
}

interface StaffUserJpaRepository extends JpaRepository<StaffUserJpaEntity, UUID> {
}

interface StaffInvitationJpaRepository extends JpaRepository<StaffInvitationJpaEntity, UUID> {

    Optional<StaffInvitationJpaEntity> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StaffInvitationJpaEntity> findWithLockByTokenHash(String tokenHash);

    List<StaffInvitationJpaEntity> findByCompanyId(UUID companyId);

    List<StaffInvitationJpaEntity> findByCompanyIdAndEmail(UUID companyId, String email);
}

interface CompanyApplicationJpaRepository extends JpaRepository<CompanyApplicationJpaEntity, UUID> {

    List<CompanyApplicationJpaEntity> findByCompanyId(UUID companyId);

    Optional<CompanyApplicationJpaEntity> findByCompanyIdAndApplicationId(UUID companyId, UUID applicationId);
}
