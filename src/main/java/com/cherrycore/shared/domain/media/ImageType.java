package com.cherrycore.shared.domain.media;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de imagen aceptados. El tipo se detecta por los primeros bytes del
 * fichero (no por la extensión ni por lo que diga el navegador, que se pueden
 * falsificar). SVG no se acepta: puede contener JavaScript.
 */
public enum ImageType {

    PNG("image/png", "png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 0),
    JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}, 0),
    WEBP("image/webp", "webp", new int[] {0x57, 0x45, 0x42, 0x50}, 8);

    private final String contentType;
    private final String extension;
    private final int[] signature;
    private final int signatureOffset;

    ImageType(String contentType, String extension, int[] signature, int signatureOffset) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
        this.signatureOffset = signatureOffset;
    }

    public static Optional<ImageType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private boolean matches(byte[] content) {
        if (content == null || content.length < signatureOffset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[signatureOffset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
