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
}
