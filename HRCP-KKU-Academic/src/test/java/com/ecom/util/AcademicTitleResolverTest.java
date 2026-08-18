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

    @Test
    @DisplayName("Should resolve full Thai academic position")
    void testResolveThaiPosition() {
        assertEquals("รองศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition(null, "Associate Professor Ph.D."));
        assertEquals("ผู้ช่วยศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition("ผศ.ดร.", "Assistant Professor"));
        assertEquals("ศาสตราจารย์", AcademicTitleResolver.resolveThaiAcademicPosition("ศ.", "Professor"));
        assertEquals("อาจารย์", AcademicTitleResolver.resolveThaiAcademicPosition(null, "Lecturer"));
    }
}
