package com.ecom.academic.service.pdf;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.PdfMode;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignedPdfRevision;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.DocumentGenerationService.StampedSignature;
import com.ecom.academic.service.DocumentWorkflowConfigService;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.model.UserDtls;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Envelopes whose PDF is signed by each signer in turn (PAdES, incremental).
 *
 * <p>Revision 0 is built when the envelope is created; each signer's signature,
 * each set of office values, and the office's closing signature are appended as
 * revisions. Nothing is re-rendered and no PIN is kept: whoever signs opens their
 * own .p12 for that one signature.
 *
 * <p>Off unless {@code app.esign.pdf-mode=incremental}, and then only for the
 * documents in {@code app.esign.incremental-docs} ({@code MODULE:type,...}) — each
 * template's reserved areas have to be checked before it joins. An envelope of such
 * a document that cannot be prepared is refused ({@link CannotPrepareException}),
 * never sent on the old flow: that file would carry no one's certificate.
 */
@Service
public class IncrementalSigningService {

    private static final Logger log = LoggerFactory.getLogger(IncrementalSigningService.class);
    private static final String LOCATION = "มหาวิทยาลัยขอนแก่น";
    private static final TimeZone BANGKOK = TimeZone.getTimeZone("Asia/Bangkok");

    private final SignedDocumentRenderer renderer;
    private final SignedPdfRevisionService revisions;
    private final DocumentWorkflowConfigService workflowConfig;
    /** Lazily: the office-value resolver sits on top of the request services, which sit on top of us. */
    private final org.springframework.beans.factory.ObjectProvider<com.ecom.academic.service.OfficeFieldResolver> officeFields;
    private final com.ecom.academic.repository.SignatureRequestRepository envelopes;
    private final boolean enabled;
    private final Set<String> documents;
    private final PdfIncrementService pdf = new PdfIncrementService(ThaiText.sarabun());
    private final BasePdfBuilder builder = new BasePdfBuilder();
    private final ObjectMapper json = new ObjectMapper();

