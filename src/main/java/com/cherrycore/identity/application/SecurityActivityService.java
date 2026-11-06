package com.cherrycore.identity.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.AuthEventRecorder;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEvent;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.identity.domain.DeviceDescription;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;

/**
 * "Actividad de seguridad" de Mi cuenta: mis eventos recientes. Misma regla
 * de privacidad que la exportación: si lo hizo otra persona, su IP y su
 * navegador no se enseñan, solo que lo hizo un administrador.
 */
@Service
public class SecurityActivityService {

    /**
     * Pasos intermedios que no aportan nada a quien revisa su cuenta: la
     * contraseña correcta antes del MFA (el login completo ya aparece).
     */
    static final Set<AuthEventType> HIDDEN = Set.of(AuthEventType.LOGIN_SUCCEEDED);

    private final AuthEventRecorder events;

    public SecurityActivityService(AuthEventRecorder events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResult<SecurityActivityItem> activity(UUID accountId, PageQuery page, RequestContext current) {
        return events.findByAccount(accountId, HIDDEN, page).map(event -> toItem(accountId, event, current));
    }

    static SecurityActivityItem toItem(UUID accountId, AuthEvent event, RequestContext current) {
        String actor = event.details().get(AuditDetail.ACTOR_ACCOUNT_ID);
        boolean byAnotherPerson = actor != null && !actor.equals(accountId.toString());
        if (byAnotherPerson) {
            return new SecurityActivityItem(event.type().name(), event.occurredAt(), null, null, null, true, false);
        }
        DeviceDescription device = DeviceDescription.fromUserAgent(event.userAgent());
        boolean currentDevice = event.ip() != null && event.userAgent() != null
                && Objects.equals(event.ip(), current.ip()) && Objects.equals(event.userAgent(), current.userAgent());
        return new SecurityActivityItem(event.type().name(), event.occurredAt(), event.ip(), device.browser(),
                device.os(), false, currentDevice);
    }
}
