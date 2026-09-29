package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureRequestRepository;

/**
 * เอกสารที่เจ้าหน้าที่ส่งกลับให้ผู้ยื่นแก้ แล้ว<em>ผู้ยื่น</em>ยังไม่ได้ลงนามฉบับแก้ไข
 *
 * <p>ต่างจาก {@code documentsAwaitingResign} ของแต่ละเฟส ซึ่งค้างจนผู้ลงนาม<em>ทุกคน</em>ลงนามใหม่ครบ
 * — ช่วงหลังผู้ยื่นลงนามแล้วเป็นตาของเจ้าหน้าที่ (ตรวจแล้วเวียนต่อ) ส่วนกฎนี้ตอบว่า "ตอนนี้ลูกอยู่ที่ผู้ยื่นไหม"
 * ซึ่งใช้พักงานของเจ้าหน้าที่ทั้งคำร้อง ให้ผู้ยื่นแก้และส่งกลับมาก่อนแล้วค่อยดำเนินการต่อ
 *
 * <p>ผู้ยื่นถือว่าส่งกลับมาแล้วเมื่อมีซองที่เปิด<em>หลัง</em>การส่งกลับ ยังไม่ถูกยกเลิก/ปฏิเสธ และช่องผู้ยื่นลงนามแล้ว
 * เอกสารที่ไม่มีช่องลงนามของผู้ยื่นไม่นับ — ไม่มีสัญญาณที่เชื่อถือได้ว่าผู้ยื่นแก้เสร็จเมื่อไร
 */
final class ApplicantRevisionRule {

    private ApplicantRevisionRule() {
    }

    /**
     * @param sentBackAt เวลาส่งกลับล่าสุดของแต่ละเอกสาร (เฉพาะที่เคยถูกส่งกลับ)
     * @return เลขเอกสารที่รอผู้ยื่น เรียงจากน้อยไปมาก
     */
    static List<Integer> awaitingApplicant(SignatureModule module, Long requestId,
            Map<Integer, LocalDateTime> sentBackAt, SignatureRequestRepository envelopes) {
        List<Integer> pending = new ArrayList<>();
        new TreeMap<>(sentBackAt).forEach((type, at) -> {
            boolean applicantSigns = SignatureAnchorRegistry.slotsOf(module, type).stream()
                    .anyMatch(slot -> "applicant".equals(slot.slotKey()));
            if (!applicantSigns) {
                return;
            }
            boolean resigned = envelopes
                    .findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(module, requestId, type)
                    .stream()
                    .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().isAfter(at))
                    .filter(e -> e.getStatus() == SignatureRequestStatus.IN_PROGRESS
                            || e.getStatus() == SignatureRequestStatus.COMPLETED)
                    .flatMap(e -> e.getSteps().stream())
                    .anyMatch(step -> "applicant".equals(step.getSlotKey())
                            && step.getStatus() == SignatureStepStatus.SIGNED);
            if (!resigned) {
                pending.add(type);
            }
        });
        return pending;
    }
}
