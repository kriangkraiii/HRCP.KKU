package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStepStatus;

/**
 * เอกสารที่เจ้าหน้าที่ส่งกลับให้ผู้ยื่นแก้ อยู่ขั้นไหนแล้ว
 *
 * <p>การส่งกลับไม่เปลี่ยนสถานะคำร้อง ผู้ยื่นจึงรู้ได้จากตรงนี้เท่านั้น เคยใช้แค่ "แก้ได้หรือไม่"
 * ซึ่งพลาดตรงที่เอกสารถูกล็อกทันทีที่เปิดซองลงนาม ผู้ยื่นที่กดส่งไปลงนามแล้วออกจากหน้าไป
 * โดยยังไม่ลงนาม จึงไม่เห็นอะไรเตือนอีกเลย
 *
 * <p>กติกา: <b>ลงนาม = ยื่นการแก้ไข</b> เอกสารที่ไม่มีช่องลงนามของผู้ยื่นใช้ปุ่ม "ยื่นการแก้ไข" แทน
 * จบเมื่อทุกคนที่ต้องลงนามใหม่ลงนามครบ ({@code documentsAwaitingResign})
 */
public final class RevisionProgress {

    private RevisionProgress() {
    }

    public enum Stage {
        TO_EDIT("ต้องแก้ไข", true),
        TO_SIGN("แก้ไขแล้ว รอท่านลงนาม", true),
        SUBMITTED("ยื่นการแก้ไขแล้ว อยู่ระหว่างการตรวจสอบ", false);

        private final String thaiLabel;
        private final boolean actionRequired;

        Stage(String thaiLabel, boolean actionRequired) {
            this.thaiLabel = thaiLabel;
            this.actionRequired = actionRequired;
        }

        public String getThaiLabel() { return thaiLabel; }
        public boolean isActionRequired() { return actionRequired; }
    }

    /** เอกสารหนึ่งฉบับที่ถูกส่งกลับ */
    public record SentBackDocument(int documentType, Stage stage, String note) {
        public Stage getStage() { return stage; }
        public String getNote() { return note; }
        public int getDocumentType() { return documentType; }
    }

    /** เอกสารที่ถูกส่งกลับทั้งหมดของคำร้องหนึ่ง เรียงตามเลขเอกสาร */
    public record Summary(Map<Integer, SentBackDocument> documents) {
        public Map<Integer, SentBackDocument> getDocuments() { return documents; }

        /** ยังมีเอกสารที่ผู้ยื่นต้องลงมือทำ */
        public boolean isActionRequired() {
            return documents.values().stream().anyMatch(d -> d.stage().isActionRequired());
        }

        public boolean isEmpty() { return documents.isEmpty(); }
    }

    /**
     * ขั้นของเอกสารหนึ่งฉบับ หรือ null เมื่อจบแล้ว (ลงนามใหม่ครบ)
     *
     * @param hasApplicantSlot เอกสารมีช่องลงนามของผู้ยื่น
     * @param requestedAt      เวลาส่งกลับรอบล่าสุด
     * @param submittedAt      เวลากด "ยื่นการแก้ไข" (ใช้เมื่อไม่มีช่องลงนามของผู้ยื่น)
     * @param envelopes        ซองลงนามของเอกสารนี้ทุกซอง
     * @param locked           เอกสารถูกล็อกเพราะมีซองที่เปิดอยู่หรือจบแล้ว
     * @param awaitingResign   ยังมีคนที่ต้องลงนามใหม่
     */
    static Stage stageOf(boolean hasApplicantSlot, LocalDateTime requestedAt, LocalDateTime submittedAt,
            List<SignatureRequest> envelopes, boolean locked, boolean awaitingResign) {
        boolean submitted = hasApplicantSlot
                ? applicantSignedAfter(envelopes, requestedAt)
                : submittedAt != null && !submittedAt.isBefore(requestedAt);
        if (!submitted) {
            return locked ? Stage.TO_SIGN : Stage.TO_EDIT;
        }
        return awaitingResign ? Stage.SUBMITTED : null;
    }

    /** ผู้ยื่นลงนามในซองที่เปิดหลังการส่งกลับแล้ว */
    static boolean applicantSignedAfter(List<SignatureRequest> envelopes, LocalDateTime requestedAt) {
        return envelopes.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(requestedAt))
                .filter(e -> e.getStatus() == SignatureRequestStatus.IN_PROGRESS
                        || e.getStatus() == SignatureRequestStatus.COMPLETED)
                .flatMap(e -> e.getSteps().stream())
                .anyMatch(step -> "applicant".equalsIgnoreCase(step.getSlotKey())
                        && step.getStatus() == SignatureStepStatus.SIGNED);
    }
}
