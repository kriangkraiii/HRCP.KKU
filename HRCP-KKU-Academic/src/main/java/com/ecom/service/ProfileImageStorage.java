package com.ecom.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.util.FileUtils;

/**
 * Stores profile images safely.
 *
 * Every filename that reaches the filesystem is generated here — the client's
 * filename is only ever used to derive the extension, and the resolved path is
 * verified to stay inside the configured directory before anything is written.
 */
@Component
public class ProfileImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ProfileImageStorage.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private final Path baseDir;

    public ProfileImageStorage(
            @Value("${app.upload.profile-image-dir:${app.upload.dir:${user.dir}/uploads/}profile_img}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    /**
     * Checks if a profile image file physically exists in the storage directory.
     *
     * @param filename the stored filename to check
     * @return true if the file exists on disk, false otherwise
     */
    public boolean exists(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        Path target = baseDir.resolve(FileUtils.sanitizeFilename(filename)).normalize();
        return target.startsWith(baseDir) && Files.isRegularFile(target);
    }

    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Validates and stores the upload.
     *
     * @return the generated filename to persist on the account, or {@code null}
     *         if there was nothing to store or the file was rejected
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String rejection = validate(file);
        if (rejection != null) {
            log.warn("Rejected profile image upload: {}", rejection);
            return null;
        }

        String extension = extractExtension(file.getOriginalFilename());
        String safeName = UUID.randomUUID().toString().substring(0, 8) + "_" + FileUtils.sanitizeFilename(file.getOriginalFilename());
        Path target = baseDir.resolve(safeName).normalize();

        // Defence in depth: sanitizeFilename already strips directory components,
        // but never write anywhere that is not under baseDir.
        if (!target.startsWith(baseDir)) {
            log.warn("Rejected profile image upload: resolved path escapes the upload directory");
            return null;
        }

        try {
            Files.createDirectories(baseDir);
            byte[] optimized = optimizeImage(file.getBytes(), extension);
            Files.write(target, optimized);
            return safeName;
        } catch (IOException e) {
            log.error("Failed to store profile image {}: {}", safeName, e.getMessage(), e);
            return null;
        }
    }

    /** @return a reason string when the upload must be rejected, otherwise null */
    private String validate(MultipartFile file) {
        if (file.getSize() > MAX_SIZE_BYTES) {
            return "file exceeds 5MB";
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return "extension '" + extension + "' is not an allowed image type";
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return "content type '" + contentType + "' is not an image";
        }

        // Extension and content type are both chosen by the uploader, so neither
        // proves anything. Decoding the bytes is what actually rejects a file
        // that merely claims to be an image.
        if (!decodesAsImage(file)) {
            return "content is not a decodable image";
        }

        return null;
    }

    /**
     * Stores image bytes that did not arrive as an upload — currently a photo
     * fetched from the college website.
     *
     * <p>The checks are the same ones an upload gets. The bytes come from another
     * server rather than a browser, which makes them less trustworthy, not more:
     * whatever that server returns ends up inside our upload directory and is then
     * served back to every user.
     *
     * @param content   the downloaded bytes
     * @param baseName  a name to build the file from; the extension decides the type
     * @return the stored filename, or null if the bytes were rejected
     */
    public String storeFromBytes(byte[] content, String baseName) {
        if (content == null || content.length == 0) {
            return null;
        }
        if (content.length > MAX_SIZE_BYTES) {
            log.warn("Rejected fetched profile image: exceeds 5MB");
            return null;
        }

        String extension = extractExtension(baseName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            log.warn("Rejected fetched profile image: extension '{}' is not allowed", extension);
            return null;
        }

        if (!decodesAsImage(content)) {
            log.warn("Rejected fetched profile image: content is not a decodable image");
            return null;
        }

        String safeName = FileUtils.sanitizeFilename(baseName);
        Path target = baseDir.resolve(safeName).normalize();
        if (!target.startsWith(baseDir)) {
            log.warn("Rejected fetched profile image: resolved path escapes the upload directory");
            return null;
        }

        try {
            Files.createDirectories(baseDir);
            byte[] optimized = optimizeImage(content, extension);
            Files.write(target, optimized);
            return safeName;
        } catch (IOException e) {
            log.error("Failed to store fetched profile image {}: {}", safeName, e.getMessage(), e);
            return null;
        }
    }

    private boolean decodesAsImage(byte[] content) {
        try (java.io.InputStream in = new java.io.ByteArrayInputStream(content)) {
            return javax.imageio.ImageIO.read(in) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private boolean decodesAsImage(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            return javax.imageio.ImageIO.read(in) != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Resizes and compresses an image to max 512x512 with high quality scaling.
     * Dramatically reduces disk space and page load time.
     */
    private byte[] optimizeImage(byte[] rawBytes, String extension) {
        if (rawBytes == null || rawBytes.length == 0) {
            return rawBytes;
        }
        try (var in = new java.io.ByteArrayInputStream(rawBytes)) {
            var original = javax.imageio.ImageIO.read(in);
            if (original == null) {
                return rawBytes;
            }

            int origWidth = original.getWidth();
            int origHeight = original.getHeight();
            int maxDim = 512;

            if (origWidth <= maxDim && origHeight <= maxDim && rawBytes.length < 200_000) {
                return rawBytes; // Already compact
            }

            double scale = Math.min((double) maxDim / origWidth, (double) maxDim / origHeight);
            if (scale > 1.0) scale = 1.0;
            int newWidth = Math.max(1, (int) Math.round(origWidth * scale));
            int newHeight = Math.max(1, (int) Math.round(origHeight * scale));

            int imageType = (original.getTransparency() == java.awt.Transparency.OPAQUE)
                    ? java.awt.image.BufferedImage.TYPE_INT_RGB
                    : java.awt.image.BufferedImage.TYPE_INT_ARGB;

            var resized = new java.awt.image.BufferedImage(newWidth, newHeight, imageType);
            var g2d = resized.createGraphics();
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(original, 0, 0, newWidth, newHeight, null);
            g2d.dispose();

            var out = new java.io.ByteArrayOutputStream();
            String format = ("png".equalsIgnoreCase(extension) || "gif".equalsIgnoreCase(extension)) ? extension : "jpg";
            if ("jpg".equalsIgnoreCase(format) && imageType == java.awt.image.BufferedImage.TYPE_INT_ARGB) {
                format = "png";
            }
            javax.imageio.ImageIO.write(resized, format, out);
            return out.toByteArray();
        } catch (Exception e) {
            log.warn("Image optimization fallback to raw bytes: {}", e.getMessage());
            return rawBytes;
        }
    }
}
