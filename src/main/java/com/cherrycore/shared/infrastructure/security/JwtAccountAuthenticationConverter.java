package com.cherrycore.shared.infrastructure.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Convierte un JWT de CherryCore en la autenticación de la petición:
 * el nombre es el "sub" (id de la cuenta) y el claim account_type da el rol.
 */
public class JwtAccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final JwtGrantedAuthoritiesConverter SCOPES = new JwtGrantedAuthoritiesConverter();

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    public JwtAccountAuthenticationConverter() {
        delegate.setJwtGrantedAuthoritiesConverter(JwtAccountAuthenticationConverter::authorities);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    /*
     * Rol por tipo de cuenta (ROLE_CUSTOMER / ROLE_STAFF) + scopes (SCOPE_catalog.sync...).
     */
    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(SCOPES.convert(jwt));
        String accountType = jwt.getClaimAsString(AccountAuthorities.ACCOUNT_TYPE_CLAIM);
        if (accountType != null) {
            authorities.addAll(AccountAuthorities.forAccountType(accountType));
        }
        return authorities;
    }
}
