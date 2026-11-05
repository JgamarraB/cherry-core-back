package com.cherrycore.admin.application.port;

import java.time.Instant;
import java.util.UUID;

import com.cherrycore.admin.application.AccountSummary;
import com.cherrycore.admin.application.AuditEventView;
import com.cherrycore.admin.application.DashboardView;
import com.cherrycore.shared.application.PageQuery;
import com.cherrycore.shared.application.PageResult;

/**
 * Consultas de solo lectura para el panel de superadmin (listados y cifras).
 * Van directas a SQL: no modifican nada y así son rápidas.
 */
public interface AdminReadModel {

    DashboardView dashboard(Instant since);

    /**
     * @param type   CUSTOMER / STAFF o {@code null}
     * @param status estado o {@code null}
     * @param search texto en el email o {@code null}
     */
    PageResult<AccountSummary> searchAccounts(String type, String status, String search, PageQuery page);

    PageResult<AuditEventView> searchAuditEvents(UUID accountId, String type, Instant from, Instant to,
            PageQuery page);
}
