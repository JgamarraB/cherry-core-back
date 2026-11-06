package com.cherrycore.admin.infrastructure.web;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cherrycore.admin.application.AdminService;
import com.cherrycore.admin.application.AuditEventView;
import com.cherrycore.admin.application.DashboardView;
import com.cherrycore.auth.infrastructure.clients.ClientDefinition;
import com.cherrycore.auth.infrastructure.clients.OAuthClientAdminService;
import com.cherrycore.auth.infrastructure.clients.OAuthClientAdminService.CreatedClient;
import com.cherrycore.auth.infrastructure.clients.OAuthClientAdminService.OAuthClientView;
import com.cherrycore.catalog.application.CatalogService;
import com.cherrycore.catalog.domain.Application;
import com.cherrycore.catalog.domain.ApplicationPermission;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientAudience;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties.ClientType;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;
import com.cherrycore.shared.infrastructure.web.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Panel de superadmin: cifras, apps y sus permisos, clientes OAuth y auditoría.
 */
@RestController
@PreAuthorize(AccountAuthorities.HAS_ROLE_SUPER_ADMIN)
public class AdminCatalogController {

    private static final int MAX_CODE = 50;
    private static final int MAX_CLIENT_ID = 100;
    private static final int MAX_URIS = 20;
    private static final int MAX_URI_LENGTH = 500;
    private static final String LOGO_PART = "file";

    private final AdminService admin;
    private final CatalogService catalog;
    private final OAuthClientAdminService clients;

    public AdminCatalogController(AdminService admin, CatalogService catalog, OAuthClientAdminService clients) {
        this.admin = admin;
        this.catalog = catalog;
        this.clients = clients;
    }

    @GetMapping(AdminPaths.DASHBOARD)
    public ApiSuccessResponse<DashboardView> dashboard() {
        return ApiSuccessResponse.of(admin.dashboard());
    }

