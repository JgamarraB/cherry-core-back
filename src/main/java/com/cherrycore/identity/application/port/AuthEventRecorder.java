package com.cherrycore.identity.application.port;

import java.util.List;
import java.util.UUID;

import com.cherrycore.identity.domain.AuthEvent;

public interface AuthEventRecorder {

    void record(AuthEvent event);

    /**
     * Últimos eventos de una cuenta, del más reciente al más antiguo.
     */
    List<AuthEvent> findRecentByAccount(UUID accountId, int limit);
}
