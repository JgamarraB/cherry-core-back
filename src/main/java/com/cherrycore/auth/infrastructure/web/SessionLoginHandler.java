package com.cherrycore.auth.infrastructure.web;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.cherrycore.identity.domain.AccountType;
import com.cherrycore.identity.infrastructure.security.AccountSessions;
import com.cherrycore.identity.infrastructure.web.RequestContextResolver;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Abre, modifica y cierra la sesión de login de CherryCore.
 *
 * <p>Es la sesión que usa el Authorization Server: si existe, una app que
 * redirige a {@code /oauth2/authorize} obtiene el código sin volver a pedir
 * la contraseña (single sign-on).</p>
 *
 * <p>Permisos de la sesión: rol por tipo de cuenta (CUSTOMER/STAFF),
 * SUPER_ADMIN si lo es, factores usados (contraseña, TOTP) con su hora, y la
 * empresa elegida (TENANT_{id}).</p>
 */
@Component
public class SessionLoginHandler {

    /* El principal no tiene contraseña: la sesión ya está autenticada. */
    private static final String NO_CREDENTIALS = "";

    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy sessionFixationProtection = new ChangeSessionIdAuthenticationStrategy();
    private final RequestCache requestCache = new HttpSessionRequestCache();
    private final Clock clock;

    public SessionLoginHandler(Clock clock) {
        this.clock = clock;
    }

    /**
     * Nuevo id de sesión: se llama en cuanto la contraseña es correcta, aunque
     * falte el segundo factor (evita ataques de fijación de sesión).
     */
    public void rotateSessionId(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
    }

    public void establish(UUID accountId, AccountType type, boolean superAdmin, boolean usedTotp,
            HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(AccountAuthorities.role(type.name()));
        if (superAdmin) {
            authorities.add(AccountAuthorities.role(AccountAuthorities.SUPER_ADMIN));
        }
        authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .issuedAt(clock.instant()).build());
        if (usedTotp) {
            authorities.add(FactorGrantedAuthority.withFactor(AccountAuthorities.TOTP_FACTOR)
                    .issuedAt(clock.instant()).build());
        }
        HttpSession session = request.getSession(true);
        save(accountId, authorities, request, response);
        sessionFixationProtection.onAuthentication(contextHolder.getContext().getAuthentication(), request, response);
        // Para la lista "dónde has iniciado sesión".
        session.setAttribute(AccountSessions.IP_ATTRIBUTE, request.getRemoteAddr());
        session.setAttribute(AccountSessions.USER_AGENT_ATTRIBUTE,
                RequestContextResolver.from(request).userAgent());
    }

    /**
     * Cambia la empresa activa (sustituye el permiso TENANT_ de la sesión).
     */
    public void selectTenant(UUID companyId, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = contextHolder.getContext().getAuthentication();
        List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities().stream()
                .filter(authority -> authority.getAuthority() == null
                        || !authority.getAuthority().startsWith(AccountAuthorities.TENANT_PREFIX))
                .map(GrantedAuthority.class::cast)
                .toList());
        authorities.add(AccountAuthorities.tenant(companyId));
        save(UUID.fromString(current.getName()), authorities, request, response);
    }

    /**
     * @return la URL a la que volver (la petición de autorización que se
     *         interrumpió para hacer login), o {@code null}
     */
    public String consumeRedirectUrl(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest saved = requestCache.getRequest(request, response);
        if (saved == null) {
            return null;
        }
        requestCache.removeRequest(request, response);
        return saved.getRedirectUrl();
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        contextHolder.clearContext();
    }

    private void save(UUID accountId, List<GrantedAuthority> authorities, HttpServletRequest request,
            HttpServletResponse response) {
        User principal = new User(accountId.toString(), NO_CREDENTIALS, authorities);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                authorities);
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
