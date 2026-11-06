package com.cherrycore.identity.application.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cherrycore.identity.domain.AuthEvent;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;

public interface AuthEventRecorder {

    void record(AuthEvent event);

    /**
     * Últimos eventos de una cuenta, del más reciente al más antiguo.
     */
    List<AuthEvent> findRecentByAccount(UUID accountId, int limit);

    /**
     * Eventos de una cuenta por páginas, del más reciente al más antiguo, sin
     * los tipos de {@code excluded}.
     */
    PageResult<AuthEvent> findByAccount(UUID accountId, Set<AuthEventType> excluded, PageQuery page);
}
