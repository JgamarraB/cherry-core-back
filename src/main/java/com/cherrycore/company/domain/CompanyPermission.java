package com.cherrycore.company.domain;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Permisos de la propia app CherryCore (gestión de la empresa). Se registran
 * en el catálogo al arrancar, igual que hace cualquier otra app.
 */
public enum CompanyPermission {

    PROFILE_MANAGE("company.profile.manage", "Edit company data and logo", true),
    MEMBERS_VIEW("company.members.view", "View members and invitations", true),
    MEMBERS_MANAGE("company.members.manage", "Invite, suspend and remove members", true),
    ROLES_MANAGE("company.roles.manage", "Create and edit roles and their permissions", true),
    APPLICATIONS_VIEW("company.applications.view", "View the applications enabled for the company", true);

    public static final String APPLICATION_CODE = "cherrycore";
    public static final String APPLICATION_NAME = "CherryCore";

    private final String code;
    private final String description;
    private final boolean grantedToAdminByDefault;

    CompanyPermission(String code, String description, boolean grantedToAdminByDefault) {
        this.code = code;
        this.description = description;
        this.grantedToAdminByDefault = grantedToAdminByDefault;
    }

    public static Set<CompanyPermission> adminDefaults() {
        EnumSet<CompanyPermission> defaults = EnumSet.noneOf(CompanyPermission.class);
        Arrays.stream(values()).filter(permission -> permission.grantedToAdminByDefault).forEach(defaults::add);
        return defaults;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }
}
