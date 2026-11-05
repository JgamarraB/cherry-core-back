package com.cherrycore.identity.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import com.cherrycore.identity.domain.IdentityErrorCode;
import com.cherrycore.shared.domain.error.DomainException;

/**
 * Sesiones de login abiertas de una cuenta ("dónde has iniciado sesión").
 *
 * <p>Nunca se expone el id real de la sesión (con él se podría suplantar):
 * se usa su hash como identificador.</p>
 */
@Component
public class AccountSessions {

    public static final String IP_ATTRIBUTE = "cherrycore.session.ip";
    public static final String USER_AGENT_ATTRIBUTE = "cherrycore.session.userAgent";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final FindByIndexNameSessionRepository<? extends Session> repository;

    public AccountSessions(FindByIndexNameSessionRepository<? extends Session> repository) {
        this.repository = repository;
    }

    public List<SessionView> list(UUID accountId, String currentSessionId) {
        return sessionsOf(accountId).values().stream()
                .map(session -> new SessionView(publicId(session.getId()), session.getCreationTime(),
                        session.getLastAccessedTime(), session.getAttribute(IP_ATTRIBUTE),
                        session.getAttribute(USER_AGENT_ATTRIBUTE), session.getId().equals(currentSessionId)))
                .sorted(Comparator.comparing(SessionView::lastActiveAt).reversed())
                .toList();
    }

    public void revoke(UUID accountId, String publicId, String currentSessionId) {
        String sessionId = sessionsOf(accountId).keySet().stream()
                .filter(id -> publicId(id).equals(publicId) && !id.equals(currentSessionId))
                .findFirst()
                .orElseThrow(() -> new DomainException(IdentityErrorCode.SESSION_NOT_FOUND));
        repository.deleteById(sessionId);
    }

    private Map<String, ? extends Session> sessionsOf(UUID accountId) {
        return repository.findByPrincipalName(accountId.toString());
    }

    private static String publicId(String sessionId) {
        try {
            byte[] hash = MessageDigest.getInstance(HASH_ALGORITHM).digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record SessionView(String id, Instant createdAt, Instant lastActiveAt, String ip, String userAgent,
            boolean current) {
    }
}
