package com.ecom.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stores signature images.
 *
 * <p>Modelled on {@link ProfileImageStorage}: filenames are generated here, and
 * every resolved path is verified to stay inside the configured directory before
 * anything is written or read.
 *
 * <p>Two things differ, both deliberate:
 * <ul>
 *   <li><b>Input is raw PNG bytes, not a {@code MultipartFile}.</b> All three
 *       ways of making a signature — drawing, uploading, typing — are rendered to
 *       a canvas in the browser and exported as PNG, so exactly one format ever
 *       reaches the server and the DOCX stamping code has one case to handle.</li>
 *   <li><b>The directory is not the statically-served upload root.</b>
 *       {@code WebConfig} maps {@code /uploads/**} to a resource handler that any
 *       logged-in user can read; a signature image is personal data and is served
 *       only through an ownership-checked controller.</li>
 * </ul>
 */
@Component
public class SignatureImageStorage {

    private static final Logger log = LoggerFactory.getLogger(SignatureImageStorage.class);

    /**
     * Ceiling for a decoded signature PNG.
     *
     * <p>1 MB, not because the disk cares but because the image arrives as a
     * base64 data URL inside a normal form post. Base64 inflates by about a
     * third, and Tomcat's default {@code maxHttpFormPostSize} is 2 MB — a larger
     * cap here would let the request be rejected by the container before any of
     * this validation could produce a usable error message. A trimmed line
     * drawing is typically well under 100 KB.
     */
    private static final long MAX_SIZE_BYTES = 1024 * 1024;
    private static final int MAX_WIDTH_PX = 1600;
    private static final int MAX_HEIGHT_PX = 600;
    private static final String DATA_URL_PREFIX = "data:image/png;base64,";

    private final Path baseDir;

    public SignatureImageStorage(
            @Value("${app.upload.signature-dir:${app.upload.dir:${user.dir}/uploads/}signatures}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    /** What a stored signature image is, once decoded and validated. */
    public record StoredImage(String filename, int width, int height) {
    }

    /**
     * Decodes a {@code data:image/png;base64,...} URL produced by the browser
     * canvas.
     *
     * @return the raw PNG bytes, or null when the value is not a PNG data URL
     */
    public byte[] decodeDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(DATA_URL_PREFIX)) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(dataUrl.substring(DATA_URL_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            log.warn("Rejected signature image: payload is not valid base64");
            return null;
        }
    }

    /**
     * Validates and stores a signature PNG.
     *
     * @return the stored file's details, or null when the image was rejected
     */
    public StoredImage store(byte[] pngBytes) {
        if (pngBytes == null || pngBytes.length == 0) {
            return null;
        }
        if (pngBytes.length > MAX_SIZE_BYTES) {
            log.warn("Rejected signature image: {} bytes exceeds the {} byte limit",
                    pngBytes.length, MAX_SIZE_BYTES);
            return null;
        }

        // The bytes claim to be a PNG; decoding is what actually proves it. This
        // is the check that stops an executable renamed to .png.
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        } catch (IOException e) {
            log.warn("Rejected signature image: could not be decoded ({})", e.toString());
            return null;
        }
        if (image == null) {
            log.warn("Rejected signature image: not a readable image");
            return null;
        }
        if (image.getWidth() > MAX_WIDTH_PX || image.getHeight() > MAX_HEIGHT_PX) {
            log.warn("Rejected signature image: {}x{} exceeds {}x{}",
                    image.getWidth(), image.getHeight(), MAX_WIDTH_PX, MAX_HEIGHT_PX);
            return null;
        }

        String filename = "sig_" + UUID.randomUUID().toString().replace("-", "") + ".png";
        Path target = baseDir.resolve(filename).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("Rejected signature image: resolved path escapes the storage directory");
            return null;
        }

        try {
            Files.createDirectories(baseDir);
            Files.write(target, pngBytes);
            return new StoredImage(filename, image.getWidth(), image.getHeight());
        } catch (IOException e) {
            log.error("Failed to store signature image {}: {}", filename, e.getMessage(), e);
            return null;
        }
    }

    /** Reads a stored signature, or null when it is missing or the name is unsafe. */
    public byte[] read(String filename) {
        Path target = resolveSafely(filename);
        if (target == null || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            log.error("Failed to read signature image {}: {}", filename, e.getMessage(), e);
            return null;
        }
    }

    public boolean deleteIfPresent(String filename) {
        Path target = resolveSafely(filename);
        if (target == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.error("Failed to delete signature image {}: {}", filename, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Resolves a stored filename to a path inside the storage directory.
     *
     * @return the path, or null if the name is blank or would escape the directory
     */
    private Path resolveSafely(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String safe = Path.of(filename).getFileName().toString()
                .replace("\0", "").replace("/", "").replace("\\", "").trim();
        if (safe.isBlank()) {
            return null;
        }
        Path target = baseDir.resolve(safe).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("Refusing to touch a signature path outside the storage directory: {}", filename);
            return null;
        }
        return target;
    }

    public Path getBaseDir() {
        return baseDir;
    }
}
