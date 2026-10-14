package com.ecom.academic.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ecom.academic.model.SignatureModule;

/**
 * สถานะจริงของเอกสารแต่ละฉบับ — ทุกหน้าที่ระบายสีสถานะเอกสารใช้ค่าจากที่นี่ที่เดียว
 *
 * <p>เดิมแต่ละหน้าตัดสินเอง: บางหน้านับ "บันทึกแล้ว" ว่าเสร็จ บางหน้านับ "ผู้ยื่นเซ็นแล้ว" ว่าเสร็จ
 * แถวไฟล์ใต้กล่องเอกสารเขียวเสมอ กล่องจึงเขียวทั้งที่ยังมีคนต้องลงนามหรือยังไม่ได้ออกเลขที่หนังสือ
 *
 * <p>เขียวแปลว่าจบจริงเท่านั้น: บันทึกแล้ว ลงนามครบทุกช่องไม่เหลือใครให้ส่งต่อ และออกเลขที่หนังสือ/วันที่
 * ครบ (ถ้าเอกสารนั้นมีช่องเหล่านี้) ขั้นใดยังไม่จบก็บอกขั้นนั้น
 */
public class DocumentProgress {

    public enum Stage {
        NOT_STARTED("รอกรอก", "fa-clock", ""),
        DRAFT("ร่าง", "fa-pencil", "dgi-draft"),
        AWAITING_SIGNATURE("รอลงนาม", "fa-pen-nib", "dgi-draft"),
        /** ส่วนของผู้ยื่นลงนามแล้ว ผู้ลงนามที่เหลือรอเจ้าหน้าที่ตรวจแล้วส่งต่อ */
        AWAITING_FORWARD("รอเจ้าหน้าที่ส่งต่อ", "fa-paper-plane", "dgi-draft"),
        AWAITING_OFFICE("รอออกเลข", "fa-hashtag", "dgi-draft"),
        DONE("เสร็จแล้ว", "fa-check", "dgi-completed");

        private final String label;
        private final String icon;
        private final String cardClass;

        Stage(String label, String icon, String cardClass) {
            this.label = label;
            this.icon = icon;
            this.cardClass = cardClass;
        }

        public String getLabel() {
            return label;
        }

        public String getIcon() {
            return icon;
        }

        /** คลาสของกล่องเอกสารในหน้าคำร้อง (dgi-completed = เขียว, dgi-draft = ส้ม) */
        public String getCardClass() {
            return cardClass;
        }

        /** สีของป้ายสถานะ */
        public String getBadgeClass() {
            return this == DONE ? "bg-success" : "bg-warning text-dark";
        }

        public boolean isDone() {
            return this == DONE;
        }

        public boolean isStarted() {
            return this != NOT_STARTED;
        }
    }

    /** ป้ายสถานะของเอกสารหนึ่งฉบับในหน้าคำร้องของผู้ยื่น */
    public record ApplicantBadge(String label, String icon, String badgeClass) {
    }

    private static final String NEUTRAL = "bg-body-secondary text-body-secondary border";
    private static final String PENDING = "bg-warning-subtle text-warning-emphasis border border-warning-subtle";
    private static final String SUCCESS = "bg-success-subtle text-success border border-success-subtle";

    /**
     * ป้ายเดียวของการ์ดเอกสารในหน้าผู้ยื่น เขียนจากมุมของผู้ยื่น
     *
     * <p>เดิมการ์ดมีป้าย "ลงนามแล้ว" แยกกับป้ายสถานะ จึงขึ้นคู่ที่ขัดกันได้ เช่น "ลงนามแล้ว" คู่กับ
     * "รอยื่นคำร้อง" ซึ่งเป็นงานของทั้งคำร้อง ไม่ใช่ของเอกสาร และป้ายพื้นทึบมีไอคอนดูเหมือนปุ่ม
     * ป้ายจึงเป็นพื้นอ่อนมีขอบ ส่วนคำว่า "ยื่นคำร้อง" มีแค่ปุ่มท้ายหน้า
     *
     * <p>ก่อนส่งคำร้อง {@link Stage#AWAITING_FORWARD} ใช้ "พร้อมยื่น" ไม่ใช่ "ลงนามแล้ว" เพราะเอกสารที่ไม่มีช่อง
     * ของผู้ยื่น (ผู้อื่นลงนามรอส่งต่อ) ก็อยู่ขั้นนี้ได้
     *
     * @param submitted       ผู้ยื่นส่งคำร้องแล้ว — ก่อนส่ง เจ้าหน้าที่ยังส่งต่อให้ผู้ลงนามคนอื่นไม่ได้
     * @param applicantSigned ส่วนของผู้ยื่นลงนามครบแล้ว (หรือเอกสารนี้ไม่มีช่องของผู้ยื่น)
     */
    public static ApplicantBadge applicantBadge(Stage stage, boolean submitted, boolean applicantSigned) {
        return switch (stage) {
            case NOT_STARTED -> new ApplicantBadge("รอกรอก", "fa-clock", NEUTRAL);
            case DRAFT -> new ApplicantBadge("ร่าง", "fa-pencil", PENDING);
            case AWAITING_SIGNATURE -> applicantSigned
                    ? new ApplicantBadge("รอผู้อื่นลงนาม", "fa-hourglass-half", PENDING)
                    : new ApplicantBadge("รอท่านลงนาม", "fa-pen-nib", PENDING);
            case AWAITING_FORWARD -> submitted
                    ? new ApplicantBadge("รอเจ้าหน้าที่ส่งต่อ", "fa-hourglass-half", PENDING)
                    : new ApplicantBadge("พร้อมยื่น", "fa-check", SUCCESS);
            case AWAITING_OFFICE -> new ApplicantBadge("รอออกเลข", "fa-hashtag", PENDING);
            case DONE -> new ApplicantBadge("เสร็จแล้ว", "fa-check", SUCCESS);
        };
    }

