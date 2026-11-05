package com.cherrycore.identity.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.IdentityDataExport.AccountData;
import com.cherrycore.identity.application.IdentityDataExport.LinkedIdentityData;
import com.cherrycore.identity.application.IdentityDataExport.SecurityEventData;
import com.cherrycore.identity.application.port.AuthEventRecorder;
import com.cherrycore.identity.application.port.FederatedIdentityRepository;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AuditDetail;
import com.cherrycore.identity.domain.AuthEventType;

/**
 * Parte de identidad de "descargar mis datos": cuenta, últimos eventos de
 * seguridad e identidades vinculadas (Google, Apple). Deja la descarga en la auditoría.
 */
@Service
public class IdentityDataExportService {

    /** Eventos de seguridad que se incluyen (los más recientes). */
    public static final int MAX_SECURITY_EVENTS = 100;

    private final AccountQueryService accounts;
    private final MfaService mfa;
    private final AuthEventRecorder events;
    private final FederatedIdentityRepository federatedIdentities;
    private final AuthEventLogger eventLogger;

    public IdentityDataExportService(AccountQueryService accounts, MfaService mfa, AuthEventRecorder events,
            FederatedIdentityRepository federatedIdentities, AuthEventLogger eventLogger) {
        this.accounts = accounts;
        this.mfa = mfa;
        this.events = events;
        this.federatedIdentities = federatedIdentities;
        this.eventLogger = eventLogger;
    }

    @Transactional
    public IdentityDataExport export(UUID accountId, RequestContext context) {
        Account account = accounts.getActive(accountId);
        AccountData data = new AccountData(account.id(), account.type().name(), account.email().value(),
                account.emailVerifiedAt(), account.status().name(), account.locale().toLanguageTag(),
                account.hasPassword(), mfa.status(accountId).enabled(), account.isSuperAdmin(),
                account.lastLoginAt());
        /*
         * Si el evento lo provocó otra persona (un superadmin, el dueño de una
         * empresa...), la IP y el navegador son los suyos: no se entregan.
         */
        var securityEvents = events.findRecentByAccount(accountId, MAX_SECURITY_EVENTS).stream()
                .map(event -> {
                    boolean byAnotherPerson = event.details().containsKey(AuditDetail.ACTOR_ACCOUNT_ID);
                    return new SecurityEventData(event.type().name(), event.occurredAt(),
                            byAnotherPerson ? null : event.ip(), byAnotherPerson ? null : event.userAgent());
                })
                .toList();
        var linked = federatedIdentities.findByAccount(accountId).stream()
                .map(identity -> new LinkedIdentityData(identity.provider().name(), identity.emailAtLink()))
                .toList();
        eventLogger.log(AuthEventType.ACCOUNT_DATA_EXPORTED, accountId, context);
        return new IdentityDataExport(data, securityEvents, linked);
    }
}
