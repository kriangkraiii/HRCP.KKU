package com.ecom.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.service.SignatureImageStorage;
import com.ecom.service.UploadPaths;

/**
 * Checks at startup that the files the database points at are where this run
 * will look for them.
 *
 * <p>Every file resolves against {@code app.upload.dir} (see {@link UploadPaths}),
 * which defaults to {@code uploads/} under the working directory. If the Windows
 * service starts in a different folder, or {@code APP_UPLOAD_DIR} points somewhere
 * new, nothing is deleted — but every signature and document looks gone, and the
 * render code only logs a warning per file. This makes that one loud line at
 * startup instead.
 *
 * <p>Logs only, never stops the app: a wrong directory is fixed by restarting in
 * the right one, and refusing to start would take the whole site down for it.
 */
@Component
public class UploadStorageCheck {

    private static final Logger log = LoggerFactory.getLogger(UploadStorageCheck.class);

    private final UploadPaths uploadPaths;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureStepRepository stepRepository;
    private final AcademicDocumentRepository documentRepository;

    public UploadStorageCheck(UploadPaths uploadPaths,
            SignatureImageStorage signatureImageStorage,
            SignatureStepRepository stepRepository,
            AcademicDocumentRepository documentRepository) {
        this.uploadPaths = uploadPaths;
        this.signatureImageStorage = signatureImageStorage;
        this.stepRepository = stepRepository;
        this.documentRepository = documentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        log.info("Upload storage: working directory {} | upload root {} | signatures {}",
                Path.of("").toAbsolutePath(), uploadPaths.root(), signatureImageStorage.getBaseDir());

        try {
            stepRepository.findFirstByImagePathSnapshotIsNotNullOrderByIdDesc().ifPresent(step -> {
                if (signatureImageStorage.read(step.getImagePathSnapshot()) == null) {
                    log.error("Signature image {} of signed step {} is not in {}. Signed documents will "
                            + "render without their signatures — check the service's working directory "
                            + "and APP_UPLOAD_DIR / APP_SIGNATURE_DIR.",
                            step.getImagePathSnapshot(), step.getId(), signatureImageStorage.getBaseDir());
                }
            });
            // A single missing document proves little — drafts get deleted and files
            // regenerated — but no upload root at all while documents exist means
            // this run is looking in the wrong place.
            documentRepository.findFirstByGeneratedFilePathIsNotNullOrderByIdDesc().ifPresent(doc -> {
                if (!Files.isDirectory(uploadPaths.root())) {
                    log.error("Upload root {} does not exist, yet documents such as {} were generated before. "
                            + "Check the service's working directory and APP_UPLOAD_DIR.",
                            uploadPaths.root(), doc.getGeneratedFilePath());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Upload storage check could not run: {}", e.toString());
        }
    }
}
