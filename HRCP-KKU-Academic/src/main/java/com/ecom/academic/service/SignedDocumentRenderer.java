package com.ecom.academic.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.service.SignatureImageStorage;

/**
 * Renders an envelope's document, with whatever has been signed so far.
 *
 * <p>Always builds from the envelope's frozen snapshot, never from the live form.
 * That is the whole point of freezing: what the second signer sees must be
 * exactly what the first one signed, and the finished document must match both.
 *
 * <p>Partially-signed renders are used by the signing page, so a signer can see
 * the signatures already collected above the space where theirs will go.
 */
@Service
public class SignedDocumentRenderer {

    private static final Logger log = LoggerFactory.getLogger(SignedDocumentRenderer.class);

    /** Where finished, fully-signed copies are archived. */
    private static final String SIGNED_OUTPUT_DIR = "uploads/academic/signed";

    private final DocumentGenerationService documentGenerationService;
    private final SignatureStepRepository stepRepository;
    private final SignatureImageStorage signatureImageStorage;
    private final SignerNameResolver signerNameResolver;

    public SignedDocumentRenderer(DocumentGenerationService documentGenerationService,
            SignatureStepRepository stepRepository,
            SignatureImageStorage signatureImageStorage,
            SignerNameResolver signerNameResolver) {
        this.documentGenerationService = documentGenerationService;
        this.stepRepository = stepRepository;
        this.signatureImageStorage = signatureImageStorage;
        this.signerNameResolver = signerNameResolver;
    }

    /**
     * The document as it currently stands, including every signature collected.
     *
     * <p>The document carries the signatures only. The verification code still
     * exists on the envelope and {@code /esign/verify/{code}} still works, but
     * nothing is printed onto the document itself.
     */
    public byte[] renderDocx(SignatureRequest envelope) throws IOException {
        return renderDocx(envelope, null, null);
    }

    /**
     * The document rendered with collected signatures, plus optionally previewing
     * an active step's signature before confirmation.
     */
    public byte[] renderDocx(SignatureRequest envelope, SignatureStep previewStep, com.ecom.academic.model.UserSignature previewSig) throws IOException {
        List<StampedSignature> signatures = collectSignatures(envelope, previewStep, previewSig);

        String json = envelope.getFrozenJson();
        if (json == null || json.isBlank()) {
            json = "{}";
        }

        // ช่องลงนามที่ยังไม่มีใครเซ็นต้องขึ้นชื่อคนที่รอเซ็นอยู่ ไม่ใช่วงเล็บว่าง
        // เติมตอน render เท่านั้น — frozenJson ที่เก็บไว้และแฮชของมันไม่ถูกแตะ
        json = signerNameResolver.fillInto(envelope, json);

        return envelope.getModule() == SignatureModule.ACADEMIC
                ? documentGenerationService.generateSignedDocx(
                        envelope.getDocumentType(), json, signatures)
                : documentGenerationService.generateSignedP2Docx(
                        envelope.getDocumentType(), json, signatures);
    }

    /**
     * Stores the finished document alongside the request's other files.
     *
     * <p>Kept rather than re-generated on demand: a stored copy is what was
     * actually signed. Re-rendering later would depend on templates and code
     * that may have moved on since.
     *
     * @return the paths written, or null if the document could not be produced
     */
    public StoredCopies storeFinalCopies(SignatureRequest envelope) {
        try {
            byte[] docx = renderDocx(envelope);
            Path dir = Path.of(SIGNED_OUTPUT_DIR, String.valueOf(envelope.getRequestId()));
            Files.createDirectories(dir);

            String base = (envelope.getModule() == SignatureModule.ACADEMIC ? "doc_" : "p2doc_")
                    + envelope.getDocumentType() + "_signed_" + envelope.getVerificationCode();

            Path docxPath = dir.resolve(base + ".docx");
            Files.write(docxPath, docx);

            String pdfPath = null;
            if (documentGenerationService.isPdfConversionAvailable()) {
                byte[] pdf = documentGenerationService.convertDocxToPdfCached(docx);
                if (pdf != null && pdf.length > 0) {
                    Path target = dir.resolve(base + ".pdf");
                    Files.write(target, pdf);
                    pdfPath = target.toString();
                }
            }
            return new StoredCopies(docxPath.toString(), pdfPath);
        } catch (IOException e) {
            // The signatures themselves are already safe in the database; failing
            // to archive a copy must not undo them.
            log.error("Could not store the signed copy of envelope {}: {}", envelope.getId(), e.toString());
            return null;
        }
    }