    public IncrementalSigningService(SignedDocumentRenderer renderer, SignedPdfRevisionService revisions,
            DocumentWorkflowConfigService workflowConfig,
            org.springframework.beans.factory.ObjectProvider<com.ecom.academic.service.OfficeFieldResolver> officeFields,
            com.ecom.academic.repository.SignatureRequestRepository envelopes,
            @Value("${app.esign.pdf-mode:incremental}") String mode,
            @Value("${app.esign.incremental-docs:ACADEMIC:1,ACADEMIC:2,ACADEMIC:3,ACADEMIC:4,ACADEMIC:5,ACADEMIC:7,ACADEMIC:8,ACADEMIC:9,POSITION:1,POSITION:2,POSITION:3,POSITION:4,POSITION:6,POSITION:7,POSITION:8,POSITION:9}") String documents) {
        this.renderer = renderer;
        this.revisions = revisions;
        this.workflowConfig = workflowConfig;
        this.officeFields = officeFields;
        this.envelopes = envelopes;
        this.enabled = "incremental".equalsIgnoreCase(mode.trim());
        this.documents = new LinkedHashSet<>();
        for (String d : documents.split(",")) {
            if (!d.isBlank()) {
                this.documents.add(d.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    public boolean isEnabledFor(SignatureModule module, int documentType) {
        return enabled && documents.contains(module.name() + ":" + documentType);
    }

    // ------------------------------------------------------------------ revision 0

    /** Attempts at the base PDF: LibreOffice now and then fails one conversion and succeeds the next. */
    static final int PREPARE_ATTEMPTS = 3;

    /**
     * An incrementally signed document whose PDF could not be prepared. The envelope
     * must not go out: on the old flow nobody's certificate would be in the file.
     */
    public static final class CannotPrepareException extends RuntimeException {
        public CannotPrepareException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Prepares a new envelope's PDF. Leaves the envelope LEGACY (and returns false)
     * when this document is not switched on.
     *
     * @throws CannotPrepareException when it is switched on but the PDF could not be
     *         prepared, even after retrying a failed conversion
     */
    @Transactional
    public boolean start(SignatureRequest envelope) {
        if (envelope.isIncremental()) {
            return true;
        }
        if (!isEnabledFor(envelope.getModule(), envelope.getDocumentType())) {
            return false;
        }
        Exception last = null;
        for (int attempt = 1; attempt <= PREPARE_ATTEMPTS; attempt++) {
            try {
                prepare(envelope);
                return true;
            } catch (IOException e) {
                // The conversion itself (LibreOffice) — worth another go
                last = e;
                log.warn("Envelope {}: preparing the signed PDF failed (attempt {}/{}): {}",
                        envelope.getId(), attempt, PREPARE_ATTEMPTS, e.toString());
            } catch (BasePdfBuilder.BaseBuildException | RuntimeException e) {
                // The template itself does not fit — the same every time
                last = e;
                break;
            }
        }
        envelope.setPdfMode(PdfMode.LEGACY);
        envelope.setFieldLayoutJson(null);
        log.error("Envelope {}: could not prepare the incrementally signed PDF; refusing to send it",
                envelope.getId(), last);
        throw new CannotPrepareException("Envelope " + envelope.getId() + ": signed PDF could not be prepared", last);
    }

    private void prepare(SignatureRequest envelope) throws IOException, BasePdfBuilder.BaseBuildException {
        List<SignatureSlot> slots = workflowConfig.effectiveSlotsFor(envelope.getModule(), envelope.getDocumentType());

        List<BasePdfBuilder.TextSpec> texts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String f : DocumentFieldOwnership.lateFields(envelope.getModule(), envelope.getDocumentType())) {
            if (seen.add(f)) {
                texts.add(new BasePdfBuilder.TextSpec(f, reserveFor(f), isTickField(f), linesFor(f)));
            }
        }
        List<BasePdfBuilder.SlotSpec> slotSpecs = new ArrayList<>();
        for (SignatureSlot s : slots) {
            List<String> own = ownFields(s);
            for (String f : own) {
                if (seen.add(f)) {
                    texts.add(new BasePdfBuilder.TextSpec(f, reserveFor(f), tickFields(s).contains(f)));
                }
            }
            slotSpecs.add(new BasePdfBuilder.SlotSpec(s.slotKey(), s.anchorPlaceholder(), own));
        }

        BasePdfBuilder.Result base = builder.build(new BasePdfBuilder.Renderer() {
            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                return docx(overrides, pictures, DocumentGenerationService.SIGNATURE_HEIGHTS_EMU.get(0));
            }

            @Override
            public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures,
                    long signatureHeightEmu) throws IOException {
                return renderer.renderBaseDocx(envelope, overrides, pictures.stream()
                        .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height()))
                        .toList(), signatureHeightEmu);
            }

            @Override
            public byte[] toPdf(byte[] docx) throws IOException {
                return renderer.toPdf(docx);
            }
        }, texts, slotSpecs);
        envelope.setFieldLayoutJson(json.writeValueAsString(base.layout()));
        envelope.setPdfMode(PdfMode.INCREMENTAL);
        revisions.append(envelope, null, base.pdf(), SignedPdfRevision.Kind.BASE, null, null, null);
    }

    /** The template fields a slot's signer fills while signing. */
    static List<String> ownFields(SignatureSlot s) {
        List<String> own = new ArrayList<>();
        if (s.marks() != null) {
            if (s.marks().signedDateFieldKey() != null) {
                own.add(s.marks().signedDateFieldKey());
            }
            if (s.marks().commentFieldKey() != null) {
                own.add(s.marks().commentFieldKey());
            }
        }
        if (s.marks() != null && s.marks().commentTickFieldKey() != null) {
            own.add(s.marks().commentTickFieldKey());
        }
        if (s.choice() != null) {
            own.add(s.choice().fieldKey());
        }
        return own;
    }

    /** Of a slot's own fields, those printed as a ✓ in brackets. */
    static Set<String> tickFields(SignatureSlot s) {
        Set<String> ticks = new LinkedHashSet<>();
        if (s.choice() != null && s.choice().renderAsTick()) {
            ticks.add(s.choice().fieldKey());
        }
        if (s.marks() != null && s.marks().commentTickFieldKey() != null) {
            ticks.add(s.marks().commentTickFieldKey());
        }
        return ticks;
    }

    /**
     * Lines reserved for a late field. The remarks of document 2 (text_1..5) sit in
     * narrow table cells and wrap there, as they do in the Word version.
     *
     * <p>Only as many lines as the row already has: a reserved line is a real line
     * break in the layout, so reserving more makes the row taller than in the
     * unsigned document and the page changes shape the moment someone signs.
     * Rows 3 and 4 are two lines tall because their item text wraps; the rest are one.
     */
    static int linesFor(String field) {
        return switch (field) {
            case "text_3", "text_4" -> 2;
            default -> 1;
        };
    }

