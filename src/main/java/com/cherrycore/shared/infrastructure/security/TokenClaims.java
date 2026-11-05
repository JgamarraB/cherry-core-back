package com.cherrycore.shared.infrastructure.security;

/**
 * Nombres de los claims propios de los JWT de CherryCore.
 */
public final class TokenClaims {

    /** CUSTOMER o STAFF. */
    public static final String ACCOUNT_TYPE = AccountAuthorities.ACCOUNT_TYPE_CLAIM;
    /** Solo backoffice: empresa con la que entró. */
    public static final String TENANT_ID = "tenant_id";
    public static final String TENANT_NAME = "tenant_name";
    /** Solo backoffice: código del rol en la empresa (OWNER, ADMIN, ...). */
    public static final String TENANT_ROLE = "tenant_role";
    /** Solo backoffice: permisos en ESTA app para esa empresa. */
    public static final String PERMISSIONS = "permissions";
    /** Solo en tokens de máquina: código de la app del catálogo. */
    public static final String APPLICATION = "application";
    /** Solo si es superadmin de la plataforma. */
    public static final String PLATFORM_ROLE = "platform_role";

    private TokenClaims() {
    }
}
