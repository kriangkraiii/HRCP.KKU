package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;

/**
 * Encrypts .p12 files stored before encryption at rest existed (disk and database copy).
 *
 * <p>Runs at every start and only touches files still in plain PKCS#12 form, so it
 * is a no-op once done. Without APP_ESIGN_P12_MASTER_KEY it does nothing.
 */
@Component
public class CertificateFileEncryption {

    private static final Logger log = LoggerFactory.getLogger(CertificateFileEncryption.class);

    private final UserDigitalCertificateRepository repository;
    private final DigitalCertificateStorage storage;
    private final CertificatePinEncryptionService encryption;

    public CertificateFileEncryption(UserDigitalCertificateRepository repository, DigitalCertificateStorage storage,
            CertificatePinEncryptionService encryption) {
        this.repository = repository;
        this.storage = storage;
        this.encryption = encryption;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void encryptExisting() {
        if (!encryption.hasCurrentKey()) {
            return;
        }
        int done = 0, missing = 0;
        try {
            for (UserDigitalCertificate cert : repository.findAll()) {
                byte[] stored = storage.readStored(cert.getCertificatePath());
                if (stored == null) {
                    missing++;
                    continue;
                }
                if (!CertificatePinEncryptionService.isEncryptedFile(stored)) {
                    storage.rewrite(cert.getCertificatePath(), encryption.encryptFile(stored));
                    done++;
                }
            }
        } catch (Exception e) {
            // Plain files still work; never block startup over this.
            log.error("Could not encrypt stored certificate files: {}", e.toString());
            return;
        }
        if (done > 0 || missing > 0) {
            log.info("Certificate files encrypted at rest: {} encrypted now, {} missing", done, missing);
        }
    }
}
