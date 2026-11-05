package com.cherrycore.shared.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Traducción entre tipo de cuenta y permisos de Spring Security.
 * CUSTOMER → ROLE_CUSTOMER (se comprueba con {@code hasRole('CUSTOMER')}).
 */
public final class AccountAuthorities {

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String STAFF = "STAFF";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String HAS_ROLE_CUSTOMER = "hasRole('" + CUSTOMER + "')";
    public static final String HAS_ROLE_STAFF = "hasRole('" + STAFF + "')";
    public static final String HAS_ROLE_SUPER_ADMIN = "hasRole('" + SUPER_ADMIN + "')";

    /** Empresa con la que trabaja una persona de backoffice: TENANT_{uuid}. */
    public static final String TENANT_PREFIX = "TENANT_";

    /** Segundo factor (app autenticadora). */
    public static final String TOTP_FACTOR = "TOTP";

    /** Claim del JWT con el tipo de cuenta. */
    public static final String ACCOUNT_TYPE_CLAIM = "account_type";

    private AccountAuthorities() {
    }

    public static List<GrantedAuthority> forAccountType(String accountType) {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + accountType));
    }

    public static GrantedAuthority role(String role) {
        return new SimpleGrantedAuthority(ROLE_PREFIX + role);
    }

    public static GrantedAuthority tenant(UUID companyId) {
        return new SimpleGrantedAuthority(TENANT_PREFIX + companyId);
    }

    public static Optional<UUID> tenantOf(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name != null && name.startsWith(TENANT_PREFIX))
                .map(name -> UUID.fromString(name.substring(TENANT_PREFIX.length())))
                .findFirst();
    }
}
