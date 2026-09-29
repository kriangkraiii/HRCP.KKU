package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;

/**
 * Rewrites saved certificate PINs from the legacy key to {@code APP_ESIGN_P12_MASTER_KEY}.
 *
 * <p>Runs on every start and only touches rows still in the legacy format, so a
 * second run is a no-op. A row that cannot be decrypted is left as it is and
 * counted: its owner simply types the PIN at the next signing.
 */
@Component
public class PinKeyMigration {

    private static final Logger log = LoggerFactory.getLogger(PinKeyMigration.class);

    private final UserDigitalCertificateRepository repository;
    private final CertificatePinEncryptionService encryption;

    public PinKeyMigration(UserDigitalCertificateRepository repository, CertificatePinEncryptionService encryption) {
        this.repository = repository;
        this.encryption = encryption;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        if (!encryption.hasCurrentKey()) {
            return;
        }
        int rewritten = 0;
        int unreadable = 0;
        try {
            for (UserDigitalCertificate cert : repository.findByEncryptedPinIsNotNull()) {
                if (!encryption.needsReEncryption(cert.getEncryptedPin())) {
                    continue;
                }
                String pin = encryption.decrypt(cert.getEncryptedPin());
                if (pin == null) {
                    unreadable++;
                    continue;
                }
                cert.setEncryptedPin(encryption.encrypt(pin));
                repository.save(cert);
                rewritten++;
            }
        } catch (RuntimeException e) {
            // Never block startup over this; the legacy rows still decrypt.
            log.error("Could not move saved certificate PINs to the new key: {}", e.toString());
            return;
        }
        if (rewritten > 0 || unreadable > 0) {
            log.info("Saved certificate PINs moved to APP_ESIGN_P12_MASTER_KEY: {} rewritten, {} unreadable",
                    rewritten, unreadable);
        }
    }
}
