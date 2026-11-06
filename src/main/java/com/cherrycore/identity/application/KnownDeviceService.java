package com.cherrycore.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.identity.application.port.DeviceFingerprintHasher;
import com.cherrycore.identity.application.port.IdentityNotifier;
import com.cherrycore.identity.application.port.KnownDeviceRepository;
import com.cherrycore.identity.domain.Account;
import com.cherrycore.identity.domain.AccountDeletionRequested;
import com.cherrycore.identity.domain.AuthEventType;
import com.cherrycore.shared.domain.id.UuidV7;

/**
 * Dispositivos conocidos de cada cuenta. Al completar un login (con contraseña
 * y MFA, o social) desde un navegador que la cuenta no había usado, se avisa
 * por email y queda en la auditoría como {@code NEW_DEVICE_LOGIN}.
 *
 * <p>El primer dispositivo de una cuenta no avisa: es su alta (o el primer
 * login tras activar esta función).</p>
 */
@Service
public class KnownDeviceService {

    private final KnownDeviceRepository devices;
    private final DeviceFingerprintHasher hasher;
    private final AccountQueryService accounts;
    private final IdentityNotifier notifier;
    private final AuthEventLogger eventLogger;
    private final NewDeviceAlertSettings settings;
    private final Clock clock;

    public KnownDeviceService(KnownDeviceRepository devices, DeviceFingerprintHasher hasher,
            AccountQueryService accounts, IdentityNotifier notifier, AuthEventLogger eventLogger,
            NewDeviceAlertSettings settings, Clock clock) {
        this.devices = devices;
        this.hasher = hasher;
        this.accounts = accounts;
        this.notifier = notifier;
        this.eventLogger = eventLogger;
        this.settings = settings;
        this.clock = clock;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    /**
     * @param deviceId id aleatorio de la cookie del navegador
     * @return true si era un dispositivo nuevo y se ha avisado
     */
    @Transactional
    public boolean loginCompleted(UUID accountId, String deviceId, RequestContext context) {
        if (!settings.enabled() || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        boolean firstDevice = !devices.hasAny(accountId);
        boolean added = devices.addOrTouch(UuidV7.generate(clock), accountId, hasher.hash(accountId, deviceId), now);
        if (!added || firstDevice) {
            return false;
        }
        Account account = accounts.getActive(accountId);
        eventLogger.log(AuthEventType.NEW_DEVICE_LOGIN, accountId, context);
        notifier.newDeviceLogin(account, now, context);
        return true;
    }

    @EventListener
    void on(AccountDeletionRequested event) {
        devices.deleteByAccount(event.accountId());
    }
}
