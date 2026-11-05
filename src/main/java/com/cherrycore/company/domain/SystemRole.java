package com.cherrycore.company.domain;

/**
 * Roles que toda empresa tiene siempre. No se pueden borrar ni renombrar.
 * OWNER tiene siempre todos los permisos.
 */
public enum SystemRole {

    OWNER("Owner"),
    ADMIN("Administrator"),
    STAFF("Staff");

    private final String defaultName;

    SystemRole(String defaultName) {
        this.defaultName = defaultName;
    }

    public String defaultName() {
        return defaultName;
    }

    public RoleCode code() {
        return new RoleCode(name());
    }
}
