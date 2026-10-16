package com.ecom.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * สิ่งที่ URL ชี้ไปไม่มีอยู่ (คำร้อง เอกสาร ไฟล์แนบที่ถูกลบ หรือเลขที่เดาเอา) — ตอบ 404 ไม่ใช่ 500
 *
 * <p>เดิม controller โยน {@code RuntimeException("Request not found")} ซึ่งไปจบที่หน้า "ระบบขัดข้อง" (500)
 * พร้อม stack trace ใน log ระดับ ERROR ทั้งที่ระบบไม่ได้ขัดข้อง เช่นกดลิงก์ในการแจ้งเตือนเก่าของแบบร่างที่ลบไปแล้ว
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
