package com.cherrycore.company.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyNotifier;
import com.cherrycore.company.application.port.CompanyNotifier.Recipient;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.AccountRegistrationService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Alta de empresas. Quedan pendientes de aprobación por un superadmin.
 */
@Service
public class CompanyRegistrationService {

    private final AccountRegistrationService accountRegistration;
    private final AccountQueryService accounts;
    private final StaffUserRepository staffUsers;
    private final CompanySetup setup;
    private final CompanyNotifier notifier;

    public CompanyRegistrationService(AccountRegistrationService accountRegistration, AccountQueryService accounts,
            StaffUserRepository staffUsers, CompanySetup setup, CompanyNotifier notifier) {
        this.accountRegistration = accountRegistration;
        this.accounts = accounts;
        this.staffUsers = staffUsers;
        this.setup = setup;
        this.notifier = notifier;
    }

    /**
     * Alta pública (formulario de registro): crea la cuenta del dueño y la empresa.
     * Si el email ya tiene cuenta de backoffice no se crea nada y se avisa al
     * dueño real; la respuesta es la misma (no revela qué emails existen).
     */
    @Transactional
    public void registerWithNewOwner(RegisterCompanyCommand command, RequestContext context) {
        if (!command.termsAccepted()) {
            throw new DomainException(CommonErrorCode.TERMS_NOT_ACCEPTED);
        }
        CompanyProfileData profile = command.company().toProfile();
        PersonName firstName = PersonName.of(command.ownerFirstName());
        PersonName lastName = PersonName.of(command.ownerLastName());
        PhoneNumber phone = command.ownerPhone() == null || command.ownerPhone().isBlank()
                ? null : PhoneNumber.of(command.ownerPhone());

        accountRegistration.registerStaff(command.ownerEmail(), command.ownerPassword(), command.locale(), context)
                .ifPresent(account -> {
                    staffUsers.save(new StaffUser(account.id(), firstName, lastName, phone));
                    notifyReview(setup.create(account.id(), profile));
                });
    }

    /**
     * Una persona de backoffice que ya tiene cuenta registra otra empresa.
     */
    @Transactional
    public Company registerForExistingStaff(UUID accountId, CompanyDataCommand data) {
        Account account = accounts.getActive(accountId);
        if (account.type() != AccountType.STAFF) {
            throw new DomainException(CompanyErrorCode.COMPANY_PERMISSION_DENIED);
        }
        Company company = setup.create(accountId, data.toProfile());
        notifyReview(company);
        return company;
    }

    private void notifyReview(Company company) {
        List<Recipient> superAdmins = accounts.superAdmins().stream()
                .map(admin -> new Recipient(admin.email().value(), admin.locale()))
                .toList();
        if (!superAdmins.isEmpty()) {
            notifier.companyPendingReview(company, superAdmins);
        }
    }
}
