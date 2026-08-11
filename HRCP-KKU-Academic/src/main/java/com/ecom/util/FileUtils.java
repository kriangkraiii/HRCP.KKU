package com.ecom.util;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Utility for safe file handling.
 * Prevents Path Traversal attacks by sanitizing user-provided filenames.
 */
public final class FileUtils {

    private FileUtils() {
    }

    /**
     * Sanitize a user-provided filename to prevent Path Traversal.
     * Strips directory components and prepends a UUID to avoid collisions.
     *
     * @param originalFilename the raw filename from the upload
     * @return a safe filename like "a3b8d1b6_report.pdf"
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return UUID.randomUUID().toString();
        }

        // Strip all directory components (prevents ../../../etc/passwd)
        String baseName = Path.of(originalFilename).getFileName().toString();

        // Remove any remaining path separators or null bytes
        baseName = baseName.replace("\0", "")
                .replace("/", "")
                .replace("\\", "");

        if (baseName.isBlank() || baseName.equals(".") || baseName.equals("..")) {
            return UUID.randomUUID().toString();
        }

        // Add UUID prefix to prevent overwrites
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return shortUuid + "_" + baseName;
    }

    /**
     * Build a Content-Disposition header for a download.
     *
     * <p>Stored filenames come from the upload request, so they are neither
     * trusted in the quoted-string form nor assumed to be ASCII. Quotes,
     * backslashes and control characters are stripped from the plain
     * {@code filename}, and the exact name is carried in the RFC 5987
     * {@code filename*} parameter so Thai names still arrive intact.
     */
    public static String contentDisposition(String originalFilename) {
        String name = (originalFilename == null || originalFilename.isBlank())
                ? "download"
                : originalFilename;

        String ascii = name.replaceAll("[\\p{Cntrl}\"\\\\]", "").trim();
        if (ascii.isEmpty()) {
            ascii = "download";
        }

        String encoded = java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
