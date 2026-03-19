package com.aseubel.yusi.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Slf4j
public final class ImageUtils {

    private static final long MAX_FILE_SIZE = 300 * 1024L;
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 1280;
    private static final float INITIAL_QUALITY = 0.85f;
    private static final float QUALITY_STEP = 0.15f;
    private static final float MIN_QUALITY = 0.2f;

    private ImageUtils() {
    }

    public static byte[] compressImage(byte[] imageBytes) throws IOException {
        if (imageBytes == null || imageBytes.length == 0) {
            return imageBytes;
        }

        if (imageBytes.length <= MAX_FILE_SIZE) {
            return imageBytes;
        }

        String format = getImageFormat(imageBytes);
        if (format == null) {
            format = "jpg";
        }

        BufferedImage originalImage = readImage(imageBytes);
        if (originalImage == null) {
            return imageBytes;
        }

        BufferedImage resizedImage = resizeImageIfNeeded(originalImage);

        byte[] compressed = compressToBytes(resizedImage, format, INITIAL_QUALITY);

        float quality = INITIAL_QUALITY;
        while (compressed.length > MAX_FILE_SIZE && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP;
            compressed = compressToBytes(resizedImage, format, quality);
        }

        if (compressed.length > MAX_FILE_SIZE && resizedImage != originalImage) {
            float scale = (float) Math.sqrt((double) MAX_FILE_SIZE / compressed.length);
            int newWidth = (int) (resizedImage.getWidth() * scale);
            int newHeight = (int) (resizedImage.getHeight() * scale);
            BufferedImage smallerImage = resizeImage(resizedImage, newWidth, newHeight);
            compressed = compressToBytes(smallerImage, format, INITIAL_QUALITY);

            quality = INITIAL_QUALITY;
            while (compressed.length > MAX_FILE_SIZE && quality > MIN_QUALITY) {
                quality -= QUALITY_STEP;
                compressed = compressToBytes(smallerImage, format, quality);
            }
        }

        log.debug("Image compressed from {} bytes to {} bytes", imageBytes.length, compressed.length);
        return compressed;
    }

    public static byte[] compressImage(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        return compressImage(bytes);
    }

    private static BufferedImage readImage(byte[] imageBytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        ImageInputStream iis = ImageIO.createImageInputStream(bais);
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, true);
            BufferedImage image = reader.read(0);
            reader.dispose();
            return image;
        } finally {
            iis.close();
        }
    }

    private static BufferedImage resizeImageIfNeeded(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= MAX_WIDTH && height <= MAX_HEIGHT) {
            return image;
        }

        float ratio = Math.min((float) MAX_WIDTH / width, (float) MAX_HEIGHT / height);
        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);

        return resizeImage(image, newWidth, newHeight);
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return resizedImage;
    }

    private static byte[] compressToBytes(BufferedImage image, String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgbImage.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            ImageIO.write(rgbImage, format, baos);
        } else {
            ImageIO.write(image, format, baos);
        }

        return baos.toByteArray();
    }

    private static String getImageFormat(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 4) {
            return null;
        }

        if (imageBytes[0] == (byte) 0xFF && imageBytes[1] == (byte) 0xD8) {
            return "jpg";
        }
        if (imageBytes[0] == (byte) 0x89 && imageBytes[1] == (byte) 0x50) {
            return "png";
        }
        if (imageBytes[0] == (byte) 0x47 && imageBytes[1] == (byte) 0x49) {
            return "gif";
        }
        if (imageBytes[0] == (byte) 0x52 && imageBytes[1] == (byte) 0x49) {
            return "webp";
        }

        return null;
    }

    public static Dimension getImageDimensions(byte[] imageBytes) throws IOException {
        BufferedImage image = readImage(imageBytes);
        if (image == null) {
            return new Dimension(0, 0);
        }
        return new Dimension(image.getWidth(), image.getHeight());
    }
}
