package com.ecom.academic.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ecom.academic.model.SignatureModule;

/**
 * พักงานของเจ้าหน้าที่ทั้งคำร้อง ระหว่างรอผู้ยื่นแก้เอกสารที่ถูกส่งกลับ
 *
 * <p>ส่งกลับเอกสารฉบับหนึ่งแล้ว เจ้าหน้าที่ต้องรอให้ผู้ยื่นแก้และลงนามใหม่ก่อน จึงจะดำเนินการต่อได้
 * <em>ทุกเอกสาร</em> — บันทึกช่องของเจ้าหน้าที่ ส่งเวียนลงนาม เริ่มเวียน และส่งต่อผู้ลงนามถัดไป
 * สิ่งที่ยังทำได้คือส่งกลับเอกสารฉบับอื่นเพิ่ม (รวบข้อแก้ไขส่งไปทีเดียว) และยกเลิกการเวียนลงนาม
 */
@Component
public class ApplicantRevisionHold {

    private final AcademicRequestService academic;
    private final PositionRequestService position;

    public ApplicantRevisionHold(AcademicRequestService academic, PositionRequestService position) {
        this.academic = academic;
        this.position = position;
    }

    /** เลขเอกสารที่รอผู้ยื่นแก้และลงนามใหม่ — ว่างเมื่อเจ้าหน้าที่ดำเนินการต่อได้ */
    public List<Integer> pending(SignatureModule module, Long requestId) {
        if (module == null || requestId == null) {
            return List.of();
        }
        return module == SignatureModule.POSITION
                ? position.documentsAwaitingApplicant(requestId)
                : academic.documentsAwaitingApplicant(requestId);
    }

    /** ชื่อเอกสารที่รออยู่ สำหรับแสดงบนหน้าจอ เช่น "เอกสารที่ 1 (บันทึกข้อความ…)" */
    public List<String> pendingLabels(SignatureModule module, Long requestId) {
        return pending(module, requestId).stream()
                .map(type -> "เอกสารที่ " + type + " (" + label(module, type) + ")")
                .toList();
    }

    /** ข้อความบอกเหตุผลที่ดำเนินการต่อไม่ได้ — null เมื่อไม่ได้พักอยู่ */
    public String blocker(SignatureModule module, Long requestId) {
        List<String> labels = pendingLabels(module, requestId);
        if (labels.isEmpty()) {
            return null;
        }
        return "ยังดำเนินการต่อไม่ได้ — รอผู้ยื่นแก้ไขและลงนามใหม่: " + String.join(", ", labels);
    }

    private String label(SignatureModule module, int type) {
        return module == SignatureModule.POSITION ? position.getDocLabel(type)
                : AcademicRequestService.getDocLabel(type);
    }
}