    /** Where the finished copies were written. */
    public record StoredCopies(String docxPath, String pdfPath) {
    }

    /**
     * The same document as PDF, for on-screen review.
     *
     * @return the PDF, or null when LibreOffice is unavailable — callers offer
     *         the DOCX instead rather than failing the page
     */
    public byte[] renderPdf(SignatureRequest envelope) throws IOException {
        return renderPdf(envelope, null, null);
    }

    /**
     * Renders document as PDF with collected signatures and optional preview signature.
     */
    public byte[] renderPdf(SignatureRequest envelope, SignatureStep previewStep, com.ecom.academic.model.UserSignature previewSig) throws IOException {
        byte[] docx = renderDocx(envelope, previewStep, previewSig);
        if (!documentGenerationService.isPdfConversionAvailable()) {
            return null;
        }
        return documentGenerationService.convertDocxToPdfCached(docx);
    }

    /**
     * Loads the image for each signed step, plus optional preview signature.
     *
     * <p>Reads {@code imagePathSnapshot} rather than following the link to the
     * signer's library: someone deleting a signature from their own library must
     * not blank out a document they already signed.
     */
    private List<StampedSignature> collectSignatures(SignatureRequest envelope) {
        return collectSignatures(envelope, null, null);
    }

    private List<StampedSignature> collectSignatures(SignatureRequest envelope,
            SignatureStep previewStep, com.ecom.academic.model.UserSignature previewSig) {
        List<StampedSignature> stamped = new ArrayList<>();

        for (SignatureStep step : stepRepository.findSignedSteps(envelope.getId())) {
            String path = step.getImagePathSnapshot();
            if (path == null || path.isBlank()) {
                continue;
            }
            byte[] png = signatureImageStorage.read(path);
            if (png == null) {
                // Better a document missing one signature, clearly, than a failed
                // render that hides the rest.
                log.warn("Signature image {} for step {} is missing; rendering without it",
                        path, step.getId());
                continue;
            }

            // Measure the file rather than trusting the stored dimensions: the
            // aspect ratio decides the printed size, and the row could be stale.
            int width = 0;
            int height = 0;
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (IOException e) {
                log.warn("Could not measure signature image {}: {}", path, e.toString());
            }

            stamped.add(new StampedSignature(step.getAnchorPlaceholder(), png, width, height));
        }

        // Place preview signature onto active step's anchor if provided and not already signed
        if (previewStep != null && previewSig != null && previewStep.getAnchorPlaceholder() != null) {
            boolean alreadyStamped = stamped.stream()
                    .anyMatch(s -> s.anchorPlaceholder().equals(previewStep.getAnchorPlaceholder()));
            if (!alreadyStamped && previewSig.getImagePath() != null && !previewSig.getImagePath().isBlank()) {
                byte[] png = signatureImageStorage.read(previewSig.getImagePath());
                if (png != null) {
                    int width = 0;
                    int height = 0;
                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
                        if (image != null) {
                            width = image.getWidth();
                            height = image.getHeight();
                        }
                    } catch (IOException e) {
                        log.warn("Could not measure preview signature image {}: {}", previewSig.getImagePath(), e.toString());
                    }
                    stamped.add(new StampedSignature(previewStep.getAnchorPlaceholder(), png, width, height));
                }
            }
        }

        return stamped;
    }
}
