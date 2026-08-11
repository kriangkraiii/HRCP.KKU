package com.ecom.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

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
            @Value("${app.upload.profile-image-dir:${user.dir}/uploads/profile_img}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
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

        String safeName = FileUtils.sanitizeFilename(file.getOriginalFilename());
        Path target = baseDir.resolve(safeName).normalize();

        // Defence in depth: sanitizeFilename already strips directory components,
        // but never write anywhere that is not under baseDir.
        if (!target.startsWith(baseDir)) {
            log.warn("Rejected profile image upload: resolved path escapes the upload directory");
            return null;
        }

        try {
            Files.createDirectories(baseDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
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
}
