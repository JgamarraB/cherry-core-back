package com.cherrycore.company.infrastructure.notification;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.cherrycore.company.application.CompanyLogos;
import com.cherrycore.company.application.port.CompanyNotifier;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.StaffInvitation;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.mail.EmailBranding;
import com.cherrycore.shared.infrastructure.mail.EmailRequested;

/**
 * Emails de empresas. Los que van al personal de una empresa llevan su
 * marca (logo o, si no tiene, su nombre comercial).
 */
@Component
class CompanyEmailNotifier implements CompanyNotifier {

    private static final String PENDING_REVIEW = "email.company-pending-review";
    private static final String APPROVED = "email.company-approved";
    private static final String REJECTED = "email.company-rejected";
    private static final String INVITATION = "email.staff-invitation";
    private static final String OWNERSHIP = "email.ownership-transferred";
    private static final String OWNERSHIP_NO_NAME = "email.ownership-transferred-anonymous";
    private static final String TOKEN_PARAM = "token";
    private static final String COMPANIES_PATH = "/companies/";

    private final ApplicationEventPublisher publisher;
    private final CompanyLogos logos;
    private final CherryCoreProperties properties;

    CompanyEmailNotifier(ApplicationEventPublisher publisher, CompanyLogos logos, CherryCoreProperties properties) {
        this.publisher = publisher;
        this.logos = logos;
        this.properties = properties;
    }

    @Override
    public void companyPendingReview(Company company, List<Recipient> superAdmins) {
        String reviewUrl = UriComponentsBuilder.fromUri(properties.frontend().adminUrl())
                .path(COMPANIES_PATH + company.id()).build().toUriString();
        superAdmins.forEach(admin -> publisher.publishEvent(new EmailRequested(PENDING_REVIEW, admin.email(),
                admin.locale(), null, reviewUrl,
                List.of(company.profile().tradeName().value(), company.profile().taxId().value()))));
    }

    @Override
    public void companyApproved(Company company, List<Recipient> owners) {
        owners.forEach(owner -> publisher.publishEvent(new EmailRequested(APPROVED, owner.email(), owner.locale(),
                branding(company), properties.frontend().loginUrl().toString(),
                List.of(company.profile().tradeName().value()))));
    }

    @Override
    public void companyRejected(Company company, List<Recipient> owners) {
        owners.forEach(owner -> publisher.publishEvent(new EmailRequested(REJECTED, owner.email(), owner.locale(),
                branding(company), null,
                List.of(company.profile().tradeName().value(), company.rejectionReason()))));
    }

    @Override
    public void invitationSent(StaffInvitation invitation, Company company, String roleName, String rawToken,
            Locale locale) {
        URI base = properties.frontend().invitationUrl();
        String link = UriComponentsBuilder.fromUri(base).queryParam(TOKEN_PARAM, rawToken).build().toUriString();
        publisher.publishEvent(new EmailRequested(INVITATION, invitation.email().value(), locale, branding(company),
                link, List.of(company.profile().tradeName().value(), roleName,
                        properties.companies().invitationTtl().toDays())));
    }

    @Override
    public void ownershipTransferred(Company company, Recipient newOwner, String previousOwnerName) {
        boolean named = previousOwnerName != null && !previousOwnerName.isBlank();
        publisher.publishEvent(new EmailRequested(named ? OWNERSHIP : OWNERSHIP_NO_NAME, newOwner.email(),
                newOwner.locale(), branding(company), properties.frontend().loginUrl().toString(),
                named ? List.of(company.profile().tradeName().value(), previousOwnerName)
                        : List.of(company.profile().tradeName().value())));
    }

    private EmailBranding branding(Company company) {
        return new EmailBranding(company.profile().tradeName().value(), logos.urlOf(company));
    }
}
