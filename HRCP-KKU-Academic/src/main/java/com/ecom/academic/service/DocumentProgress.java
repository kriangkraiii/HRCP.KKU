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
