package com.ecom.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AcademicTitleResolverTest {

    @Test
    @DisplayName("Should resolve Associate Professor with Ph.D. to รศ.ดร.")
    void testResolveAssocProfDr() {
        assertEquals("รศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, "รองศาสตราจารย์ ดร.", "Associate Professor Ph.D."));
        assertEquals("รศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, null, "Associate ProfessorPh.D."));
        assertEquals("รศ.ดร.", AcademicTitleResolver.resolveShortTitle("รศ.ดร.", null, null));
    }

    @Test
    @DisplayName("Should resolve Assistant Professor with Ph.D. to ผศ.ดร.")
    void testResolveAsstProfDr() {
        assertEquals("ผศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, "ผู้ช่วยศาสตราจารย์ ดร.", "Assistant Professor Ph.D."));
        assertEquals("ผศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, "ผู้ช่วยศาสตราจารย์", "Assistant Professor Ph.D."));
        assertEquals("ผศ.ดร.", AcademicTitleResolver.resolveShortTitle("ผศ.ดร.", null, null));
    }

    @Test
    @DisplayName("Should resolve Professor with Ph.D. to ศ.ดร.")
    void testResolveProfDr() {
        assertEquals("ศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, "ศาสตราจารย์ ดร.", "Professor Ph.D."));
        assertEquals("ศ.ดร.", AcademicTitleResolver.resolveShortTitle(null, null, "Prof. Dr."));
    }

    @Test
    @DisplayName("Should resolve Lecturer and Dr")
    void testResolveLecturerAndDr() {
        assertEquals("อ.ดร.", AcademicTitleResolver.resolveShortTitle(null, "อาจารย์ ดร.", "Lecturer Ph.D."));
        assertEquals("อาจารย์", AcademicTitleResolver.resolveShortTitle(null, "อาจารย์", "Lecturer"));
        assertEquals("ดร.", AcademicTitleResolver.resolveShortTitle(null, "ดร.", "Dr."));
        assertEquals("นาย", AcademicTitleResolver.resolveShortTitle("นาย", null, null));
    }

    /**
     * SsoUserProvisioner.shortTitleFor พึ่งกติกาสองข้อนี้ทั้งหมด: ตำแหน่งมาจาก candidate
     * แรกที่เข้าเค้า ส่วนวุฒิ ดร. เก็บจาก candidate ไหนก็ได้ ถ้าใครมาแก้ resolveShortTitle
     * แล้วทำข้อใดข้อหนึ่งหาย คำนำหน้าที่ derive ตอนล็อกอิน SSO จะผิดแบบเงียบ ๆ
     */
    @Test
    @DisplayName("ตำแหน่งยึด candidate แรกที่เข้าเค้า ส่วนวุฒิ ดร. เก็บจากทุก candidate")
    void firstMatchingCandidateWinsTheRankButAnyCandidateCanSupplyTheDoctorate() {
        // เลื่อนขึ้น: คำนำหน้าเดิมให้วุฒิ ตำแหน่งใหม่ให้ยศ
        assertEquals("ผศ.ดร.", AcademicTitleResolver.resolveShortTitle("ผู้ช่วยศาสตราจารย์", "อ.ดร."));
        // ลดลง: ตำแหน่งใหม่ต้องชนะคำนำหน้าเดิมที่สูงเกินจริง
        assertEquals("ผศ.ดร.", AcademicTitleResolver.resolveShortTitle("ผู้ช่วยศาสตราจารย์", "รศ.ดร."));
        // ไม่มีวุฒิเดิมก็ไม่เติมให้
        assertEquals("รศ.", AcademicTitleResolver.resolveShortTitle("รองศาสตราจารย์", "นาย"));
        // สลับลำดับแล้วพัง — คือเหตุผลที่ลำดับ argument เป็นสาระ ไม่ใช่รสนิยม
        assertEquals("รศ.ดร.", AcademicTitleResolver.resolveShortTitle("รศ.ดร.", "ผู้ช่วยศาสตราจารย์"));
    }

    @Test
    @DisplayName("Should resolve full Thai academic position")
    void testResolveThaiPosition() {
        assertEquals("รองศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition(null, "Associate Professor Ph.D."));
        assertEquals("ผู้ช่วยศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition("ผศ.ดร.", "Assistant Professor"));
        assertEquals("ศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition("ศ.", "Professor"));
        assertEquals("อาจารย์", AcademicTitleResolver.resolveThaiAcademicPosition(null, "Lecturer"));
    }
}
