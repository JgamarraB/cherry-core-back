package com.cherrycore.shared.infrastructure.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Convierte un JWT de CherryCore en la autenticación de la petición:
 * el nombre es el "sub" (id de la cuenta) y el claim account_type da el rol.
 */
public class JwtAccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();

    public JwtAccountAuthenticationConverter() {
        delegate.setJwtGrantedAuthoritiesConverter(JwtAccountAuthenticationConverter::authorities);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return delegate.convert(jwt);
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        String accountType = jwt.getClaimAsString(AccountAuthorities.ACCOUNT_TYPE_CLAIM);
        return accountType == null ? List.of() : AccountAuthorities.forAccountType(accountType);
    }
}
