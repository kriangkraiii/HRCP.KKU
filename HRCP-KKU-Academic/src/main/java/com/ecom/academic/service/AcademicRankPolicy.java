package com.ecom.academic.service;

import java.util.Optional;

import com.ecom.academic.model.AcademicRank;
import com.ecom.model.UserDtls;

/**
 * ตำแหน่งที่ขอต้องสูงกว่าตำแหน่งที่ถืออยู่ ข้ามขั้นได้ แต่ขอตำแหน่งเดิมหรือต่ำกว่าไม่ได้
 *
 * <p>ใช้ร่วมกันทั้งสองเฟส: ตอนกรอกเอกสารที่ 1 ของการประเมินการสอน และตอนสร้าง/ยื่นคำร้องขอตำแหน่ง
 */
public final class AcademicRankPolicy {

    private AcademicRankPolicy() {
    }

    /**
     * ตำแหน่งปัจจุบันของผู้ยื่น
     *
     * <p>ยึดเอกสารที่กรอกไว้ก่อน เรียงตามลำดับที่ส่งมา แล้วค่อยใช้โปรไฟล์ เพราะโปรไฟล์ sync จาก HR
     * ได้ตลอดเวลา แต่สิ่งที่เขียนไว้ในเอกสารคือสิ่งที่ถูกพิจารณาจริง เอกสารที่พิมพ์ข้อความซึ่งไม่ได้ระบุ
     * ตำแหน่งวิชาการจะถูกข้ามไป ไม่อย่างนั้นพิมพ์อะไรก็ได้ลงช่องนั้นก็หลบกติกาได้
     *
     * <p>ถ้าไม่มีที่ไหนระบุตำแหน่งวิชาการเลย ถือว่าเป็นอาจารย์ กติกานี้มีไว้กันคนที่ถือตำแหน่งอยู่แล้ว
     * ไม่ใช่กันคนที่ระบบยังไม่รู้จัก
     */
    public static AcademicRank currentRank(UserDtls user, String... documentedPositions) {
        if (documentedPositions != null) {
            for (String documented : documentedPositions) {
                AcademicRank rank = AcademicRank.of(documented);
                if (rank != null) {
                    return rank;
                }
            }
        }
        AcademicRank fromProfile = user == null ? null : AcademicRank.of(user.getAcademicPosition());
        return fromProfile != null ? fromProfile : AcademicRank.LECTURER;
    }

    /** @return ข้อความแจ้งเหตุผล ถ้าตำแหน่งที่ขอไม่สูงกว่าตำแหน่งปัจจุบัน */
    public static Optional<String> rankViolation(AcademicRank current, AcademicRank target) {
        if (target == null || target.isAbove(current)) {
            return Optional.empty();
        }
        return Optional.of("ท่านดำรงตำแหน่ง" + current.thaiLabel()
                + "อยู่แล้ว ไม่สามารถขอกำหนดตำแหน่ง" + target.thaiLabel()
                + " ซึ่งเท่ากับหรือต่ำกว่าตำแหน่งปัจจุบันได้");
    }
}
