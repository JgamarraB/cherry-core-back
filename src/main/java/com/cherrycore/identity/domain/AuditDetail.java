package com.cherrycore.identity.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Claves del contexto que acompaña a un evento de auditoría ({@code auth_events.details}).
 *
 * <p>Solo se guardan claves de esta lista y valores de texto cortos: nunca
 * contraseñas, secretos, tokens ni códigos MFA. El frontend traduce las claves
 * conocidas.</p>
 */
public final class AuditDetail {

    /** Cuenta que hizo la acción (cuando no es la propia cuenta del evento: admin, dueño...). */
    public static final String ACTOR_ACCOUNT_ID = "actorAccountId";
    /** Cuenta afectada (cuando el evento se guarda en la cuenta de quien actúa). */
    public static final String TARGET_ACCOUNT_ID = "targetAccountId";
    public static final String COMPANY_ID = "companyId";
    public static final String COMPANY_NAME = "companyName";
    public static final String APPLICATION_CODE = "applicationCode";
    public static final String ROLE = "role";
    public static final String PREVIOUS_ROLE = "previousRole";
    public static final String EMAIL = "email";
    public static final String REASON = "reason";

    public static final int MAX_VALUE_LENGTH = 200;

    private static final Set<String> ALLOWED = Set.of(ACTOR_ACCOUNT_ID, TARGET_ACCOUNT_ID, COMPANY_ID, COMPANY_NAME,
            APPLICATION_CODE, ROLE, PREVIOUS_ROLE, EMAIL, REASON);

    private AuditDetail() {
    }

    /**
     * Quita claves desconocidas y valores vacíos, y recorta los valores largos.
     */
    static Map<String, String> sanitize(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return Map.of();
        }
        Map<String, String> clean = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (ALLOWED.contains(key) && value != null && !value.isBlank()) {
                clean.put(key, value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) : value);
            }
        });
        return Collections.unmodifiableMap(clean);
    }
}
