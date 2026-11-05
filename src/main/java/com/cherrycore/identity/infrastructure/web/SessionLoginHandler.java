package com.cherrycore.identity.infrastructure.web;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

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

import com.cherrycore.identity.application.AuthenticatedAccount;
import com.cherrycore.shared.infrastructure.security.AccountAuthorities;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Abre y cierra la sesión de login de CherryCore.
 *
 * <p>Esta sesión es la que usa el Authorization Server: cuando una app redirige
 * a {@code /oauth2/authorize}, si hay sesión entrega el código sin volver a
 * pedir la contraseña (single sign-on).</p>
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
     * @return la URL a la que volver (la petición de autorización que se
     *         interrumpió para hacer login), o {@code null}
     */
    public String login(AuthenticatedAccount account, HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>(AccountAuthorities.forAccountType(account.type().name()));
        /*
         * Factor de autenticación usado y cuándo: el ID token lo necesita
         * ("auth_time") y será la base para exigir MFA (segundo factor).
         */
        authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .issuedAt(clock.instant())
                .build());
        User principal = new User(account.id().toString(), NO_CREDENTIALS, authorities);
        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);

        // Nueva id de sesión tras el login: evita ataques de fijación de sesión.
        request.getSession(true);
        sessionFixationProtection.onAuthentication(authentication, request, response);

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

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
}
