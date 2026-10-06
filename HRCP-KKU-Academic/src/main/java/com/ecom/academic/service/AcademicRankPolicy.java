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

    /**
     * ผู้ยื่นเป็นศาสตราจารย์แล้วหรือไม่ — ตำแหน่งสูงสุด จึงยื่นอะไรต่อไม่ได้ทั้งประเมินการสอนและขอตำแหน่ง
     *
     * <p>ต่างจาก {@link #currentRank} ตรงที่โปรไฟล์ไม่ถูกเอกสารบัง: ได้ ศ. หลังประเมินการสอนไปแล้ว
     * เอกสารเก่าที่ยังเขียนว่า ผศ. ต้องไม่เปิดทางให้ยื่นต่อ ที่ไหนบอกว่า ศ. ก็ถือว่า ศ.
     */
    public static boolean holdsHighestRank(UserDtls user, String... documentedPositions) {
        AcademicRank fromProfile = user == null ? null : AcademicRank.of(user.getAcademicPosition());
        return fromProfile == AcademicRank.PROFESSOR
                || currentRank(user, documentedPositions) == AcademicRank.PROFESSOR;
    }

    /**
     * การขอตำแหน่งนี้ต้องใช้ผลประเมินการสอนหรือไม่ — ขอ ผศ./รศ. ต้องใช้เสมอ ขอ ศ. ไม่ต้องใช้ไม่ว่าจะมาจากตำแหน่งใด
     * (ประกาศ มข. ฉบับที่ 1669/2569 ข้อ ๖, ข้อบังคับ พ.ศ. 2569 ข้อ ๑๗.๒.๑ และ ๑๘.๓) อาจารย์หรือ ผศ. ที่ข้ามขั้นไปขอ ศ.
     * โดยวิธีพิเศษก็ดำเนินการแบบเดียวกับ ศ. วิธีปกติ จึงไม่ต้องประเมินการสอนเช่นกัน
     *
     * <p>{@code current} ยังรับไว้เพื่อให้ผู้เรียกไม่ต้องเปลี่ยน แต่ไม่มีผลต่อคำตอบแล้ว
     */
    public static boolean requiresTeachingEvaluation(AcademicRank current, AcademicRank target) {
        return target != null && target.requiresTeachingEvaluation();
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
