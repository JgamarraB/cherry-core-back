package com.cherrycore.auth.infrastructure.web;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cherrycore.company.application.CompanyMembersService;
import com.cherrycore.company.application.StaffDataExport;
import com.cherrycore.company.application.StaffDataExportService;
import com.cherrycore.customer.application.CustomerDataExport;
import com.cherrycore.customer.application.CustomerDataExportService;
import com.cherrycore.identity.application.IdentityDataExport;
import com.cherrycore.identity.application.IdentityDataExport.AccountData;
import com.cherrycore.identity.application.IdentityDataExport.LinkedIdentityData;
import com.cherrycore.identity.application.IdentityDataExport.SecurityEventData;
import com.cherrycore.identity.application.IdentityDataExportService;
import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.infrastructure.security.AccountSessions;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.security.CurrentAccount;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Mi cuenta": descargar mis datos (RGPD, derecho de acceso) y salir de una empresa.
 */
@RestController
@RequestMapping(AccountController.BASE)
public class AccountDataController {

    public static final String EXPORT = "/export";
    public static final String LEAVE_COMPANY = "/companies/{companyId}/leave";

    private static final String EXPORT_FILE_NAME = "cherrycore-data-%s.json";

    private final IdentityDataExportService identityExport;
    private final CustomerDataExportService customerExport;
    private final StaffDataExportService staffExport;
    private final CompanyMembersService members;
    private final AccountSessions sessions;
    private final SessionLoginHandler sessionHandler;
    private final Clock clock;

    AccountDataController(IdentityDataExportService identityExport, CustomerDataExportService customerExport,
            StaffDataExportService staffExport, CompanyMembersService members, AccountSessions sessions,
            SessionLoginHandler sessionHandler, Clock clock) {
        this.identityExport = identityExport;
        this.customerExport = customerExport;
        this.staffExport = staffExport;
        this.members = members;
        this.sessions = sessions;
        this.sessionHandler = sessionHandler;
        this.clock = clock;
    }

    /**
     * Todos mis datos en un JSON para descargar. Sin hash de contraseña, secreto
     * MFA ni tokens. Cada módulo aporta lo suyo; la descarga queda en la auditoría.
     */
    @GetMapping(EXPORT)
    public ResponseEntity<ApiSuccessResponse<AccountDataExport>> export(HttpServletRequest request) {
        UUID accountId = currentAccount();
        IdentityDataExport identity = identityExport.export(accountId, RequestContextResolver.from(request));
        boolean customer = AccountType.CUSTOMER.name().equals(identity.account().type());
        List<SessionData> openSessions = sessions.list(accountId, request.getSession().getId()).stream()
                .map(session -> new SessionData(session.createdAt(), session.lastActiveAt(), session.ip(),
                        session.userAgent(), session.current()))
                .toList();
        Instant now = clock.instant();
        AccountDataExport export = new AccountDataExport(now, identity.account(),
                customer ? customerExport.export(accountId) : null,
                customer ? null : staffExport.export(accountId),
                openSessions, identity.securityEvents(), identity.federatedIdentities());

        String fileName = EXPORT_FILE_NAME.formatted(LocalDate.ofInstant(now, ZoneOffset.UTC));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(ApiSuccessResponse.of(export));
    }

    /**
     * Salir de una empresa. Si era la empresa activa de esta sesión, se quita
     * (el frontend vuelve a pedir la sesión).
     */
    @PostMapping(LEAVE_COMPANY)
    @PreAuthorize(AccountAuthorities.HAS_ROLE_STAFF)
    public ApiSuccessResponse<Void> leaveCompany(@PathVariable UUID companyId, HttpServletRequest request,
            HttpServletResponse response) {
        members.leave(currentAccount(), companyId, RequestContextResolver.from(request));
        if (CurrentAccount.activeCompanyId().filter(companyId::equals).isPresent()) {
            sessionHandler.clearTenant(request, response);
        }
        return ApiSuccessResponse.of(null);
    }

    private static UUID currentAccount() {
        return CurrentAccount.id().orElseThrow(() -> new DomainException(CommonErrorCode.AUTHENTICATION_REQUIRED));
    }

    /**
     * @param customer perfil de cliente (solo cuentas de cliente)
     * @param staff    perfil y empresas (solo cuentas de backoffice)
     */
    public record AccountDataExport(
            Instant exportedAt,
            AccountData account,
            CustomerDataExport customer,
            StaffDataExport staff,
            List<SessionData> sessions,
            List<SecurityEventData> securityEvents,
            List<LinkedIdentityData> federatedIdentities
    ) {
    }

    /**
     * Sesión abierta, sin su identificador (no hace falta y no se expone).
     */
    public record SessionData(Instant createdAt, Instant lastActiveAt, String ip, String userAgent,
            boolean current) {
    }
}
