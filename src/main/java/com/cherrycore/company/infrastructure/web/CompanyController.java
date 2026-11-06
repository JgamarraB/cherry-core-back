package com.cherrycore.company.infrastructure.web;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cherrycore.company.application.AvailablePermissionsView;
import com.cherrycore.company.application.CompanyApplicationView;
import com.cherrycore.company.application.CompanyAccessPolicy;
import com.cherrycore.company.application.CompanyApplicationsService;
import com.cherrycore.company.application.CompanyMembersService;
import com.cherrycore.company.application.CompanyProfileService;
import com.cherrycore.company.application.CompanyRolesService;
import com.cherrycore.company.application.CompanyView;
import com.cherrycore.company.application.InvitationView;
import com.cherrycore.company.application.MemberView;
import com.cherrycore.company.application.MyCompanyAccess;
import com.cherrycore.company.application.RoleView;
import com.cherrycore.company.application.StaffInvitationService;
import com.cherrycore.company.infrastructure.web.CompanyDtos.ChangeRoleRequest;
import com.cherrycore.company.infrastructure.web.CompanyDtos.CompanyData;
import com.cherrycore.company.infrastructure.web.CompanyDtos.CreateRoleRequest;
import com.cherrycore.company.infrastructure.web.CompanyDtos.InviteRequest;
import com.cherrycore.company.infrastructure.web.CompanyDtos.UpdateRoleRequest;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;
import com.cherrycore.shared.infrastructure.web.ApiSuccessResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Gestión de la empresa activa: datos, logo, miembros, roles, invitaciones y
 * apps. Cada operación comprueba el permiso del rol en la capa de aplicación.
 */
@RestController
@RequestMapping(CompanyPaths.BASE)
@PreAuthorize(AccountAuthorities.HAS_ROLE_STAFF)
public class CompanyController {

    private static final String LOGO_PART = "file";

    private final CompanyProfileService profile;
    private final CompanyMembersService members;
    private final CompanyRolesService roles;
    private final StaffInvitationService invitations;
    private final CompanyApplicationsService applications;
    private final CompanyAccessPolicy access;

    public CompanyController(CompanyProfileService profile, CompanyMembersService members, CompanyRolesService roles,
            StaffInvitationService invitations, CompanyApplicationsService applications, CompanyAccessPolicy access) {
        this.profile = profile;
        this.members = members;
        this.roles = roles;
        this.invitations = invitations;
        this.applications = applications;
        this.access = access;
    }

    /** Mi rol y mis permisos en la empresa activa. */
    @GetMapping(CompanyPaths.ME)
    public ApiSuccessResponse<MyCompanyAccess> me() {
        return ApiSuccessResponse.of(access.myAccess(StaffActorResolver.current()));
    }

    @GetMapping
    public ApiSuccessResponse<CompanyView> get() {
        return ApiSuccessResponse.of(profile.get(StaffActorResolver.current()));
    }

    @PutMapping
    public ApiSuccessResponse<CompanyView> update(@Valid @RequestBody CompanyData body) {
        return ApiSuccessResponse.of(profile.update(StaffActorResolver.current(), body.toCommand()));
    }

