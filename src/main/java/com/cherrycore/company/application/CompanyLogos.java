package com.cherrycore.company.application;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cherrycore.company.domain.Company;
import com.cherrycore.shared.application.port.FileStorage;
import com.cherrycore.shared.domain.media.ImageType;

/**
 * Dónde se guarda el logo de cada empresa y su URL pública.
 * Estructura: {@code companies/{companyId}/public/branding/logo-{id}.{ext}}.
 */
@Component
public class CompanyLogos {

    private static final String KEY_FORMAT = "companies/%s/public/branding/logo-%s.%s";

    private final FileStorage storage;

    public CompanyLogos(FileStorage storage) {
        this.storage = storage;
    }

    String keyFor(UUID companyId, UUID fileId, ImageType type) {
        return KEY_FORMAT.formatted(companyId, fileId, type.extension());
    }

    public String urlOf(Company company) {
        return company.logoKey() == null ? null : storage.publicUrl(company.logoKey());
    }

    public CompanyView view(Company company) {
        return CompanyView.of(company, urlOf(company));
    }
}
