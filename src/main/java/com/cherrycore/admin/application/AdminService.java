package com.cherrycore.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.admin.application.port.AdminReadModel;
import com.cherrycore.company.application.MyCompanyView;
import com.cherrycore.company.application.StaffTenantService;
import com.cherrycore.identity.application.AccountQueryService;
import com.cherrycore.identity.application.MfaService;
import com.cherrycore.identity.application.MfaStatus;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Consultas del panel de superadmin.
 */
@Service
public class AdminService {

    private static final Duration DASHBOARD_WINDOW = Duration.ofHours(24);
    private static final int RECENT_EVENTS = 20;

    private final AdminReadModel readModel;
    private final AccountQueryService accounts;
    private final MfaService mfa;
    private final StaffTenantService tenants;
    private final Clock clock;

    public AdminService(AdminReadModel readModel, AccountQueryService accounts, MfaService mfa,
            StaffTenantService tenants, Clock clock) {
        this.readModel = readModel;
        this.accounts = accounts;
        this.mfa = mfa;
        this.tenants = tenants;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        return readModel.dashboard(clock.instant().minus(DASHBOARD_WINDOW));
    }

    @Transactional(readOnly = true)
    public PageResult<AccountSummary> searchAccounts(String type, String status, String search, PageQuery page) {
        return readModel.searchAccounts(type, status, blankToNull(search), page);
    }

    @Transactional(readOnly = true)
    public AccountDetail accountDetail(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new DomainException(IdentityErrorCode.ACCOUNT_NOT_FOUND));
        List<MyCompanyView> companies = account.type() == AccountType.STAFF
                ? tenants.myCompanies(accountId)
                : List.of();
        List<AuditEventView> recentEvents = readModel.searchAuditEvents(accountId, null, null, null,
                new PageQuery(0, RECENT_EVENTS)).items();
        return new AccountDetail(account.id(), account.type().name(), account.email().value(),
                account.status().name(), account.emailVerifiedAt(), account.isSuperAdmin(), account.lockedUntil(),
                account.lastLoginAt(), account.locale().toLanguageTag(), mfa.status(accountId), companies,
                recentEvents);
    }

    @Transactional(readOnly = true)
    public PageResult<AuditEventView> auditEvents(UUID accountId, String type, Instant from, Instant to,
            PageQuery page) {
        return readModel.searchAuditEvents(accountId, blankToNull(type), from, to, page);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record AccountDetail(
            UUID id,
            String type,
            String email,
            String status,
            Instant emailVerifiedAt,
            boolean superAdmin,
            Instant lockedUntil,
            Instant lastLoginAt,
            String locale,
            MfaStatus mfa,
            List<MyCompanyView> companies,
            List<AuditEventView> recentEvents
    ) {
    }
}
