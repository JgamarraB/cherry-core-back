package com.cherrycore.catalog.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.catalog.domain.Application;
import com.cherrycore.shared.application.port.FileStorage;
import com.cherrycore.shared.domain.media.ImageType;

/**
 * Dónde se guarda el logo de cada app y su URL pública.
 * Estructura: {@code applications/{applicationId}/public/branding/logo-{id}.{ext}}.
 */
@Component
public class ApplicationLogos {

    private static final String KEY_FORMAT = "applications/%s/public/branding/logo-%s.%s";

    private final FileStorage storage;

    public ApplicationLogos(FileStorage storage) {
        this.storage = storage;
    }

    String keyFor(UUID applicationId, UUID fileId, ImageType type) {
        return KEY_FORMAT.formatted(applicationId, fileId, type.extension());
    }

    public String urlOf(Application application) {
        return application.logoKey() == null ? null : storage.publicUrl(application.logoKey());
    }
}
