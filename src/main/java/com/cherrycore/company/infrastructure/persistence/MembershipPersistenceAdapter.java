package com.cherrycore.company.infrastructure.persistence;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.MembershipStatus;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;

@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

    private final MembershipJpaRepository memberships;
    private final Clock clock;

    MembershipPersistenceAdapter(MembershipJpaRepository memberships, Clock clock) {
        this.memberships = memberships;
        this.clock = clock;
    }

    @Override
    public Optional<Membership> findById(UUID id) {
        return memberships.findById(id).map(MembershipPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Membership> findByCompanyAndStaffUser(UUID companyId, UUID staffUserId) {
        return memberships.findByCompanyIdAndStaffUserId(companyId, staffUserId)
                .map(MembershipPersistenceAdapter::toDomain);
    }

    @Override
    public List<Membership> findByStaffUser(UUID staffUserId) {
        return memberships.findByStaffUserId(staffUserId).stream().map(MembershipPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public List<Membership> findByCompany(UUID companyId) {
        return memberships.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
                .map(MembershipPersistenceAdapter::toDomain).toList();
    }

    @Override
    public long countByRole(UUID roleId) {
        return memberships.countByRoleId(roleId);
    }

    @Override
    public long countActiveByRole(UUID roleId) {
        return memberships.countByRoleIdAndStatus(roleId, MembershipStatus.ACTIVE);
    }

    @Override
    public void save(Membership membership) {
        MembershipJpaEntity entity = memberships.findById(membership.id())
                .orElseGet(() -> new MembershipJpaEntity(membership.id(), membership.staffUserId(),
                        membership.companyId()));
        entity.setRoleId(membership.roleId());
        entity.setStatus(membership.status());
        memberships.saveAndFlush(entity);
    }

    @Override
    public void delete(Membership membership) {
        memberships.findById(membership.id()).ifPresent(entity -> {
            entity.markDeleted(clock.instant(), CurrentAccount.id().orElse(null));
            memberships.saveAndFlush(entity);
        });
    }

    private static Membership toDomain(MembershipJpaEntity entity) {
        return new Membership(entity.getId(), entity.getStaffUserId(), entity.getCompanyId(), entity.getRoleId(),
                entity.getStatus());
    }
}
