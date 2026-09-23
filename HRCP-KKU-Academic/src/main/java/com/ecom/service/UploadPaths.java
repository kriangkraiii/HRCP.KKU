package com.ecom.service;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that decides where uploaded and generated files live.
 *
 * <p>Everything is resolved against {@code app.upload.dir}. Before this class,
 * generated documents, attachments and signed copies were written to a bare
 * {@code "uploads/..."} path — relative to whatever directory the Windows service
 * happened to start in — while signature images followed {@code app.upload.dir}.
 * The two agreed only by coincidence of defaults, so a service started from a
 * different folder, or an {@code APP_UPLOAD_DIR} set on the server, made every
 * document look lost.
 *
 * <p>The database format does not change: paths are still stored as
 * {@code uploads/academic/5/doc_1.docx}. The leading {@code uploads/} is simply
 * read as "the upload root" instead of "a folder under the working directory",
 * so every existing row keeps working and older builds can still read new rows.
 */
@Component
public class UploadPaths {

    /** How every stored path starts; stands for the upload root. */
    public static final String STORED_PREFIX = "uploads/";

    private final Path root;

    public UploadPaths(@Value("${app.upload.dir:${user.dir}/uploads/}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * The layout used when no Spring context exists — plain unit tests that build
     * a service by hand. Same as the property's default.
     */
    public static UploadPaths workingDirectoryDefault() {
        return new UploadPaths(Path.of("uploads").toAbsolutePath().toString());
    }

    public Path root() {
        return root;
    }

    /** A directory or file under the upload root, e.g. {@code dir("academic", "5", "attachments")}. */
    public Path dir(String first, String... more) {
        return root.resolve(Path.of(first, more)).normalize();
    }

    /**
     * Where a path read from the database actually is.
     *
     * <p>{@code uploads/...} (either slash direction — rows written on Windows
     * through {@code Path.toString()} use backslashes) resolves against the upload
     * root. Absolute paths are taken as they are. Anything else is left relative
     * to the working directory, as it always was.
     *
     * @return the path, or null for a blank value or one that climbs out of the root
     */
    public Path resolve(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String normalized = stored.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith(STORED_PREFIX)) {
            Path resolved = root.resolve(normalized.substring(STORED_PREFIX.length())).normalize();
            return resolved.startsWith(root) ? resolved : null;
        }
        Path path = Path.of(stored.trim());
        return path.isAbsolute() ? path.normalize() : path.toAbsolutePath().normalize();
    }

    /**
     * The value to store in the database for a file under the upload root:
     * {@code uploads/} plus the path below the root, always with forward slashes.
     * A file outside the root is stored as its absolute path.
     */
    public String toStored(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) {
            return absolute.toString();
        }
        return STORED_PREFIX + root.relativize(absolute).toString().replace('\\', '/');
    }
}