    @PutMapping(path = CompanyPaths.LOGO, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiSuccessResponse<CompanyView> uploadLogo(@RequestParam(LOGO_PART) MultipartFile file)
            throws IOException {
        return ApiSuccessResponse.of(profile.uploadLogo(StaffActorResolver.current(), file.getBytes()));
    }

    @DeleteMapping(CompanyPaths.LOGO)
    public ApiSuccessResponse<CompanyView> removeLogo() {
        return ApiSuccessResponse.of(profile.removeLogo(StaffActorResolver.current()));
    }

    @GetMapping(CompanyPaths.MEMBERS)
    public ApiSuccessResponse<List<MemberView>> members() {
        return ApiSuccessResponse.of(members.list(StaffActorResolver.current()));
    }

    @PutMapping(CompanyPaths.MEMBER)
    public ApiSuccessResponse<MemberView> changeRole(@PathVariable UUID membershipId,
            @Valid @RequestBody ChangeRoleRequest body, HttpServletRequest request) {
        return ApiSuccessResponse.of(members.changeRole(StaffActorResolver.current(), membershipId, body.roleId(),
                RequestContextResolver.from(request)));
    }

    @PostMapping(CompanyPaths.MEMBER_SUSPEND)
    public ApiSuccessResponse<MemberView> suspend(@PathVariable UUID membershipId, HttpServletRequest request) {
        return ApiSuccessResponse.of(members.suspend(StaffActorResolver.current(), membershipId,
                RequestContextResolver.from(request)));
    }

    @PostMapping(CompanyPaths.MEMBER_REACTIVATE)
    public ApiSuccessResponse<MemberView> reactivate(@PathVariable UUID membershipId, HttpServletRequest request) {
        return ApiSuccessResponse.of(members.reactivate(StaffActorResolver.current(), membershipId,
                RequestContextResolver.from(request)));
    }

    @DeleteMapping(CompanyPaths.MEMBER)
    public ApiSuccessResponse<Void> remove(@PathVariable UUID membershipId, HttpServletRequest request) {
        members.remove(StaffActorResolver.current(), membershipId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    /**
     * Solo un OWNER: el miembro pasa a OWNER y quien la cede pasa a ADMIN.
     */
    @PostMapping(CompanyPaths.MEMBER_TRANSFER_OWNERSHIP)
    public ApiSuccessResponse<MemberView> transferOwnership(@PathVariable UUID membershipId,
            HttpServletRequest request) {
        return ApiSuccessResponse.of(members.transferOwnership(StaffActorResolver.current(), membershipId,
                RequestContextResolver.from(request)));
    }

    @GetMapping(CompanyPaths.ROLES)
    public ApiSuccessResponse<List<RoleView>> roles() {
        return ApiSuccessResponse.of(roles.list(StaffActorResolver.current()));
    }

    @GetMapping(CompanyPaths.AVAILABLE_PERMISSIONS)
    public ApiSuccessResponse<List<AvailablePermissionsView>> availablePermissions() {
        return ApiSuccessResponse.of(roles.availablePermissions(StaffActorResolver.current()));
    }

    @PostMapping(CompanyPaths.ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<RoleView> createRole(@Valid @RequestBody CreateRoleRequest body) {
        return ApiSuccessResponse.of(roles.create(StaffActorResolver.current(), body.code(), body.name(),
                body.permissionIds()));
    }

    @PutMapping(CompanyPaths.ROLE)
    public ApiSuccessResponse<RoleView> updateRole(@PathVariable UUID roleId,
            @Valid @RequestBody UpdateRoleRequest body) {
        return ApiSuccessResponse.of(roles.update(StaffActorResolver.current(), roleId, body.name(),
                body.permissionIds()));
    }

    @DeleteMapping(CompanyPaths.ROLE)
    public ApiSuccessResponse<Void> deleteRole(@PathVariable UUID roleId) {
        roles.delete(StaffActorResolver.current(), roleId);
        return ApiSuccessResponse.of(null);
    }

    @GetMapping(CompanyPaths.INVITATIONS)
    public ApiSuccessResponse<List<InvitationView>> invitations() {
        return ApiSuccessResponse.of(invitations.listPending(StaffActorResolver.current()));
    }

    @PostMapping(CompanyPaths.INVITATIONS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<InvitationView> invite(@Valid @RequestBody InviteRequest body,
            HttpServletRequest request) {
        return ApiSuccessResponse.of(invitations.invite(StaffActorResolver.current(), body.email(), body.roleId(),
                RequestContextResolver.from(request)));
    }

    @DeleteMapping(CompanyPaths.INVITATION)
    public ApiSuccessResponse<Void> revokeInvitation(@PathVariable UUID invitationId, HttpServletRequest request) {
        invitations.revoke(StaffActorResolver.current(), invitationId, RequestContextResolver.from(request));
        return ApiSuccessResponse.of(null);
    }

    @GetMapping(CompanyPaths.APPLICATIONS)
    public ApiSuccessResponse<List<CompanyApplicationView>> applications() {
        return ApiSuccessResponse.of(applications.enabledFor(StaffActorResolver.current()));
    }
}
