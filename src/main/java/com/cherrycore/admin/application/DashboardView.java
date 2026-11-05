package com.cherrycore.admin.application;

/**
 * Cifras del panel. {@code *Last24h}: últimas 24 horas.
 */
public record DashboardView(
        long customers,
        long staff,
        long companiesActive,
        long companiesPending,
        long companiesSuspended,
        long loginsLast24h,
        long failedLoginsLast24h,
        long lockedAccountsLast24h,
        long registrationsLast24h
) {
}
