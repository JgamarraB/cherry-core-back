package com.cherrycore.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.identity.application.port.AuthEventRecorder;
import com.cherrycore.identity.domain.AuthEvent;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Atajo para registrar eventos de seguridad con la hora y los datos de la petición.
 */
@Component
public class AuthEventLogger {

    private final AuthEventRecorder recorder;
    private final Clock clock;

    public AuthEventLogger(AuthEventRecorder recorder, Clock clock) {
        this.recorder = recorder;
        this.clock = clock;
    }

    public void log(AuthEventType type, UUID accountId, RequestContext context) {
        recorder.record(new AuthEvent(UuidV7.generate(clock), accountId, type,
                context.ip(), context.userAgent(), clock.instant()));
    }
}
