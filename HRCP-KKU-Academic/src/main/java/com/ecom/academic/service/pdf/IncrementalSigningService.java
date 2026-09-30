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
 * template's reserved areas have to be checked before it joins. An envelope that
 * cannot be prepared this way stays on the old flow.
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

    /**
     * Prepares a new envelope's PDF. Leaves the envelope LEGACY (and returns false)
     * when this document is not switched on or cannot be prepared.
     */
    @Transactional
    public boolean start(SignatureRequest envelope) {
        if (envelope.isIncremental()) {
            return true;
        }
        if (!isEnabledFor(envelope.getModule(), envelope.getDocumentType())) {
            return false;
        }
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

        try {
            BasePdfBuilder.Result base = builder.build(new BasePdfBuilder.Renderer() {
                @Override
                public byte[] docx(Map<String, String> overrides, List<BasePdfBuilder.SlotPicture> pictures) throws IOException {
                    return renderer.renderBaseDocx(envelope, overrides, pictures.stream()
                            .map(p -> new StampedSignature(p.anchorPlaceholder(), p.png(), p.width(), p.height()))
                            .toList());
                }

                @Override
                public byte[] toPdf(byte[] docx) throws IOException {
                    return renderer.toPdf(docx);
                }
            }, texts, slotSpecs);
            envelope.setFieldLayoutJson(json.writeValueAsString(base.layout()));
            envelope.setPdfMode(PdfMode.INCREMENTAL);
            revisions.append(envelope, null, base.pdf(), SignedPdfRevision.Kind.BASE, null, null, null);
            return true;
        } catch (BasePdfBuilder.BaseBuildException | IOException | RuntimeException e) {
            log.warn("Envelope {}: could not prepare the incrementally signed PDF ({}); using the old flow",
                    envelope.getId(), e.toString());
            envelope.setPdfMode(PdfMode.LEGACY);
            envelope.setFieldLayoutJson(null);
            return false;
        }
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

        Map<String, String> own = ownValues(slot, step, step.getSignedAt());
        own.keySet().retainAll(fields);

        // Frozen by this signature: its own fields, and whatever the office has already
        // filled in between signers (those are the values this signer is agreeing to).
        List<String> locked = new ArrayList<>();
        locked.add(sigField);
        locked.addAll(own.keySet());
        Map<String, String> filled = values(current);
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

        byte[] next = pdf.sign(current, new PdfIncrementService.SignSpec(signer, sigField, own, locked, certify,
                imagePng, printedName, "ลงนามในตำแหน่ง \"" + (step.getRoleLabel() != null ? step.getRoleLabel() : slot.roleLabel()) + "\"",
                LOCATION, calendar(step.getSignedAt())));
        int no = revisions.append(envelope, current, next, SignedPdfRevision.Kind.SIGN, step.getId(),
                step.getSigner() != null ? step.getSigner().getId() : null, signer.fingerprint());
        step.setPdfRevisionNo(no);
        return no;
    }

    /** What the signer of {@code slot} writes into the document by signing. */
    static Map<String, String> ownValues(SignatureSlot slot, SignatureStep step, LocalDateTime signedAt) {
        Map<String, String> own = new LinkedHashMap<>();
        if (slot.marks() != null && slot.marks().signedDateFieldKey() != null && signedAt != null) {
            own.put(slot.marks().signedDateFieldKey(), AcademicRequestService.formatThaiDate(signedAt));
        }
        if (slot.marks() != null && slot.marks().commentFieldKey() != null && step.getSignerComment() != null
                && !step.getSignerComment().isBlank()) {
            own.put(slot.marks().commentFieldKey(), step.getSignerComment());
        }
        if (slot.choice() != null && step.getSignerChoiceValue() != null) {
            own.put(slot.choice().fieldKey(), slot.choice().renderedValue(step.getSignerChoiceValue()));
        }
        if (slot.marks() != null && slot.marks().commentTickFieldKey() != null && step.getSignerComment() != null
                && !step.getSignerComment().isBlank()) {
            own.put(slot.marks().commentTickFieldKey(), com.ecom.academic.service.SignatureAnchorRegistry.TICK);
        }
        return own;
    }

    /**
     * The document as the signer of an active step would leave it: the current file
     * with their picture and today's date drawn in. Not signed, not stored.
     */
    public byte[] preview(SignatureRequest envelope, SignatureStep step, byte[] imagePng) throws IOException {
        byte[] current = latest(envelope);
        if (step == null || step.getStatus() != com.ecom.academic.model.SignatureStepStatus.ACTIVE) {
            return current;
        }
        SignatureSlot slot = workflowConfig.effectiveSlotsFor(envelope.getModule(), envelope.getDocumentType()).stream()
                .filter(s -> s.slotKey().equals(step.getSlotKey())).findFirst().orElse(null);
        if (slot == null) {
            return current;
        }
        Set<String> fields = fieldNames(current);
        Map<String, String> own = ownValues(slot, step, LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
        own.keySet().retainAll(fields);
        return pdf.preview(current, "sig_" + slot.slotKey(), own, imagePng);
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
        Set<String> fields = fieldNames(current);
        Map<String, String> filled = values(current);
        Map<String, String> changes = new LinkedHashMap<>();
        values.forEach((k, v) -> {
            String value = v == null ? "" : v.trim();
            if (fields.contains(k) && !value.equals(filled.getOrDefault(k, ""))) {
                changes.put(k, value);
            }
        });
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
        Set<String> office = DocumentFieldOwnership.officeFields(envelope.getModule(), envelope.getDocumentType());
        String filled = officeFields.getObject().fillInto(envelope, "{}");
        Map<String, String> values = new LinkedHashMap<>();
        try {
            json.readTree(filled).properties().forEach(e -> {
                String v = e.getValue().asText("");
                if (!v.isBlank() && (includeOffice || !office.contains(e.getKey()))) {
                    values.put(e.getKey(), v);
                }
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IOException(e);
        }
        return values.isEmpty() ? -1 : fill(envelope, values, actor);
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

    private static Calendar calendar(LocalDateTime at) {
        Calendar cal = Calendar.getInstance(BANGKOK);
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
