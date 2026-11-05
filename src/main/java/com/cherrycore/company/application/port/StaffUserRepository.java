package com.cherrycore.company.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.StaffUser;

public interface StaffUserRepository {

    Optional<StaffUser> findById(UUID id);

    List<StaffUser> findByIds(Collection<UUID> ids);

    void save(StaffUser staffUser);
}
