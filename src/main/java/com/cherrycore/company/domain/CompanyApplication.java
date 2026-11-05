package com.cherrycore.company.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * App activada (o no) para una empresa. Hoy lo gestiona el superadmin a mano;
 * más adelante lo hará la suscripción de Stripe.
 */
public class CompanyApplication {

    private final UUID id;
    private final UUID companyId;
    private final UUID applicationId;
    private CompanyApplicationStatus status;

    public CompanyApplication(UUID id, UUID companyId, UUID applicationId, CompanyApplicationStatus status) {
        this.id = Objects.requireNonNull(id);
        this.companyId = Objects.requireNonNull(companyId);
        this.applicationId = Objects.requireNonNull(applicationId);
        this.status = Objects.requireNonNull(status);
    }

    public void enable() {
        status = CompanyApplicationStatus.ENABLED;
    }

    public void disable() {
        status = CompanyApplicationStatus.DISABLED;
    }

    public boolean isEnabled() {
        return status == CompanyApplicationStatus.ENABLED;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID applicationId() {
        return applicationId;
    }

    public CompanyApplicationStatus status() {
        return status;
    }
}
