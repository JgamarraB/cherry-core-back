package com.cherrycore.shared.domain.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsByContentNotByName() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);

        assertThat(ImageType.detect(png)).contains(ImageType.PNG);
        assertThat(ImageType.detect(jpeg)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(webp)).contains(ImageType.WEBP);
    }

    @Test
    void rejectsSvgAndOtherContent() {
        byte[] svg = "<svg onload=\"alert(1)\"></svg>".getBytes(StandardCharsets.UTF_8);
        assertThat(ImageType.detect(svg)).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}
