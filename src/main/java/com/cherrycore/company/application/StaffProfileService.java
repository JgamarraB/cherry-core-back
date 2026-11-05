package com.cherrycore.company.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Perfil propio de una persona de backoffice.
 */
@Service
public class StaffProfileService {

    private final StaffUserRepository staffUsers;

    public StaffProfileService(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    @Transactional(readOnly = true)
    public StaffUser get(UUID accountId) {
        return staffUsers.findById(accountId)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.STAFF_PROFILE_NOT_FOUND));
    }

    @Transactional
    public StaffUser update(UUID accountId, String firstName, String lastName, String phone) {
        StaffUser profile = get(accountId);
        profile.updateProfile(PersonName.of(firstName), PersonName.of(lastName),
                phone == null || phone.isBlank() ? null : PhoneNumber.of(phone));
        staffUsers.save(profile);
        return profile;
    }
}