    /** Office/admin fields that hold a ✓ rather than text (the chk_* boxes). */
    static boolean isTickField(String field) {
        return field.startsWith("chk");
    }

    /** Reserved length in non-breaking spaces (≈3.46 pt each at 16 pt). */
    static int reserveFor(String field) {
        if (field.contains("memo_no")) {
            return 32;
        }
        if (field.contains("date")) {
            return 25;
        }
        if (field.contains("comment")) {
            return 60;
        }
        // Names and positions (with academic titles) run long; BasePdfBuilder shrinks
        // a reserve that does not fit its line.
        if (field.contains("name") || field.contains("position")) {
            return 45;
        }
        return 30;
    }

    // ------------------------------------------------------------------ revisions

    /**
     * The signer's revision: their fields and their signature, in their own name.
     * Call after the step is marked signed (its date is printed).
     *
     * @return the revision number
     */
    @Transactional
    public int sign(SignatureRequest envelope, SignatureStep step, CmsSigner signer, byte[] imagePng,
            String printedName) throws IOException {
        return sign(envelope, step, signer, imagePng, printedName, null);
    }

    /**
     * @param reason เหตุผลในลายมือชื่อดิจิทัล — null ใช้ "ลงนามในตำแหน่ง ..." ตามปกติ
     *               ทางสำรองของผู้ลงนามภายนอก (กุญแจของระบบ) ระบุชื่อและวิธียืนยันตัวตนไว้ที่นี่ ({@link SystemSealService})
     */
    @Transactional
    public int sign(SignatureRequest envelope, SignatureStep step, CmsSigner signer, byte[] imagePng,
            String printedName, String reason) throws IOException {
        // What the office filled in between signers is part of what this one signs —
        // but not the memo number and date, which are issued at the very end.
        syncLateValues(envelope, null, false);
        byte[] current = latest(envelope);
        SignatureSlot slot = workflowConfig.effectiveSlotsFor(envelope.getModule(), envelope.getDocumentType()).stream()
                .filter(s -> s.slotKey().equals(step.getSlotKey())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No slot " + step.getSlotKey() + " in this document"));
        Set<String> fields = fieldNames(current);
        String sigField = "sig_" + slot.slotKey();
        if (!fields.contains(sigField)) {
            throw new IllegalStateException("The signed PDF has no place for slot " + slot.slotKey());
        }

        Map<String, String> filled = values(current);
        Map<String, String> own = ownValues(slot, step, step.getSignedAt());
        thaiDateDigits(own, slot, envelope);
        own.keySet().retainAll(fields);
        putNameIfBlank(own, slot, step, fields, filled);

        // Frozen by this signature: its own fields, and whatever the office has already
        // filled in between signers (those are the values this signer is agreeing to).
        List<String> locked = new ArrayList<>();
        locked.add(sigField);
        locked.addAll(own.keySet());
        Set<String> office = DocumentFieldOwnership.officeFields(envelope.getModule(), envelope.getDocumentType());
        for (String f : DocumentFieldOwnership.adminFields(envelope.getModule(), envelope.getDocumentType())) {
            // Office fields stay open for the issuing step; locking one here would make
            // issuing the number break this very signature.
            if (!office.contains(f) && filled.containsKey(f) && !filled.get(f).isBlank() && !locked.contains(f)) {
                locked.add(f);
            }
        }
        boolean certify = revisions.chain(envelope.getId()).stream()
                .noneMatch(r -> r.getKind() == SignedPdfRevision.Kind.SIGN);

        String why = reason != null ? reason
                : "ลงนามในตำแหน่ง \"" + (step.getRoleLabel() != null ? step.getRoleLabel() : slot.roleLabel()) + "\"";
        byte[] next = pdf.sign(current, new PdfIncrementService.SignSpec(signer, sigField, own, locked, certify,
                imagePng, printedName, why, LOCATION, calendar(step.getSignedAt()), nameField(slot)));
        int no = revisions.append(envelope, current, next, SignedPdfRevision.Kind.SIGN, step.getId(),
                step.getSigner() != null ? step.getSigner().getId() : null, signer.fingerprint());
        step.setPdfRevisionNo(no);
        // หนังสือที่พิมพ์หลายฉบับในไฟล์เดียว: ฉบับที่ตัดแยกต้องมีใบรับรองของผู้ลงนามด้วย และกุญแจมีอยู่ตอนนี้เท่านั้น
        renderer.storeSignedLetters(envelope, next, no, sigField, signer, printedName, why, LOCATION,
                calendar(step.getSignedAt()));
        return no;
    }

    /** What the signer of {@code slot} writes into the document by signing. */
    static Map<String, String> ownValues(SignatureSlot slot, SignatureStep step, LocalDateTime signedAt) {
        return ownValues(slot, signedAt, step.getSignerChoiceValue(), step.getSignerComment());
    }

    /**
     * The same, from an answer and a comment not yet saved on the step — the signing
     * page's preview while the signer is still choosing.
     */
    static Map<String, String> ownValues(SignatureSlot slot, LocalDateTime signedAt, String choice, String comment) {
        Map<String, String> own = new LinkedHashMap<>();
        boolean hasComment = comment != null && !comment.isBlank();
        if (slot.marks() != null && slot.marks().signedDateFieldKey() != null && signedAt != null) {
            own.put(slot.marks().signedDateFieldKey(), AcademicRequestService.formatThaiDate(signedAt));
        }
        if (slot.marks() != null && slot.marks().commentFieldKey() != null && hasComment) {
            own.put(slot.marks().commentFieldKey(), comment);
        }
        if (slot.choice() != null && choice != null) {
            own.put(slot.choice().fieldKey(), slot.choice().renderedValue(choice));
        }
        if (slot.marks() != null && slot.marks().commentTickFieldKey() != null && hasComment) {
            own.put(slot.marks().commentTickFieldKey(), com.ecom.academic.service.SignatureAnchorRegistry.TICK);
        }
        return own;
    }

    /**
     * The signing date in Thai numerals when the document prints them (doc_8: "๒ ตุลาคม ๒๕๖๙").
     * The Word render converts on its own; values drawn into the PDF here do not pass through it.
     */
    private void thaiDateDigits(Map<String, String> own, SignatureSlot slot, SignatureRequest envelope) {
        String key = slot.marks() == null ? null : slot.marks().signedDateFieldKey();
        if (key != null && own.containsKey(key) && renderer.usesThaiNumerals(envelope)) {
            own.put(key, com.ecom.util.ThaiDateUtil.toThaiDigits(own.get(key)));
        }
    }

    /**
     * The signer's name under their signature line, when the base reserved it and
     * nobody has filled it in — document 2's HR slot is signed by whichever officer
     * reviewed it, so the name cannot be known when the base is built.
     */
    static void putNameIfBlank(Map<String, String> own, SignatureSlot slot, SignatureStep step,
            Set<String> fields, Map<String, String> filled) {
        String anchor = slot.anchorPlaceholder();
        String name = com.ecom.academic.service.SignerNameResolver.printedNameOf(step);
        if (anchor != null && fields.contains(anchor) && filled.getOrDefault(anchor, "").isBlank()
                && name != null && !name.isBlank()) {
            own.put(anchor, name);
        }
    }

    /** The name under a slot's signature, drawn centred in its "( ... )". */
    private static Set<String> nameField(SignatureSlot slot) {
        return slot.anchorPlaceholder() == null ? Set.of() : Set.of(slot.anchorPlaceholder());
    }

    /**
     * The document as the signer of an active step would leave it: the current file
     * with their picture and today's date drawn in. Not signed, not stored.
     */
    public byte[] preview(SignatureRequest envelope, SignatureStep step, byte[] imagePng) throws IOException {
        return preview(envelope, step, imagePng, step != null ? step.getSignerChoiceValue() : null,
                step != null ? step.getSignerComment() : null);
    }

    /**
     * The same, with the answer and comment the signer has chosen on the page but not
     * yet signed — drawn by the rule {@link #sign} uses, so the preview is what they
     * will get. Nothing is stored.
     *
     * @throws PdfIncrementService.DoesNotFitException when the comment does not fit its box
     */
    public byte[] preview(SignatureRequest envelope, SignatureStep step, byte[] imagePng, String choice,
            String comment) throws IOException {
        // ช่องที่สำนักงานกรอกไว้แล้วจะถูกเขียนลงไฟล์ตอนลงนาม (sign → syncLateValues) — แสดงให้เห็นตั้งแต่ตอนนี้
        byte[] current = latestForViewing(envelope);
        if (step == null || step.getStatus() != com.ecom.academic.model.SignatureStepStatus.ACTIVE) {
            return current;
        }
        SignatureSlot slot = workflowConfig.effectiveSlotsFor(envelope.getModule(), envelope.getDocumentType()).stream()
                .filter(s -> s.slotKey().equals(step.getSlotKey())).findFirst().orElse(null);
        if (slot == null) {
            return current;
        }
        Set<String> fields = fieldNames(current);
        Map<String, String> own = ownValues(slot, LocalDateTime.now(ZoneId.of("Asia/Bangkok")), choice, comment);
        thaiDateDigits(own, slot, envelope);
        own.keySet().retainAll(fields);
        putNameIfBlank(own, slot, step, fields, values(current));
        return pdf.preview(current, "sig_" + slot.slotKey(), own, nameField(slot), imagePng);
    }

    /**
     * Office values (memo number, dates, admin fields) as an unsigned revision.
     * Values for fields this document does not print, or unchanged ones, are skipped.
     *
     * @return the revision number, or -1 when nothing changed
     */
    @Transactional
    public int fill(SignatureRequest envelope, Map<String, String> values, UserDtls actor) throws IOException {
        if (envelope.getPdfLockedAt() != null) {
            throw new IllegalStateException("เอกสารนี้ปิดแล้ว แก้ไขไม่ได้");
        }
        byte[] current = latest(envelope);
        Map<String, String> changes = changesAgainst(current, values);
        if (changes.isEmpty()) {
            return -1;
        }
        byte[] next = pdf.fill(current, changes);
        return revisions.append(envelope, current, next, SignedPdfRevision.Kind.FILL, null,
                actor != null ? actor.getId() : null, null);
    }

    /**
     * Brings the PDF's late fields up to what the office has saved for the document
     * (an unsigned revision, only if something changed). Empty values are skipped:
     * blank means "not issued yet", not "erase".
     *
     * @return the revision number, or -1 when nothing changed
     */
    @Transactional
    public int syncLateValues(SignatureRequest envelope, UserDtls actor) throws IOException {
        return syncLateValues(envelope, actor, true);
    }

    /**
     * @param includeOffice also the office's own fields (memo number, date) — only when issuing
     */
    @Transactional
    public int syncLateValues(SignatureRequest envelope, UserDtls actor, boolean includeOffice) throws IOException {
        Map<String, String> values = savedLateValues(envelope, includeOffice);
        return values.isEmpty() ? -1 : fill(envelope, values, actor);
    }

    /**
     * The current file as the next signature will find it: what the office has saved
     * since the last revision (ticks and remarks of document 2, for instance) drawn in,
     * the way {@link #sign} writes them before signing. Shown only, never stored —
     * otherwise an officer's tick does not appear on any preview until they sign.
     * The memo number and date stay out, as they do when signing.
     */
    public byte[] latestForViewing(SignatureRequest envelope) throws IOException {
        byte[] current = latest(envelope);
        if (envelope.getPdfLockedAt() != null) {
            return current;
        }
        try {
            Map<String, String> changes = changesAgainst(current, savedLateValues(envelope, false));
            return changes.isEmpty() ? current : pdf.fill(current, changes);
        } catch (IOException | RuntimeException e) {
            // A value that does not fit (or a field a signer has locked) is reported where it
            // is saved or signed — the preview falls back to the file as it is.
            log.debug("Preview without pending values for envelope {}: {}", envelope.getId(), e.toString());
            return current;
        }
    }

    /** The late values the office has saved for this envelope's document; blanks skipped. */
    private Map<String, String> savedLateValues(SignatureRequest envelope, boolean includeOffice) throws IOException {
        Set<String> office = DocumentFieldOwnership.officeFields(envelope.getModule(), envelope.getDocumentType());
        String filled = officeFields.getObject().fillInto(envelope, "{}");
        // ค่าที่กรอกทีหลังวาดลง PDF ตรง ๆ ไม่ผ่านการแปลงเลขของเครื่องสร้าง Word — แปลงเองตามเอกสาร
        boolean thai = renderer.usesThaiNumerals(envelope);
        Map<String, String> values = new LinkedHashMap<>();
        try {
            json.readTree(filled).properties().forEach(e -> {
                String v = e.getValue().asText("");
                if (!v.isBlank() && (includeOffice || !office.contains(e.getKey()))) {
                    values.put(e.getKey(), thai ? com.ecom.util.ThaiDateUtil.toThaiDigits(v) : v);
                }
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException(e);
        }
        return values;
    }

    /** The values that differ from what {@code current} already shows, for fields it prints. */
    private static Map<String, String> changesAgainst(byte[] current, Map<String, String> values) throws IOException {
        Set<String> fields = fieldNames(current);
        Map<String, String> filled = values(current);
        Map<String, String> changes = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            String value = v == null ? "" : v.trim();
            if (fields.contains(k) && !value.equals(filled.getOrDefault(k, ""))) {
                changes.put(k, value);
            }
        });
        return changes;
    }

    /**
     * The staff's values that the next signature would write into this envelope's PDF
     * but do not fit their boxes — the same values {@link #syncLateValues(SignatureRequest, UserDtls, boolean)}
     * writes before a signer signs. Checked before the document goes on, so the staff
     * member who typed them shortens them, not the next signer who cannot.
     */
    @Transactional(readOnly = true)
    public List<LateFieldFit.Problem> staffValuesThatDoNotFit(SignatureRequest envelope) {
        if (!envelope.isIncremental() || envelope.getPdfLockedAt() != null) {
            return List.of();
        }
        Set<String> office = DocumentFieldOwnership.officeFields(envelope.getModule(), envelope.getDocumentType());
        boolean thai = renderer.usesThaiNumerals(envelope);
        Map<String, String> values = new LinkedHashMap<>();
        try {
            json.readTree(officeFields.getObject().fillInto(envelope, "{}")).properties().forEach(e -> {
                if (!office.contains(e.getKey())) {
                    String v = e.getValue().asText("");
                    values.put(e.getKey(), thai ? com.ecom.util.ThaiDateUtil.toThaiDigits(v) : v);
                }
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
        return LateFieldFit.check(envelope.getFieldLayoutJson(), values);
    }

    /** The incrementally signed envelope currently holding this document, if any. */
    @Transactional(readOnly = true)
    public java.util.Optional<SignatureRequest> envelopeFor(SignatureModule module, Long requestId, int documentType) {
        return envelopes.findBlockingEnvelopes(module, requestId, documentType).stream()
                .filter(SignatureRequest::isIncremental)
                .findFirst();
    }

    /** The office's closing signature: after it, any change breaks every signature. */
    @Transactional
    public int lock(SignatureRequest envelope, CmsSigner signer, String printedName, UserDtls actor) throws IOException {
        if (envelope.getPdfLockedAt() != null) {
            throw new IllegalStateException("เอกสารนี้ปิดแล้ว");
        }
        byte[] current = latest(envelope);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Bangkok"));
        byte[] next = pdf.sign(current, new PdfIncrementService.SignSpec(signer, BasePdfBuilder.LOCK_FIELD, Map.of(),
                null, false, null, printedName, "ออกเลขที่หนังสือและปิดเอกสาร", LOCATION, calendar(now)));
        int no = revisions.append(envelope, current, next, SignedPdfRevision.Kind.LOCK, null,
                actor != null ? actor.getId() : null, signer.fingerprint());
        envelope.setPdfLockedAt(now);
        return no;
    }

    /** The current signed PDF of an INCREMENTAL envelope. */
    public byte[] latest(SignatureRequest envelope) {
        if (!envelope.isIncremental()) {
            throw new IllegalStateException("Envelope " + envelope.getId() + " is not incrementally signed");
        }
        byte[] current = revisions.latest(envelope.getId());
        if (current == null) {
            throw new IllegalStateException("Envelope " + envelope.getId() + " has no signed PDF");
        }
        return current;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The signing time for the PDF. Gregorian whatever the machine's regional format:
     * PDFBox writes /M straight from this calendar's fields, and on a Thai-format JVM
     * {@code Calendar.getInstance()} is Buddhist — the file would say D:2569…, which
     * every reader shows as the year 2569 CE.
     */
    static Calendar calendar(LocalDateTime at) {
        Calendar cal = new java.util.GregorianCalendar(BANGKOK, Locale.ROOT);
        if (at != null) {
            cal.setTimeInMillis(at.atZone(ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli());
        }
        return cal;
    }

    static Set<String> fieldNames(byte[] file) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            if (form != null) {
                for (PDField f : form.getFieldTree()) {
                    names.add(f.getFullyQualifiedName());
                }
            }
        }
        return names;
    }

    /** Current text values of the document's fields. */
    public static Map<String, String> values(byte[] file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(file))) {
            PDAcroForm form = doc.getDocumentCatalog().getAcroForm(null);
            if (form != null) {
                for (PDField f : form.getFieldTree()) {
                    if (!(f instanceof PDSignatureField)) {
                        out.put(f.getFullyQualifiedName(), PdfIncrementService.valueOf(f));
                    }
                }
            }
        }
        return out;
    }
}