    @GetMapping(AdminPaths.AUDIT_EVENTS)
    public ApiSuccessResponse<PageResponse<AuditEventView>> auditEvents(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) AuthEventType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(name = AdminPaths.PAGE, defaultValue = AdminPaths.DEFAULT_PAGE) int page,
            @RequestParam(name = AdminPaths.SIZE, defaultValue = AdminPaths.DEFAULT_SIZE) int size) {
        return ApiSuccessResponse.of(PageResponse.from(admin.auditEvents(accountId,
                type == null ? null : type.name(), from, to, new PageQuery(page, size))));
    }

    // --- Apps del catálogo -------------------------------------------------

    @GetMapping(AdminPaths.APPLICATIONS)
    public ApiSuccessResponse<List<ApplicationView>> applications() {
        return ApiSuccessResponse.of(catalog.listApplications().stream().map(this::view).toList());
    }

    @PostMapping(AdminPaths.APPLICATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<ApplicationView> createApplication(@Valid @RequestBody ApplicationRequest body) {
        return ApiSuccessResponse.of(view(catalog.create(body.code(), body.name())));
    }

    @PutMapping(AdminPaths.APPLICATIONS + "/{applicationId}")
    public ApiSuccessResponse<ApplicationView> updateApplication(@PathVariable UUID applicationId,
            @Valid @RequestBody UpdateApplicationRequest body) {
        return ApiSuccessResponse.of(view(catalog.update(applicationId, body.name(), body.active())));
    }

    /**
     * Logo de la app (se muestra en el login). Multipart, campo {@code file}, máx. 2 MB;
     * PNG, JPEG o WEBP comprobado por el contenido.
     */
    @PutMapping(path = AdminPaths.APPLICATION_LOGO, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiSuccessResponse<ApplicationView> uploadLogo(@PathVariable UUID applicationId,
            @RequestParam(LOGO_PART) MultipartFile file) throws IOException {
        return ApiSuccessResponse.of(view(catalog.uploadLogo(applicationId, file.getBytes())));
    }

    @DeleteMapping(AdminPaths.APPLICATION_LOGO)
    public ApiSuccessResponse<ApplicationView> removeLogo(@PathVariable UUID applicationId) {
        return ApiSuccessResponse.of(view(catalog.removeLogo(applicationId)));
    }

    @GetMapping(AdminPaths.APPLICATIONS + "/{applicationId}/permissions")
    public ApiSuccessResponse<List<PermissionView>> permissions(@PathVariable UUID applicationId) {
        catalog.get(applicationId);
        return ApiSuccessResponse.of(catalog.permissionsOf(applicationId).stream().map(PermissionView::of).toList());
    }

    // --- Clientes OAuth ----------------------------------------------------

    @GetMapping(AdminPaths.CLIENTS)
    public ApiSuccessResponse<List<OAuthClientView>> clients() {
        return ApiSuccessResponse.of(clients.list());
    }

    @GetMapping(AdminPaths.CLIENTS + "/{clientId}")
    public ApiSuccessResponse<OAuthClientView> client(@PathVariable String clientId) {
        return ApiSuccessResponse.of(clients.get(clientId));
    }

    /**
     * El secreto aparece SOLO en esta respuesta.
     */
    @PostMapping(AdminPaths.CLIENTS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<CreatedClient> createClient(@Valid @RequestBody ClientRequest body) {
        return ApiSuccessResponse.of(clients.create(body.toDefinition(body.clientId())));
    }

    @PutMapping(AdminPaths.CLIENTS + "/{clientId}")
    public ApiSuccessResponse<OAuthClientView> updateClient(@PathVariable String clientId,
            @Valid @RequestBody ClientRequest body) {
        return ApiSuccessResponse.of(clients.update(clientId, body.toDefinition(clientId)));
    }

    @PostMapping(AdminPaths.CLIENTS + "/{clientId}/secret")
    public ApiSuccessResponse<CreatedClient> rotateSecret(@PathVariable String clientId) {
        return ApiSuccessResponse.of(clients.rotateSecret(clientId));
    }

    @DeleteMapping(AdminPaths.CLIENTS + "/{clientId}")
    public ApiSuccessResponse<Void> deleteClient(@PathVariable String clientId) {
        clients.delete(clientId);
        return ApiSuccessResponse.of(null);
    }

    private ApplicationView view(Application application) {
        return new ApplicationView(application.id(), application.code().value(), application.name(),
                application.isActive(), catalog.logoUrlOf(application));
    }

    /**
     * @param logoUrl URL pública del logo, o {@code null}
     */
    public record ApplicationView(UUID id, String code, String name, boolean active, String logoUrl) {
    }

    public record PermissionView(UUID id, String code, String description) {

        static PermissionView of(ApplicationPermission permission) {
            return new PermissionView(permission.id(), permission.code().value(), permission.description());
        }
    }

    public record ApplicationRequest(
            @NotBlank @Size(max = MAX_CODE) String code,
            @NotBlank @Size(max = Application.MAX_NAME_LENGTH) String name
    ) {
    }

    public record UpdateApplicationRequest(
            @NotBlank @Size(max = Application.MAX_NAME_LENGTH) String name,
            @NotNull Boolean active
    ) {
    }

    /**
     * En la edición se ignoran {@code clientId} y {@code type} (no cambian).
     */
    public record ClientRequest(
            @Size(max = MAX_CLIENT_ID) String clientId,
            @NotBlank @Size(max = MAX_CLIENT_ID) String name,
            ClientType type,
            @NotNull ClientAudience audience,
            @Size(max = MAX_CODE) String applicationCode,
            @NotNull @Size(min = 1, max = MAX_URIS) List<@NotBlank @Size(max = MAX_URI_LENGTH) String> redirectUris,
            @Size(max = MAX_URIS) List<@NotBlank @Size(max = MAX_URI_LENGTH) String> postLogoutRedirectUris,
            @NotNull @Size(min = 1, max = MAX_URIS) Set<String> scopes,
            boolean machineAccess
    ) {

        ClientDefinition toDefinition(String id) {
            return new ClientDefinition(id, name, type == null ? ClientType.CONFIDENTIAL : type, audience,
                    applicationCode, redirectUris, postLogoutRedirectUris == null ? List.of() : postLogoutRedirectUris,
                    scopes, machineAccess);
        }
    }
}
