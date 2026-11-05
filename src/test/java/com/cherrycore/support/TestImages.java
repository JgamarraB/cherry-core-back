package com.cherrycore.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/**
 * Imágenes de verdad (y con metadatos) para probar la subida de logos.
 */
public final class TestImages {

    private static final int PNG_SIGNATURE_AND_IHDR = 8 + 25;

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    /** PNG con un bloque de texto (tEXt) como el que añaden cámaras y editores. */
    public static byte[] pngWithText(String text) {
        byte[] png = png(4, 4);
        byte[] data = ("Comment\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        byte[] type = "tEXt".getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        ByteBuffer chunk = ByteBuffer.allocate(12 + data.length)
                .putInt(data.length).put(type).put(data).putInt((int) crc.getValue());
        return splice(png, PNG_SIGNATURE_AND_IHDR, chunk.array());
    }

    /** JPEG con un segmento APP1 "Exif" (donde va la posición GPS de las fotos). */
    public static byte[] jpegWithExif(String text) {
        byte[] jpeg = jpeg(4, 4);
        byte[] payload = ("Exif\0\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer segment = ByteBuffer.allocate(4 + payload.length)
                .put((byte) 0xFF).put((byte) 0xE1).putShort((short) (payload.length + 2)).put(payload);
        return splice(jpeg, 2, segment.array());
    }

    /** WEBP extendido (VP8X) con imagen VP8L y un bloque EXIF. */
    public static byte[] webpWithExif(int width, int height, String text) {
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x08;
        putLe24(vp8x, 4, width - 1);
        putLe24(vp8x, 7, height - 1);
        int bits = (width - 1) | (height - 1) << 14;
        byte[] vp8l = {0x2F, (byte) bits, (byte) (bits >>> 8), (byte) (bits >>> 16), (byte) (bits >>> 24), 0, 0};
        byte[] exif = text.getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        chunk(chunks, "VP8X", vp8x);
        chunk(chunks, "VP8L", vp8l);
        chunk(chunks, "EXIF", exif);
        return riff(chunks.toByteArray());
    }

    /** WEBP simple (VP8L) con las dimensiones que diga su cabecera. */
    public static byte[] webp(int width, int height) {
        int bits = (width - 1) | (height - 1) << 14;
        ByteArrayOutputStream chunks = new ByteArrayOutputStream();
        chunk(chunks, "VP8L", new byte[] {0x2F, (byte) bits, (byte) (bits >>> 8), (byte) (bits >>> 16),
                (byte) (bits >>> 24), 0});
        return riff(chunks.toByteArray());
    }

    public static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static byte[] riff(byte[] chunks) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(4 + chunks.length));
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(chunks);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String fourCc, byte[] data) {
        out.writeBytes(fourCc.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(data.length));
        out.writeBytes(data);
        if (data.length % 2 == 1) {
            out.write(0);
        }
    }

    private static byte[] le32(int value) {
        return new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private static void putLe24(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
    }

    private static byte[] splice(byte[] original, int at, byte[] insert) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(original, 0, at);
        out.writeBytes(insert);
        out.write(original, at, original.length - at);
        return out.toByteArray();
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
        return out.toByteArray();
    }
}
