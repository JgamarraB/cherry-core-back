package com.cherrycore.company.infrastructure.web;

/**
 * API de gestión de la empresa activa (la que eligió la persona al entrar).
 */
public final class CompanyPaths {

    public static final String BASE = "/api/company";
    public static final String ALL = BASE + "/**";
    public static final String ME = "/me";
    public static final String LOGO = "/logo";
    public static final String MEMBERS = "/members";
    public static final String MEMBER = "/members/{membershipId}";
    public static final String MEMBER_SUSPEND = "/members/{membershipId}/suspend";
    public static final String MEMBER_REACTIVATE = "/members/{membershipId}/reactivate";
    public static final String ROLES = "/roles";
    public static final String ROLE = "/roles/{roleId}";
    public static final String AVAILABLE_PERMISSIONS = "/permissions";
    public static final String INVITATIONS = "/invitations";
    public static final String INVITATION = "/invitations/{invitationId}";
    public static final String APPLICATIONS = "/applications";

    /** Una persona de backoffice registra otra empresa con su cuenta. */
    public static final String ACCOUNT_COMPANIES = "/api/account/companies";

    private CompanyPaths() {
    }
}
