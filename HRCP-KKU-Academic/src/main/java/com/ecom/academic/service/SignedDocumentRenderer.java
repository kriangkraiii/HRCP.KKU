package com.ecom.academic.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.service.SignatureImageStorage;
import com.ecom.service.UploadPaths;

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

    private final DocumentGenerationService documentGenerationService;
    private final SignatureStepRepository stepRepository;
    private final SignatureImageStorage signatureImageStorage;
    private final SignerNameResolver signerNameResolver;
    private final OfficeFieldResolver officeFieldResolver;
    private final UserDigitalCertificateService digitalCertificateService;
    private final UserSignatureService userSignatureService;
    private final TeachingEvaluationPartResolver teachingEvaluationPartResolver;
    private final UploadPaths uploadPaths;

    /** One archive write per envelope at a time — two viewers must not interleave files. */
    private final ConcurrentHashMap<Long, Object> archiveLocks = new ConcurrentHashMap<>();

    public SignedDocumentRenderer(DocumentGenerationService documentGenerationService,
            SignatureStepRepository stepRepository,
            SignatureImageStorage signatureImageStorage,
            SignerNameResolver signerNameResolver,
            OfficeFieldResolver officeFieldResolver,
            UserDigitalCertificateService digitalCertificateService,
            UserSignatureService userSignatureService,
            TeachingEvaluationPartResolver teachingEvaluationPartResolver,
            UploadPaths uploadPaths) {
        this.uploadPaths = uploadPaths;
        this.documentGenerationService = documentGenerationService;
        this.stepRepository = stepRepository;
        this.signatureImageStorage = signatureImageStorage;
        this.signerNameResolver = signerNameResolver;
        this.officeFieldResolver = officeFieldResolver;
        this.digitalCertificateService = digitalCertificateService;
        this.userSignatureService = userSignatureService;
        this.teachingEvaluationPartResolver = teachingEvaluationPartResolver;
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
        // เลขที่หนังสือออกหลังเอกสารลงนามครบ จึงไม่มีทางอยู่ใน frozenJson — เติมทับ
        // ตอน render เหมือนกัน โดยไม่แตะสำเนาที่แช่แข็งไว้และแฮชของมัน
        json = officeFieldResolver.fillInto(envelope, json);
        // ส่วนที่ ๓ ของแบบ ก.พ.ว. มข. ๐๓ มาจากผลประเมินการสอนใน Phase 1 — เส้นทางเดียวกัน ไม่แตะแฮช
        json = teachingEvaluationPartResolver.fillInto(envelope, json);

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
     * that may have moved on since — a deploy that changes a template must not
     * change a document that is already signed.
     *
     * <p>Next to the copies goes a key of what they were built from: the late
     * office fields (document number, dates the office fills in after signing)
     * and the signed steps. {@link #renderForDownload} rebuilds the copy only when
     * that key no longer matches — the office filled in a number, or the round
     * was reopened and signed again — never merely because the code changed.
     *
     * @return the paths written, or null if the document could not be produced
     */
    public StoredCopies storeFinalCopies(SignatureRequest envelope) {
        synchronized (archiveLocks.computeIfAbsent(envelope.getId(), id -> new Object())) {
            try {
                String key = archiveKey(envelope);
                byte[] docx = renderDocx(envelope);
                Path dir = archiveDir(envelope);
                Files.createDirectories(dir);
                String base = archiveBaseName(envelope);

                Path docxPath = dir.resolve(base + ".docx");
                writeAtomically(docxPath, docx);

                Path pdfTarget = dir.resolve(base + ".pdf");
                String pdfPath = null;
                if (documentGenerationService.isPdfConversionAvailable()) {
                    byte[] pdf = documentGenerationService.convertDocxToPdfCached(docx);
                    if (pdf != null && pdf.length > 0) {
                        try {
                            pdf = digitalCertificateService.applyDigitalSignaturesToEnvelope(pdf, envelope, null);
                        } catch (Exception e) {
                            log.warn("Could not apply digital certificates to envelope {}: {}", envelope.getId(), e.getMessage());
                        }
                        writeAtomically(pdfTarget, pdf);
                        pdfPath = uploadPaths.toStored(pdfTarget);
                    }
                }
                if (pdfPath == null) {
                    // A PDF from an earlier build of this copy would no longer match the DOCX.
                    Files.deleteIfExists(pdfTarget);
                }
                // The key goes last: a copy without a matching key is rebuilt, so a
                // crash half-way through can only cause an extra render, never a stale file.
                writeAtomically(dir.resolve(base + ".key"), key.getBytes(StandardCharsets.UTF_8));
                return new StoredCopies(uploadPaths.toStored(docxPath), pdfPath);
            } catch (IOException e) {
                // The signatures themselves are already safe in the database; failing
                // to archive a copy must not undo them.
                log.error("Could not store the signed copy of envelope {}: {}", envelope.getId(), e.toString());
                return null;
            }
        }
    }

    /**
     * The archived copy of a completed envelope, rebuilt first if what it was
     * built from has changed since.
     *
     * @return the copy, or null when it could not be produced — callers then
     *         render live as before
     */
    private FinishedCopy finishedCopy(SignatureRequest envelope) {
        try {
            Path dir = archiveDir(envelope);
            String base = archiveBaseName(envelope);
            Path docx = dir.resolve(base + ".docx");
            Path keyFile = dir.resolve(base + ".key");

            String key = archiveKey(envelope);
            boolean current = Files.isRegularFile(docx) && Files.isRegularFile(keyFile)
                    && key.equals(Files.readString(keyFile, StandardCharsets.UTF_8));
            if (!current && storeFinalCopies(envelope) == null) {
                return null;
            }
            Path pdf = dir.resolve(base + ".pdf");
            return new FinishedCopy(Files.readAllBytes(docx),
                    Files.isRegularFile(pdf) ? Files.readAllBytes(pdf) : null);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not use the archived copy of envelope {}; rendering live: {}",
                    envelope.getId(), e.toString());
            return null;
        }
    }

    private record FinishedCopy(byte[] docx, byte[] pdf) {
    }

    /**
     * What an archived copy depends on besides the frozen form: the office's late
     * fields and which signatures are on it. Deliberately not the template or code
     * version — those must not change a signed document.
     */
    private String archiveKey(SignatureRequest envelope) {
        String json = envelope.getFrozenJson();
        if (json == null || json.isBlank()) {
            json = "{}";
        }
        StringBuilder material = new StringBuilder(officeFieldResolver.fillInto(envelope, json));
        for (SignatureStep step : stepRepository.findSignedSteps(envelope.getId())) {
            material.append('|').append(step.getId())
                    .append(':').append(step.getImagePathSnapshot())
                    .append(':').append(step.getSignedAt());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Path archiveDir(SignatureRequest envelope) {
        return uploadPaths.dir("academic", "signed", String.valueOf(envelope.getRequestId()));
    }

    private static String archiveBaseName(SignatureRequest envelope) {
        return (envelope.getModule() == SignatureModule.ACADEMIC ? "doc_" : "p2doc_")
                + envelope.getDocumentType() + "_signed_" + envelope.getVerificationCode();
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, content);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Where the finished copies were written. */
    public record StoredCopies(String docxPath, String pdfPath) {
    }

    /**
     * เอกสารของซองสำหรับเปิดดูหรือดาวน์โหลด — PDF ถ้าขอ PDF และแปลงได้
     *
     * <p>แปลง PDF ไม่ได้ (ไม่มี LibreOffice) ต้องได้ DOCX ของซองนี้กลับไป ไม่ใช่ null เดิมผู้เรียก
     * ได้ null แล้วตกไปใช้ไฟล์ที่สร้างไว้ก่อนลงนาม ผู้ยื่นจึงเห็นเอกสารที่ไม่มีลายเซ็นและไม่มีค่าที่
     * แอดมินกรอกทีหลัง ทั้งที่ซองมีครบ {@link com.ecom.academic.controller.PreviewResponseFactory}
     * จัดการเรื่องรูปแบบไฟล์ต่อเอง
     */
    public byte[] renderForDownload(SignatureRequest envelope, String format) throws IOException {
        // ลงนามครบแล้ว — ส่งสำเนาที่เก็บไว้ ไม่สร้างใหม่จากเทมเพลตของรุ่นที่ deploy อยู่
        // สำเนาถูกสร้างใหม่เองเมื่อสำนักงานกรอกเลขที่หนังสือหรือช่องของตัวเองทีหลัง (ดู storeFinalCopies)
        if (envelope.getStatus() == SignatureRequestStatus.COMPLETED) {
            FinishedCopy copy = finishedCopy(envelope);
            if (copy != null) {
                return "pdf".equalsIgnoreCase(format) && copy.pdf() != null ? copy.pdf() : copy.docx();
            }
        }
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = renderPdf(envelope);
            if (pdf != null) {
                return pdf;
            }
        }
        return renderDocx(envelope);
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
            StampedSignature signature = loadSignature(step, step.getAnchorPlaceholder());
            if (signature != null) {
                stamped.add(signature);
            }
        }

        // ส่วนที่ ๓ ของแบบ ก.พ.ว. มข. ๐๓ คือแบบประเมินผลการสอนที่ประธานคณะอนุกรรมการเซ็นไว้แล้ว
        // ในเอกสารที่ 8 ของ Phase 1 — ลายเซ็นเดียวกันบนข้อความเดียวกัน จึงวางรูปเดิมซ้ำ
        // ไม่ได้ขอให้ประธานเซ็นใหม่
        teachingEvaluationPartResolver.chairSignatureFor(envelope)
                .map(step -> loadSignature(step, TeachingEvaluationPartResolver.CHAIR_ANCHOR))
                .ifPresent(stamped::add);

        // Place preview signature onto active step's anchor if provided and not already signed
        if (previewStep != null && previewSig != null && previewStep.getAnchorPlaceholder() != null) {
            boolean alreadyStamped = stamped.stream()
                    .anyMatch(s -> s.anchorPlaceholder().equals(previewStep.getAnchorPlaceholder()));
            if (!alreadyStamped) {
                byte[] png = null;
                if (previewSig.getKind() == com.ecom.academic.model.SignatureKind.TYPE
                        && previewSig.getTypedText() != null && !previewSig.getTypedText().isBlank()) {
                    String signerEmail = previewStep.getSigner() != null ? previewStep.getSigner().getEmail() : null;
                    if (signerEmail == null && previewSig.getUser() != null) {
                        signerEmail = previewSig.getUser().getEmail();
                    }
                    String signerPosition = previewStep.getSignerPositionSnapshot();
                    if ((signerPosition == null || signerPosition.isBlank()) && previewSig.getUser() != null) {
                        signerPosition = previewSig.getUser().getAcademicPosition();
                    }
                    png = userSignatureService.generateDigitalStampPng(previewSig.getTypedText(), signerPosition, signerEmail, java.time.LocalDateTime.now());
                } else if (previewSig.getImagePath() != null && !previewSig.getImagePath().isBlank()) {
                    png = signatureImageStorage.read(previewSig.getImagePath());
                }

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

    /**
     * The stamped image of one signed step, placed at the given anchor.
     *
     * @return null when the image file is missing — better a document missing one
     *         signature, clearly, than a failed render that hides the rest
     */
    private StampedSignature loadSignature(SignatureStep step, String anchorPlaceholder) {
        String path = step.getImagePathSnapshot();
        if (path == null || path.isBlank()) {
            return null;
        }
        byte[] png = signatureImageStorage.read(path);
        if (png == null) {
            log.warn("Signature image {} for step {} is missing; rendering without it",
                    path, step.getId());
            return null;
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
        return new StampedSignature(anchorPlaceholder, png, width, height);
    }
}