    /** แถวเอกสารหนึ่งแถว ไม่ผูกกับเฟส */
    public record Row(int documentType, boolean draft, String json) {
    }

    private final SignatureWorkflowService signatureWorkflow;

    public DocumentProgress(SignatureWorkflowService signatureWorkflow) {
        this.signatureWorkflow = signatureWorkflow;
    }

    /** สถานะของเอกสารทุกฉบับใน {@code types} — ฉบับที่ยังไม่มีแถวได้ {@link Stage#NOT_STARTED} */
    public Map<Integer, Stage> of(SignatureModule module, Long requestId, Collection<Integer> types,
            List<Row> rows) {
        Map<Integer, Stage> stages = new LinkedHashMap<>();
        for (Integer type : types) {
            stages.put(type, stageOf(module, requestId, type,
                    rows.stream().filter(r -> r.documentType() == type).toList()));
        }
        return stages;
    }

    /**
     * สถานะรวมของเอกสารที่แยกหลายฉบับ (เอกสารที่ 9 เฟส 2 แยกตามผลงาน) — ฉบับที่ช้าที่สุดเป็นตัวกำหนด
     * ไม่มีฉบับเลยถือว่ายังไม่เริ่ม
     */
    public static Stage weakest(Collection<Stage> stages) {
        return stages.stream()
                .min(java.util.Comparator.comparingInt(Stage::ordinal))
                .orElse(Stage.NOT_STARTED);
    }

    public Stage stageOf(SignatureModule module, Long requestId, int type, List<Row> rows) {
        if (rows.isEmpty()) {
            return Stage.NOT_STARTED;
        }
        // ส่งลงนามแล้วคือฉบับจริงแม้แถวยังติดธงร่าง — ปุ่มส่งลงนามบันทึกผ่านร่างอัตโนมัติ
        boolean locked = signatureWorkflow.isDocumentLocked(module, requestId, type);
        if (!locked && rows.stream().allMatch(Row::draft)) {
            return Stage.DRAFT;
        }
        boolean needsSignature = !SignatureAnchorRegistry.slotsOf(module, type).isEmpty();
        if (needsSignature && signatureWorkflow.awaitsStaffRelease(module, requestId, type)) {
            return Stage.AWAITING_FORWARD;
        }
        if (needsSignature && !(signatureWorkflow.isSigningComplete(module, requestId, type)
                && !signatureWorkflow.awaitsMoreSigners(module, requestId, type))) {
            return Stage.AWAITING_SIGNATURE;
        }
        // ฉบับจริงคือแถวที่ไม่ใช่ร่าง แถวร่างเก่าที่ค้างอยู่ข้าง ๆ (เช่น เอกสารที่ 5 ที่มีสามสำเนา) ไม่นับ
        // เว้นแต่มีแต่แถวร่าง ซึ่งแปลว่าส่งลงนามผ่านร่างอัตโนมัติ แถวนั้นจึงเป็นฉบับจริง
        boolean anySaved = rows.stream().anyMatch(r -> !r.draft());
        boolean officePending = rows.stream()
                .filter(r -> !r.draft() || !anySaved)
                .anyMatch(r -> !DocumentCompleteness.missingOfficeFields(module, type, r.json()).isEmpty());
        return officePending ? Stage.AWAITING_OFFICE : Stage.DONE;
    }
}
