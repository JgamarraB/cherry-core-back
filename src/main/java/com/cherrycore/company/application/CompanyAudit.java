package com.cherrycore.company.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.company.domain.Company;
import com.cherrycore.identity.application.AuthEventLogger;
import com.cherrycore.identity.application.RequestContext;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;

/**
 * Deja en la auditoría de seguridad las acciones sobre empresas, con su
 * contexto (empresa, quién actúa, a quién afecta, rol...). Nunca secretos.
 */
@Component
public class CompanyAudit {

    private final AuthEventLogger eventLogger;

    public CompanyAudit(AuthEventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    /**
     * @param accountId cuenta en la que se guarda el evento (la afectada o, si no hay, quien actúa)
     */
    public Entry event(AuthEventType type, UUID accountId, Company company) {
        return new Entry(type, accountId, company);
    }

    /**
     * Evento en construcción: se añaden detalles y se guarda con {@link #log}.
     */
    public final class Entry {

        private final AuthEventType type;
        private final UUID accountId;
        private final Map<String, String> details = new LinkedHashMap<>();

        private Entry(AuthEventType type, UUID accountId, Company company) {
            this.type = type;
            this.accountId = accountId;
            details.put(AuditDetail.COMPANY_ID, company.id().toString());
            details.put(AuditDetail.COMPANY_NAME, company.profile().tradeName().value());
        }

        public Entry actor(UUID actorAccountId) {
            if (actorAccountId != null && !actorAccountId.equals(accountId)) {
                details.put(AuditDetail.ACTOR_ACCOUNT_ID, actorAccountId.toString());
            }
            return this;
        }

        public Entry target(UUID targetAccountId) {
            if (targetAccountId != null && !targetAccountId.equals(accountId)) {
                details.put(AuditDetail.TARGET_ACCOUNT_ID, targetAccountId.toString());
            }
            return this;
        }

        public Entry detail(String key, String value) {
            details.put(key, value);
            return this;
        }

        public void log(RequestContext context) {
            eventLogger.log(type, accountId, context, details);
        }
    }
}
