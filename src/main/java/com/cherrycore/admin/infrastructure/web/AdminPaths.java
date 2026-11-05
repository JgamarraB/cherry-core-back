package com.cherrycore.admin.infrastructure.web;

/**
 * API del panel de superadmin. Solo SUPER_ADMIN (con MFA, porque es backoffice).
 */
public final class AdminPaths {

    public static final String BASE = "/api/admin";
    public static final String DASHBOARD = BASE + "/dashboard";
    public static final String COMPANIES = BASE + "/companies";
    public static final String ACCOUNTS = BASE + "/accounts";
    public static final String APPLICATIONS = BASE + "/applications";
    public static final String CLIENTS = BASE + "/clients";
    public static final String AUDIT_EVENTS = BASE + "/audit-events";

    public static final String PAGE = "page";
    public static final String SIZE = "size";
    public static final String DEFAULT_PAGE = "0";
    public static final String DEFAULT_SIZE = "20";

    private AdminPaths() {
    }
}
