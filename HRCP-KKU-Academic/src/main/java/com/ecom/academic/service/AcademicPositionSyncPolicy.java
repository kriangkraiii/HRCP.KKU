package com.ecom.academic.service;

import org.springframework.stereotype.Service;

/**
 * ตัดสินว่าตอนนี้เขียนทับตำแหน่งทางวิชาการของคนนี้จากต้นทางภายนอกได้ไหม
 *
 * <p>ทะเบียนบุคลากรของ มข. (ผ่าน SSO) ถูกกว่าค่าที่ค้างอยู่ในระบบเราเกือบทุกครั้ง การทับให้
 * เป็นปกติจึงเป็นเรื่องที่ควรทำ — <b>ยกเว้นระหว่างที่คำร้องยังเดินอยู่</b>
 *
 * <p>เหตุผลที่ต้องมีข้อยกเว้น: {@link AcademicRankPolicy} ใช้ตำแหน่งปัจจุบันตัดสินว่าผู้ยื่น
 * ขอตำแหน่งใดได้ และเอกสารที่ออกไปแล้วก็ประทับตำแหน่ง ณ ตอนนั้นไว้ ถ้าทะเบียนอัปเดตกลางคัน
 * (เช่น ผลการขอ ผศ. รอบก่อนเพิ่งมีผล) แล้วเราทับทันที คำร้องที่กำลังเวียนลงนามอยู่จะกลายเป็น
 * "ขอตำแหน่งที่ไม่สูงกว่าตำแหน่งปัจจุบัน" ทั้งที่ตอนยื่นถูกต้องทุกอย่าง — ผู้ยื่นแก้เองก็ไม่ได้
 * เพราะช่องนี้ล็อกไว้แล้ว ปล่อยให้คำร้องเดินจนจบแล้วค่อยทับในการล็อกอินครั้งถัดไปปลอดภัยกว่า
 *
 * <p>ถามทั้งสองระบบ เพราะค้างที่ใดที่หนึ่งก็คือค้าง และทั้งสองนิยาม "ค้าง" ไม่เหมือนกัน
 * โดยตั้งใจ (ฝั่ง position นับ DRAFT เป็นค้าง ฝั่งประเมินการสอนไม่นับ) การมาเขียนเงื่อนไข
 * สถานะเองที่นี่คือการทำพลาดซ้ำรอยที่ javadoc ของทั้งสองเมธอดนั้นเล่าไว้
 */
@Service
public class AcademicPositionSyncPolicy {

    private final PositionRequestService positionRequestService;
    private final AcademicRequestService academicRequestService;

    public AcademicPositionSyncPolicy(PositionRequestService positionRequestService,
            AcademicRequestService academicRequestService) {
        this.positionRequestService = positionRequestService;
        this.academicRequestService = academicRequestService;
    }

    /**
     * @param userId บัญชีที่กำลังจะเขียน — {@code null} คือบัญชีใหม่ที่ยังไม่มีแถว
     *               จึงยังไม่มีคำร้องของใครให้กระทบ
     */
    public boolean mayOverwrite(Integer userId) {
        if (userId == null) {
            return true;
        }
        return !positionRequestService.hasActiveRequest(userId)
                && !academicRequestService.hasActiveRequest(userId);
    }
}
