package com.cherrycore.company.application;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cherrycore.company.application.port.CompanyRepository;
import com.cherrycore.company.domain.Company;
import com.cherrycore.company.domain.CompanyErrorCode;
import com.cherrycore.company.domain.CompanyPermission;
import com.cherrycore.company.domain.CompanyProfileData;
import com.cherrycore.shared.application.port.FileStorage;
import com.cherrycore.shared.application.port.ImageSanitizer;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.id.UuidV7;
import com.cherrycore.shared.domain.media.ImageType;

/**
 * Datos y logo de la empresa con la que trabaja el usuario.
 */
@Service
public class CompanyProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompanyProfileService.class);
    private static final String LOG_OLD_LOGO_NOT_DELETED = "Old logo could not be deleted [{}]";

    private final CompanyAccessPolicy access;
    private final CompanyRepository companies;
    private final CompanyLogos logos;
    private final FileStorage storage;
    private final ImageSanitizer images;
    private final CompanySettings settings;
    private final Clock clock;

    public CompanyProfileService(CompanyAccessPolicy access, CompanyRepository companies, CompanyLogos logos,
            FileStorage storage, ImageSanitizer images, CompanySettings settings, Clock clock) {
        this.images = images;
        this.access = access;
        this.companies = companies;
        this.logos = logos;
        this.storage = storage;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CompanyView get(StaffActor actor) {
        return logos.view(access.requireMember(actor).company());
    }

    @Transactional
    public CompanyView update(StaffActor actor, CompanyDataCommand data) {
        Company company = access.require(actor, CompanyPermission.PROFILE_MANAGE).company();
        CompanyProfileData profile = data.toProfile();
        if (companies.existsByTaxId(profile.country(), profile.taxId(), company.id())) {
            throw new DomainException(CompanyErrorCode.TAX_ID_ALREADY_REGISTERED);
        }
        company.updateProfile(profile);
        companies.save(company);
        return logos.view(company);
    }

    /**
     * Sube el logo. El formato se comprueba por el contenido del fichero (PNG,
     * JPEG o WEBP); se ignora el nombre y el tipo que diga el navegador.
     */
    @Transactional
    public CompanyView uploadLogo(StaffActor actor, byte[] content) {
        Company company = access.require(actor, CompanyPermission.PROFILE_MANAGE).company();
        if (content.length > settings.maxLogoBytes()) {
            throw new DomainException(CompanyErrorCode.LOGO_TOO_LARGE);
        }
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new DomainException(CompanyErrorCode.LOGO_INVALID_FORMAT));
        // Sin metadatos (EXIF, GPS...) y con dimensiones razonables.
        byte[] clean = images.sanitize(content, type);

        String previousKey = company.logoKey();
        String key = logos.keyFor(company.id(), UuidV7.generate(clock), type);
        storage.storePublic(key, clean, type.contentType());
        company.changeLogo(key);
        companies.save(company);
        deleteQuietly(previousKey);
        return logos.view(company);
    }

    @Transactional
    public CompanyView removeLogo(StaffActor actor) {
        Company company = access.require(actor, CompanyPermission.PROFILE_MANAGE).company();
        String previousKey = company.logoKey();
        company.changeLogo(null);
        companies.save(company);
        deleteQuietly(previousKey);
        return logos.view(company);
    }

    /* Si falla el borrado del logo anterior solo queda un fichero huérfano: no se interrumpe la operación. */
    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException deleteFailure) {
            LOGGER.warn(LOG_OLD_LOGO_NOT_DELETED, key, deleteFailure);
        }
    }
}
