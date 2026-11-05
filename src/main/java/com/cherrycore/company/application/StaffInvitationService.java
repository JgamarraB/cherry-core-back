package com.cherrycore.company.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyNotifier;
import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.application.port.CompanyRoleRepository;
import com.cherrycore.company.application.port.MembershipRepository;
import com.cherrycore.company.application.port.StaffInvitationRepository;
import com.cherrycore.company.application.port.StaffUserRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyRole;
import com.cherrycore.company.domain.CompanyStatus;
import com.cherrycore.company.domain.Membership;
import com.cherrycore.company.domain.MembershipStatus;
import com.cherrycore.company.domain.StaffInvitation;
import com.cherrycore.company.domain.StaffUser;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.AccountRegistrationService;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.application.port.SecureTokenService;
import com.cherrycore.identity.application.port.SecureTokenService.GeneratedToken;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.EmailAddress;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.domain.value.PersonName;
import com.cherrycore.shared.domain.value.PhoneNumber;

/**
 * Invitaciones de personal a una empresa.
 */
@Service
public class StaffInvitationService {

    private final CompanyAccessPolicy access;
    private final StaffInvitationRepository invitations;
    private final CompanyRoleRepository roles;
    private final MembershipRepository memberships;
    private final CompanyRepository companies;
    private final StaffUserRepository staffUsers;
    private final AccountQueryService accounts;
    private final AccountRegistrationService accountRegistration;
    private final SecureTokenService tokens;
    private final CompanyNotifier notifier;
    private final CompanyLogos logos;
    private final CompanySettings settings;
    private final CompanyAudit audit;
    private final Clock clock;

    public StaffInvitationService(CompanyAccessPolicy access, StaffInvitationRepository invitations,
            CompanyRoleRepository roles, MembershipRepository memberships, CompanyRepository companies,
            StaffUserRepository staffUsers, AccountQueryService accounts,
            AccountRegistrationService accountRegistration, SecureTokenService tokens, CompanyNotifier notifier,
            CompanyLogos logos, CompanySettings settings, CompanyAudit audit, Clock clock) {
        this.access = access;
        this.invitations = invitations;
        this.roles = roles;
        this.memberships = memberships;
        this.companies = companies;
        this.staffUsers = staffUsers;
        this.accounts = accounts;
        this.accountRegistration = accountRegistration;
        this.tokens = tokens;
        this.notifier = notifier;
        this.logos = logos;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public InvitationView invite(StaffActor actor, String rawEmail, UUID roleId, RequestContext request) {
        CompanyContext context = access.require(actor, CompanyPermission.MEMBERS_MANAGE);
        Company company = context.company();
        EmailAddress email = EmailAddress.of(rawEmail);
        CompanyRole role = roles.findById(roleId)
                .filter(candidate -> candidate.companyId().equals(company.id()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.ROLE_NOT_FOUND));
        if (role.isOwner() && !context.isOwner()) {
            throw new DomainException(CompanyErrorCode.OWNER_ROLE_RESERVED);
        }
        accounts.findByTypeAndEmail(AccountType.STAFF, email)
                .flatMap(account -> memberships.findByCompanyAndStaffUser(company.id(), account.id()))
                .ifPresent(existing -> {
                    throw new DomainException(CompanyErrorCode.ALREADY_MEMBER);
                });

        Instant now = clock.instant();
        // Una sola invitación vigente por email: la anterior deja de valer.
        invitations.findByCompanyAndEmail(company.id(), email).forEach(previous -> {
            previous.revoke(now);
            invitations.save(previous);
        });

        GeneratedToken token = tokens.generate();
        StaffInvitation invitation = new StaffInvitation(UuidV7.generate(clock), company.id(), email, role.id(),
                token.hash(), now.plus(settings.invitationTtl()), null, null);
        invitations.save(invitation);

        Account inviter = accounts.getActive(actor.accountId());
        notifier.invitationSent(invitation, company, role.name(), token.rawValue(), inviter.locale());
        audit.event(AuthEventType.INVITATION_SENT, actor.accountId(), company)
                .detail(AuditDetail.EMAIL, email.value())
                .detail(AuditDetail.ROLE, role.code().value())
                .log(request);
        return new InvitationView(invitation.id(), email.value(), role.id(), role.name(), invitation.expiresAt());
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listPending(StaffActor actor) {
        UUID companyId = access.require(actor, CompanyPermission.MEMBERS_VIEW).company().id();
        Instant now = clock.instant();
        Map<UUID, CompanyRole> rolesById = roles.findByCompany(companyId).stream()
                .collect(Collectors.toMap(CompanyRole::id, Function.identity()));
        return invitations.findByCompany(companyId).stream()
                .filter(invitation -> invitation.isPendingAt(now))
                .sorted(Comparator.comparing(StaffInvitation::expiresAt))
                .map(invitation -> new InvitationView(invitation.id(), invitation.email().value(),
                        invitation.roleId(), rolesById.get(invitation.roleId()).name(), invitation.expiresAt()))
                .toList();
    }

    @Transactional
    public void revoke(StaffActor actor, UUID invitationId, RequestContext request) {
        Company company = access.require(actor, CompanyPermission.MEMBERS_MANAGE).company();
        UUID companyId = company.id();
        StaffInvitation invitation = invitations.findById(invitationId)
                .filter(candidate -> candidate.companyId().equals(companyId))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVITATION_NOT_FOUND));
        invitation.revoke(clock.instant());
        invitations.save(invitation);
        audit.event(AuthEventType.INVITATION_REVOKED, actor.accountId(), company)
                .detail(AuditDetail.EMAIL, invitation.email().value())
                .log(request);
    }

