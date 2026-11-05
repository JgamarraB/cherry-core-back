package com.cherrycore.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * App de la plataforma que usa CherryCore para el login (Lirgar, ...).
 * CherryCore también es una app, con los permisos de gestión de empresa.
 */
public class Application {

    public static final int MAX_NAME_LENGTH = 100;

    private final UUID id;
    private final ApplicationCode code;
    private String name;
    private ApplicationStatus status;

    public Application(UUID id, ApplicationCode code, String name, ApplicationStatus status) {
        this.id = Objects.requireNonNull(id);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.status = Objects.requireNonNull(status);
    }

    public void rename(String newName) {
        name = Objects.requireNonNull(newName);
    }

    public void enable() {
        status = ApplicationStatus.ACTIVE;
    }

    public void disable() {
        status = ApplicationStatus.DISABLED;
    }

    public boolean isActive() {
        return status == ApplicationStatus.ACTIVE;
    }

    public UUID id() {
        return id;
    }

    public ApplicationCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public ApplicationStatus status() {
        return status;
    }
}
