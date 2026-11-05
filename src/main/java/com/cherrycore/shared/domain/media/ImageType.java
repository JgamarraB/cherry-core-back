package com.cherrycore.shared.domain.media;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de imagen aceptados. El tipo se detecta por los primeros bytes del
 * fichero (no por la extensión ni por lo que diga el navegador, que se pueden
 * falsificar). SVG no se acepta: puede contener JavaScript.
 */
public enum ImageType {

    PNG("image/png", "png", new Signature(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    JPEG("image/jpeg", "jpg", new Signature(0, 0xFF, 0xD8, 0xFF)),
    // RIFF....WEBP: contenedor RIFF de tipo WEBP.
    WEBP("image/webp", "webp", new Signature(0, 0x52, 0x49, 0x46, 0x46), new Signature(8, 0x57, 0x45, 0x42, 0x50));

    private final String contentType;
    private final String extension;
    private final Signature[] signatures;

    ImageType(String contentType, String extension, Signature... signatures) {
        this.contentType = contentType;
        this.extension = extension;
        this.signatures = signatures;
    }

    public static Optional<ImageType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        return content != null && Arrays.stream(signatures).allMatch(signature -> signature.matches(content));
    }

    /**
     * Bytes que deben aparecer a partir de una posición.
     */
    private record Signature(int offset, int... bytes) {

        boolean matches(byte[] content) {
            if (content.length < offset + bytes.length) {
                return false;
            }
            for (int i = 0; i < bytes.length; i++) {
                if ((content[offset + i] & 0xFF) != bytes[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