    @Transactional(readOnly = true)
    public InvitationInfo info(String rawToken) {
        StaffInvitation invitation = invitations.findByTokenHash(tokens.hash(rawToken))
                .filter(candidate -> candidate.isPendingAt(clock.instant()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVALID_OR_EXPIRED_INVITATION));
        Company company = activeCompany(invitation);
        CompanyRole role = roles.findById(invitation.roleId())
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVALID_OR_EXPIRED_INVITATION));
        boolean exists = accounts.findByTypeAndEmail(AccountType.STAFF, invitation.email()).isPresent();
        return new InvitationInfo(company.profile().tradeName().value(), logos.urlOf(company),
                invitation.email().value(), role.name(), exists);
    }

    /**
     * Aceptar creando la cuenta (no tenía cuenta de backoffice).
     */
    @Transactional
    public UUID acceptWithNewAccount(AcceptInvitationCommand command, RequestContext context) {
        StaffInvitation invitation = pending(command.rawToken());
        activeCompany(invitation);
        if (accounts.findByTypeAndEmail(AccountType.STAFF, invitation.email()).isPresent()) {
            // Ya tiene cuenta: debe iniciar sesión y aceptar con ella.
            throw new DomainException(CompanyErrorCode.INVITATION_FOR_ANOTHER_ACCOUNT);
        }
        PersonName firstName = PersonName.of(command.firstName());
        PersonName lastName = PersonName.of(command.lastName());
        PhoneNumber phone = command.phone() == null || command.phone().isBlank()
                ? null : PhoneNumber.of(command.phone());
        Account account = accountRegistration.createInvitedStaff(invitation.email(), command.password(),
                command.locale(), context);
        staffUsers.save(new StaffUser(account.id(), firstName, lastName, phone));
        join(invitation, account.id(), context);
        return account.id();
    }

    /**
     * Aceptar con la cuenta con la que ya ha iniciado sesión.
     */
    @Transactional
    public void acceptWithExistingAccount(String rawToken, UUID accountId, RequestContext context) {
        StaffInvitation invitation = pending(rawToken);
        activeCompany(invitation);
        Account account = accounts.getActive(accountId);
        if (account.type() != AccountType.STAFF || !account.email().equals(invitation.email())) {
            throw new DomainException(CompanyErrorCode.INVITATION_FOR_ANOTHER_ACCOUNT);
        }
        if (memberships.findByCompanyAndStaffUser(invitation.companyId(), accountId).isPresent()) {
            throw new DomainException(CompanyErrorCode.ALREADY_MEMBER);
        }
        join(invitation, accountId, context);
    }

    private void join(StaffInvitation invitation, UUID accountId, RequestContext context) {
        invitation.accept(clock.instant());
        invitations.save(invitation);
        memberships.save(new Membership(UuidV7.generate(clock), accountId, invitation.companyId(),
                invitation.roleId(), MembershipStatus.ACTIVE));
        Company company = activeCompany(invitation);
        String roleCode = roles.findById(invitation.roleId()).map(role -> role.code().value()).orElse(null);
        audit.event(AuthEventType.INVITATION_ACCEPTED, accountId, company)
                .detail(AuditDetail.ROLE, roleCode)
                .log(context);
    }

    private StaffInvitation pending(String rawToken) {
        return invitations.findByTokenHashForUpdate(tokens.hash(rawToken))
                .filter(invitation -> invitation.isPendingAt(clock.instant()))
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVALID_OR_EXPIRED_INVITATION));
    }

    private Company activeCompany(StaffInvitation invitation) {
        return companies.findById(invitation.companyId())
                .filter(company -> company.status() != CompanyStatus.REJECTED)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.INVALID_OR_EXPIRED_INVITATION));
    }
}
