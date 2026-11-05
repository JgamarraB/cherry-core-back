package com.cherrycore.shared.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.media.ImageType;
import com.cherrycore.support.TestImages;

class ImageIoImageSanitizerTest {

    private static final String GPS = "GPSLatitude=40.4168";

    private final ImageIoImageSanitizer sanitizer = new ImageIoImageSanitizer(4096);

    @Test
    void pngIsReencodedWithoutItsTextMetadata() throws IOException {
        byte[] original = TestImages.pngWithText(GPS);
        assertThat(TestImages.contains(original, GPS)).isTrue();

        byte[] clean = sanitizer.sanitize(original, ImageType.PNG);

        assertThat(TestImages.contains(clean, GPS)).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(clean)).getWidth()).isEqualTo(4);
    }

    @Test
    void jpegIsReencodedWithoutExif() throws IOException {
        byte[] original = TestImages.jpegWithExif(GPS);
        assertThat(TestImages.contains(original, GPS)).isTrue();

        byte[] clean = sanitizer.sanitize(original, ImageType.JPEG);

        assertThat(TestImages.contains(clean, GPS)).isFalse();
        assertThat(ImageIO.read(new ByteArrayInputStream(clean)).getHeight()).isEqualTo(4);
    }

    @Test
    void webpKeepsItsPixelsButLosesTheExifChunk() {
        byte[] original = TestImages.webpWithExif(16, 16, GPS);

        byte[] clean = sanitizer.sanitize(original, ImageType.WEBP);

        assertThat(TestImages.contains(clean, GPS)).isFalse();
        assertThat(TestImages.contains(clean, "EXIF")).isFalse();
        assertThat(TestImages.contains(clean, "VP8L")).isTrue();
        assertThat(clean[20] & 0x08).as("VP8X flag EXIF").isZero();
        assertThat(ImageType.detect(clean)).contains(ImageType.WEBP);
    }

    /*
     * "Bomba de descompresión": pocos KB que al abrirse ocupan GB. Se rechaza
     * leyendo solo la cabecera, antes de decodificar nada.
     */
    @Test
    void oversizedImagesAreRejectedFromTheirHeader() {
        assertCode(() -> sanitizer.sanitize(TestImages.png(5000, 1), ImageType.PNG), "IMAGE_DIMENSIONS_TOO_LARGE");
        assertCode(() -> sanitizer.sanitize(TestImages.jpeg(1, 4097), ImageType.JPEG),
                "IMAGE_DIMENSIONS_TOO_LARGE");
        assertCode(() -> sanitizer.sanitize(TestImages.webp(16000, 16000), ImageType.WEBP),
                "IMAGE_DIMENSIONS_TOO_LARGE");
        assertCode(() -> sanitizer.sanitize(TestImages.webpWithExif(9000, 10, GPS), ImageType.WEBP),
                "IMAGE_DIMENSIONS_TOO_LARGE");
    }

    @Test
    void brokenImagesAreRejected() {
        byte[] onlyPngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertCode(() -> sanitizer.sanitize(onlyPngSignature, ImageType.PNG), "IMAGE_UNREADABLE");
        byte[] onlyHeader = java.util.Arrays.copyOf(TestImages.webpWithExif(8, 8, GPS), 30);
        assertCode(() -> sanitizer.sanitize(onlyHeader, ImageType.WEBP), "IMAGE_UNREADABLE");
        byte[] truncated = java.util.Arrays.copyOf(TestImages.webpWithExif(8, 8, GPS), 40);
        assertCode(() -> sanitizer.sanitize(truncated, ImageType.WEBP), "IMAGE_UNREADABLE");
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(DomainException.class)
                .extracting(error -> ((DomainException) error).getErrorCode().code()).isEqualTo(code);
    }
}
