package com.cherrycore.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record ApplicationPermission(UUID id, UUID applicationId, PermissionCode code, String description) {

    public static final int MAX_DESCRIPTION_LENGTH = 255;

    public ApplicationPermission {
        Objects.requireNonNull(id);
        Objects.requireNonNull(applicationId);
        Objects.requireNonNull(code);
    }
}
