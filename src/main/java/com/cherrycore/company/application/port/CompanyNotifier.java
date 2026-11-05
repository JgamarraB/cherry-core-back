package com.cherrycore.company.application.port;

import java.util.List;
import java.util.Locale;

import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.StaffInvitation;

/**
 * Emails relacionados con empresas.
 */
public interface CompanyNotifier {

    /**
     * Aviso a los superadmins: hay una empresa pendiente de revisar.
     */
    void companyPendingReview(Company company, List<Recipient> superAdmins);

    void companyApproved(Company company, List<Recipient> owners);

    void companyRejected(Company company, List<Recipient> owners);

    void invitationSent(StaffInvitation invitation, Company company, String roleName, String rawToken,
            Locale locale);

    record Recipient(String email, Locale locale) {
    }
}
