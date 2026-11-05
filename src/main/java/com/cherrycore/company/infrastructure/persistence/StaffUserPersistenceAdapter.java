package com.cherrycore.company.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

@Repository
class StaffUserPersistenceAdapter implements StaffUserRepository {

    private final StaffUserJpaRepository staffUsers;

    StaffUserPersistenceAdapter(StaffUserJpaRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    @Override
    public Optional<StaffUser> findById(UUID id) {
        return staffUsers.findById(id).map(StaffUserPersistenceAdapter::toDomain);
    }

    @Override
    public List<StaffUser> findByIds(Collection<UUID> ids) {
        return staffUsers.findAllById(ids).stream().map(StaffUserPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void save(StaffUser staffUser) {
        StaffUserJpaEntity entity = staffUsers.findById(staffUser.id())
                .orElseGet(() -> new StaffUserJpaEntity(staffUser.id()));
        entity.setFirstName(staffUser.firstName().value());
        entity.setLastName(staffUser.lastName().value());
        entity.setPhone(staffUser.phone() == null ? null : staffUser.phone().value());
        staffUsers.save(entity);
    }

    private static StaffUser toDomain(StaffUserJpaEntity entity) {
        return new StaffUser(entity.getId(), new PersonName(entity.getFirstName()),
                new PersonName(entity.getLastName()),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()));
    }
}
