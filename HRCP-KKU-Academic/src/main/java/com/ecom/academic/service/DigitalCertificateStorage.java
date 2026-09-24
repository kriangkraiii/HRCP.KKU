package com.ecom.academic.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stores PKCS#12 (.p12) digital certificate files in a secure directory outside web-accessible paths.
 */
@Service
public class DigitalCertificateStorage {

    private static final Logger log = LoggerFactory.getLogger(DigitalCertificateStorage.class);
    private static final long MAX_CERT_SIZE_BYTES = 5 * 1024 * 1024; // 5MB limit

    private final Path baseDir;
    private final com.ecom.service.BlobMirror mirror;

    @org.springframework.beans.factory.annotation.Autowired
    public DigitalCertificateStorage(
            @Value("${app.upload.certificate-dir:${app.upload.dir:${user.dir}/uploads/}certificates}") String baseDir,
            com.ecom.service.BlobMirror mirror) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        this.mirror = mirror;
    }

    /** Disk only, no database copy — for tests that build the storage by hand. */
    public DigitalCertificateStorage(String baseDir) {
        this(baseDir, null);
    }

    /**
     * Stores the raw bytes of a .p12 certificate file and returns the relative filename.
     */
    public String store(byte[] certBytes) throws IOException {
        if (certBytes == null || certBytes.length == 0) {
            throw new IllegalArgumentException("Certificate payload is empty");
        }
        if (certBytes.length > MAX_CERT_SIZE_BYTES) {
            throw new IllegalArgumentException("Certificate file exceeds 5 MB limit");
        }

        Files.createDirectories(baseDir);
        String filename = "cert_" + UUID.randomUUID() + ".p12";
        Path target = resolveSafe(filename);

        Path temp = Files.createTempFile(baseDir, "cert_tmp_", ".p12");
        try {
            Files.write(temp, certBytes);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (mirror != null) {
                mirror.save(com.ecom.service.BlobMirror.CERTIFICATE, filename, certBytes);
            }
            return filename;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Reads the .p12 file bytes from storage.
     */
    public byte[] read(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        try {
            Path target = resolveSafe(filename);
            if (!Files.isRegularFile(target)) {
                // Uploaded on another machine sharing the database, or lost with a
                // disk: use the database copy and put the file back.
                byte[] copy = mirror != null ? mirror.load(com.ecom.service.BlobMirror.CERTIFICATE, filename) : null;
                if (copy == null) {
                    log.warn("Certificate file does not exist on disk or in the database: {}", filename);
                    return null;
                }
                try {
                    Files.createDirectories(baseDir);
                    Files.write(target, copy);
                    log.info("Restored certificate {} from the database copy", filename);
                } catch (IOException e) {
                    log.warn("Read certificate {} from the database but could not write it back: {}",
                            filename, e.getMessage());
                }
                return copy;
            }
            return Files.readAllBytes(target);
        } catch (Exception e) {
            log.error("Failed to read certificate {}: {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a certificate file from storage.
     */
    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Path target = resolveSafe(filename);
            Files.deleteIfExists(target);
            if (mirror != null) {
                mirror.delete(com.ecom.service.BlobMirror.CERTIFICATE, filename);
            }
        } catch (Exception e) {
            log.error("Failed to delete certificate {}: {}", filename, e.getMessage());
        }
    }

    public Path getBaseDir() {
        return baseDir;
    }

    private Path resolveSafe(String filename) {
        Path target = baseDir.resolve(filename).normalize();
        if (!target.startsWith(baseDir)) {
            throw new SecurityException("Refusing to touch path outside certificates directory: " + filename);
        }
        return target;
    }
}
