package com.cherrycore.company.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cherrycore.company.domain.StaffInvitation;
import com.cherrycore.identity.domain.EmailAddress;

public interface StaffInvitationRepository {

    Optional<StaffInvitation> findById(UUID id);

    Optional<StaffInvitation> findByTokenHash(String tokenHash);

    Optional<StaffInvitation> findByTokenHashForUpdate(String tokenHash);

    List<StaffInvitation> findByCompany(UUID companyId);

    List<StaffInvitation> findByCompanyAndEmail(UUID companyId, EmailAddress email);

    void save(StaffInvitation invitation);
}
