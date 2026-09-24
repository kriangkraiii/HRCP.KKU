package com.ecom.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.ecom.academic.service.DigitalCertificateStorage;

/**
 * Copies signature images and .p12 files already on this disk into the database.
 *
 * <p>Files stored before {@link BlobMirror} existed live only on the disk of the
 * machine that received them. Each machine that starts pushes the ones it has,
 * so after both test servers have started once, each can read the other's.
 *
 * <p>Only files a row actually points at are copied — a signature library entry,
 * a signed step, a certificate. The folders also hold whatever tests and deleted
 * entries left behind, and none of that belongs in a shared database. Rows
 * already copied are skipped, so later starts are cheap.
 */
@Component
public class StoredBlobBackfill {

    private static final Logger log = LoggerFactory.getLogger(StoredBlobBackfill.class);

    private final BlobMirror mirror;
    private final SignatureImageStorage signatureStorage;
    private final DigitalCertificateStorage certificateStorage;
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public StoredBlobBackfill(BlobMirror mirror, SignatureImageStorage signatureStorage,
            DigitalCertificateStorage certificateStorage, JdbcTemplate jdbc,
            @Value("${app.storage.blob-backfill.enabled:true}") boolean enabled) {
        this.mirror = mirror;
        this.signatureStorage = signatureStorage;
        this.certificateStorage = certificateStorage;
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        if (!enabled) {
            return;
        }
        try {
            int signatures = copy(BlobMirror.SIGNATURE, signatureStorage.getBaseDir(), jdbc.queryForList(
                    "SELECT image_path FROM user_signature WHERE image_path IS NOT NULL "
                            + "UNION SELECT image_path_snapshot FROM signature_step "
                            + "WHERE image_path_snapshot IS NOT NULL",
                    String.class));
            int certificates = copy(BlobMirror.CERTIFICATE, certificateStorage.getBaseDir(), jdbc.queryForList(
                    "SELECT certificate_path FROM user_digital_certificate WHERE certificate_path IS NOT NULL",
                    String.class));
            if (signatures + certificates > 0) {
                log.info("Copied/checked {} signature image(s) and {} certificate(s) from this disk into the database",
                        signatures, certificates);
            }
        } catch (RuntimeException e) {
            log.warn("Copying signature and certificate files into the database did not finish: {}", e.toString());
        }
    }

    private int copy(String kind, Path dir, List<String> filenames) {
        int found = 0;
        for (String filename : filenames) {
            Path file = dir.resolve(Path.of(filename).getFileName().toString()).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                mirror.save(kind, file.getFileName().toString(), Files.readAllBytes(file));
                found++;
            } catch (IOException e) {
                log.warn("Could not read {} for the database copy: {}", file, e.getMessage());
            }
        }
        return found;
    }
}
