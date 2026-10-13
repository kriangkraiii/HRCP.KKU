package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.service.RevisionProgress.SentBackDocument;
import com.ecom.academic.service.RevisionProgress.Stage;
import com.ecom.academic.service.RevisionProgress.Summary;

/**
 * สถานะที่แสดงเมื่อมีเอกสารถูกส่งกลับ — การส่งกลับไม่เปลี่ยนสถานะคำร้อง
 * เดิมทั้งหน้าแอดมินและผู้ยื่นจึงยังขึ้น "รับคำร้อง · กำลังดำเนินการ" ทั้งที่งานรอผู้ยื่นอยู่
 */
@DisplayName("สถานะที่แสดงเมื่อมีเอกสารถูกส่งกลับ")
class RevisionProgressSummaryTest {

    private static Summary summaryOf(Stage... stages) {
        Map<Integer, SentBackDocument> docs = new java.util.TreeMap<>();
        for (int i = 0; i < stages.length; i++) {
            docs.put(i + 1, new SentBackDocument(i + 1, stages[i], ""));
        }
        return new Summary(docs);
    }

    @Test
    @DisplayName("ยังมีเอกสารที่ผู้ยื่นต้องแก้: รอผู้ยื่นแก้ไข")
    void awaitingApplicant() {
        Summary summary = summaryOf(Stage.TO_EDIT, Stage.SUBMITTED);
        assertThat(summary.getStatusLabel()).isEqualTo("รอผู้ยื่นแก้ไขเอกสาร");
        assertThat(summary.getColor()).isEqualTo("#f57f17");
    }

    @Test
    @DisplayName("ผู้ยื่นยื่นการแก้ไขครบแล้ว: รอเจ้าหน้าที่ตรวจ")
    void submittedAwaitsStaff() {
        Summary summary = summaryOf(Stage.SUBMITTED);
        assertThat(summary.getStatusLabel()).isEqualTo("ยื่นการแก้ไขแล้ว · รอตรวจสอบ");
        assertThat(summary.getColor()).isEqualTo("#ef6c00");
    }
}
