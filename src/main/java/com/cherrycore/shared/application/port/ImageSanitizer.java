package com.cherrycore.shared.application.port;

import com.cherrycore.shared.domain.media.ImageType;

/**
 * Limpia una imagen subida antes de publicarla: quita los metadatos (EXIF,
 * GPS, comentarios...) y rechaza las de dimensiones excesivas, que pesan poco
 * pero ocupan cientos de MB al abrirlas ("bombas de descompresión").
 */
public interface ImageSanitizer {

    /**
     * @param content imagen cuyo formato ya se ha detectado por su contenido
     * @return la imagen sin metadatos, lista para guardar
     * @throws com.cherrycore.shared.domain.error.DomainException
     *         {@code IMAGE_UNREADABLE} si no es una imagen válida de ese formato, o
     *         {@code IMAGE_DIMENSIONS_TOO_LARGE} si supera el ancho o alto máximo
     */
    byte[] sanitize(byte[] content, ImageType type);
}
