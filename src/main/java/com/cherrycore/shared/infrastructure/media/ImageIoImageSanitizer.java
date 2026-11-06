package com.cherrycore.shared.infrastructure.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.application.port.ImageSanitizer;
import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.domain.error.DomainException;
import com.cherrycore.shared.domain.media.ImageType;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

/**
 * PNG y JPEG: se leen las dimensiones de la cabecera (sin decodificar), se
 * comprueba el máximo y se vuelven a codificar con ImageIO: el fichero nuevo
 * solo tiene los píxeles, sin EXIF/GPS ni otros metadatos.
 *
 * <p>WEBP: Java no lo decodifica sin una librería extra. Se leen las
 * dimensiones de la cabecera (VP8, VP8L y VP8X) y se reescribe el contenedor
 * RIFF sin los bloques de metadatos ({@code EXIF} y {@code XMP }); los píxeles
 * no se tocan.</p>
 */
@Component
class ImageIoImageSanitizer implements ImageSanitizer {

    private static final float JPEG_QUALITY = 0.92f;
    private static final int RIFF_HEADER = 12;
    private static final int CHUNK_HEADER = 8;
    private static final Set<String> WEBP_METADATA_CHUNKS = Set.of("EXIF", "XMP ");
    private static final String VP8X = "VP8X";
    private static final String VP8 = "VP8 ";
    private static final String VP8L = "VP8L";
    /* Fotograma de una animación (sus dimensiones las limita el lienzo de VP8X). */
    private static final String ANMF = "ANMF";
    /* Banderas de VP8X que anuncian metadatos (ya no están). */
    private static final int VP8X_METADATA_FLAGS = 0x08 | 0x04;
    private static final int VP8L_SIGNATURE = 0x2F;
    private static final int[] VP8_START_CODE = {0x9D, 0x01, 0x2A};
    private static final int FOURTEEN_BITS = 0x3FFF;

    private final int maxDimension;

    @Autowired
    ImageIoImageSanitizer(CherryCoreProperties properties) {
        this(properties.storage().maxImageDimension());
    }

    ImageIoImageSanitizer(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    @Override
    public byte[] sanitize(byte[] content, ImageType type) {
        return switch (type) {
            case PNG -> reencode(content, "png");
            case JPEG -> reencode(content, "jpeg");
            case WEBP -> stripWebpMetadata(content);
        };
    }

    private byte[] reencode(byte[] content, String format) {
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (input == null || !readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                // ignoreMetadata: ni siquiera se leen.
                reader.setInput(input, true, true);
                checkDimensions(reader.getWidth(0), reader.getHeight(0));
                image = reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException broken) {
            if (broken instanceof DomainException domain) {
                throw domain;
            }
            throw unreadable();
        }
        return write(image, format);
    }

    private static byte[] write(BufferedImage image, String format) {
        BufferedImage pixels = image;
        if ("jpeg".equals(format) && image.getColorModel().hasAlpha()) {
            pixels = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            pixels.getGraphics().drawImage(image, 0, 0, null);
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(format)) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(pixels, null, null), param);
        } catch (IOException | RuntimeException failure) {
            throw unreadable();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private byte[] stripWebpMetadata(byte[] content) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int vp8xOffset = -1;
        int width = 0;
        int height = 0;
        boolean hasImage = false;
        int position = RIFF_HEADER;
        while (position + CHUNK_HEADER <= content.length) {
            String fourCc = new String(content, position, 4, StandardCharsets.US_ASCII);
            long size = le32(content, position + 4);
            int data = position + CHUNK_HEADER;
            if (size > content.length - data) {
                throw unreadable();
            }
            int length = (int) size;
            switch (fourCc) {
                case VP8X -> {
                    requireLength(length, 10);
                    width = Math.max(width, le24(content, data + 4) + 1);
                    height = Math.max(height, le24(content, data + 7) + 1);
                    vp8xOffset = body.size();
                }
                case VP8 -> {
                    requireLength(length, 10);
                    for (int i = 0; i < VP8_START_CODE.length; i++) {
                        if ((content[data + 3 + i] & 0xFF) != VP8_START_CODE[i]) {
                            throw unreadable();
                        }
                    }
                    width = Math.max(width, le16(content, data + 6) & FOURTEEN_BITS);
                    height = Math.max(height, le16(content, data + 8) & FOURTEEN_BITS);
                    hasImage = true;
                }
                case VP8L -> {
                    requireLength(length, 5);
                    if ((content[data] & 0xFF) != VP8L_SIGNATURE) {
                        throw unreadable();
                    }
                    long bits = le32(content, data + 1);
                    width = Math.max(width, (int) (bits & FOURTEEN_BITS) + 1);
                    height = Math.max(height, (int) ((bits >> 14) & FOURTEEN_BITS) + 1);
                    hasImage = true;
                }
                case ANMF -> hasImage = true;
                default -> {
                    // Otros bloques (ICCP, ANIM, ALPH...) se copian tal cual.
                }
            }
            if (!WEBP_METADATA_CHUNKS.contains(fourCc)) {
                body.write(content, position, CHUNK_HEADER + length);
                if (length % 2 == 1) {
                    body.write(0);
                }
            }
            position = data + length + (length % 2);
        }
        if (!hasImage || width == 0 || height == 0) {
            throw unreadable();
        }
        checkDimensions(width, height);

        byte[] chunks = body.toByteArray();
        if (vp8xOffset >= 0) {
            chunks[vp8xOffset + CHUNK_HEADER] &= (byte) ~VP8X_METADATA_FLAGS;
        }
        ByteArrayOutputStream webp = new ByteArrayOutputStream(RIFF_HEADER + chunks.length);
        webp.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLe32(webp, 4 + chunks.length);
        webp.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        webp.writeBytes(chunks);
        return webp.toByteArray();
    }

    private void checkDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw unreadable();
        }
        if (width > maxDimension || height > maxDimension) {
            throw new DomainException(CommonErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
        }
    }

    private static void requireLength(int length, int minimum) {
        if (length < minimum) {
            throw unreadable();
        }
    }

    private static int le16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8;
    }

    private static int le24(byte[] bytes, int offset) {
        return le16(bytes, offset) | (bytes[offset + 2] & 0xFF) << 16;
    }

    private static long le32(byte[] bytes, int offset) {
        return (le24(bytes, offset) | (long) (bytes[offset + 3] & 0xFF) << 24) & 0xFFFFFFFFL;
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write(value >>> 8 & 0xFF);
        out.write(value >>> 16 & 0xFF);
        out.write(value >>> 24 & 0xFF);
    }

    private static DomainException unreadable() {
        return new DomainException(CommonErrorCode.IMAGE_UNREADABLE);
    }
}
