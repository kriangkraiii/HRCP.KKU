package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.repository.SignatureRequestRepository;

/**
 * Writes the finished copy of a document once every signature is in.
 *
 * <p>Runs asynchronously because converting to PDF shells out to LibreOffice and
 * takes seconds. Doing that inside the signing transaction would hold a database
 * transaction open for the whole conversion, and would make the last signer wait
 * on it — while the signature itself is already safely recorded either way.
 *
 * <p>A failure here leaves the envelope complete with no archived file. That is
 * the right trade: the signatures and their evidence live in the database, and
 * the copy can be regenerated, whereas losing a signature could not be undone.
 */
@Service
public class SignedDocumentArchiver {

    private static final Logger log = LoggerFactory.getLogger(SignedDocumentArchiver.class);

    private final SignatureRequestRepository requestRepository;
    private final SignedDocumentRenderer renderer;

    public SignedDocumentArchiver(SignatureRequestRepository requestRepository,
            SignedDocumentRenderer renderer) {
        this.requestRepository = requestRepository;
        this.renderer = renderer;
    }

    /**
     * Renders and stores the signed DOCX and PDF, recording their paths.
     *
     * <p>Uses the document-generation pool rather than the default executor so a
     * burst of completions cannot starve request threads.
     *
     * @param envelopeId the completed envelope; re-loaded here because the
     *                   caller's entity belongs to a transaction that has ended
     */
    @Async("docPrewarmExecutor")
    @Transactional
    public void archive(Long envelopeId) {
        SignatureRequest envelope = requestRepository.findByIdWithSteps(envelopeId).orElse(null);
        if (envelope == null) {
            log.warn("Envelope {} vanished before its signed copy could be archived", envelopeId);
            return;
        }

        SignedDocumentRenderer.StoredCopies copies = renderer.storeFinalCopies(envelope);
        if (copies == null) {
            return;
        }

        requestRepository.updateSignedDocumentPaths(envelopeId, copies.docxPath(), copies.pdfPath());

        log.info("Archived signed copy of envelope {} ({})", envelopeId, envelope.getVerificationCode());
    }
}
