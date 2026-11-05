package com.cherrycore.company.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.company.application.port.StaffInvitationRepository;
import com.cherrycore.company.domain.StaffInvitation;
import com.cherrycore.identity.domain.EmailAddress;

@Repository
class InvitationPersistenceAdapter implements StaffInvitationRepository {

    private final StaffInvitationJpaRepository invitations;

    InvitationPersistenceAdapter(StaffInvitationJpaRepository invitations) {
        this.invitations = invitations;
    }

    @Override
    public Optional<StaffInvitation> findById(UUID id) {
        return invitations.findById(id).map(InvitationPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<StaffInvitation> findByTokenHash(String tokenHash) {
        return invitations.findByTokenHash(tokenHash).map(InvitationPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<StaffInvitation> findByTokenHashForUpdate(String tokenHash) {
        return invitations.findWithLockByTokenHash(tokenHash).map(InvitationPersistenceAdapter::toDomain);
    }

    @Override
    public List<StaffInvitation> findByCompany(UUID companyId) {
        return invitations.findByCompanyId(companyId).stream().map(InvitationPersistenceAdapter::toDomain).toList();
    }

    @Override
    public List<StaffInvitation> findByCompanyAndEmail(UUID companyId, EmailAddress email) {
        return invitations.findByCompanyIdAndEmail(companyId, email.value()).stream()
                .map(InvitationPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void save(StaffInvitation invitation) {
        StaffInvitationJpaEntity entity = invitations.findById(invitation.id())
                .orElseGet(() -> new StaffInvitationJpaEntity(invitation.id(), invitation.companyId(),
                        invitation.email().value(), invitation.roleId(), invitation.tokenHash(),
                        invitation.expiresAt()));
        entity.setAcceptedAt(invitation.acceptedAt());
        entity.setRevokedAt(invitation.revokedAt());
        invitations.save(entity);
    }

    private static StaffInvitation toDomain(StaffInvitationJpaEntity entity) {
        return new StaffInvitation(entity.getId(), entity.getCompanyId(), new EmailAddress(entity.getEmail()),
                entity.getRoleId(), entity.getTokenHash(), entity.getExpiresAt(), entity.getAcceptedAt(),
                entity.getRevokedAt());
    }
}
