package com.cherrycore.shared.infrastructure.security;

import java.util.List;

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
    public static final String HAS_ROLE_CUSTOMER = "hasRole('" + CUSTOMER + "')";

    /** Claim del JWT con el tipo de cuenta. */
    public static final String ACCOUNT_TYPE_CLAIM = "account_type";

    private AccountAuthorities() {
    }

    public static List<GrantedAuthority> forAccountType(String accountType) {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + accountType));
    }
}
