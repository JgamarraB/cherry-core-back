package com.cherrycore.company.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.Membership;

public interface MembershipRepository {

    Optional<Membership> findById(UUID id);

    Optional<Membership> findByCompanyAndStaffUser(UUID companyId, UUID staffUserId);

    List<Membership> findByStaffUser(UUID staffUserId);

    List<Membership> findByCompany(UUID companyId);

    long countByRole(UUID roleId);

    long countActiveByRole(UUID roleId);

    void save(Membership membership);

    void delete(Membership membership);
}
