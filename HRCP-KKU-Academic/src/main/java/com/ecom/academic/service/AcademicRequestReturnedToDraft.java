package com.ecom.academic.service;

import com.ecom.model.UserDtls;

/**
 * คำร้องประเมินการสอนถูกส่งคืนให้ผู้ยื่นแก้ไข (Flow ข้อ 2 — คณบดีไม่เห็นชอบ).
 *
 * <p>เป็น event แทนการเรียก {@link SignatureWorkflowService} ตรง ๆ จาก {@link AcademicRequestService}
 * เพราะสองตัวนี้พึ่งกันเป็นวง (ผ่าน {@code DocumentSnapshotProvider}) ตัวรับทำงานแบบ synchronous
 * อยู่ใน transaction เดียวกับการเปลี่ยนสถานะ ถ้ายกเลิกลายเซ็นไม่สำเร็จ การส่งคืนก็ถูก rollback ด้วย
 */
public record AcademicRequestReturnedToDraft(Long requestId, UserDtls returnedBy, String reason) {
}
