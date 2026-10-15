package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
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

    private static final LocalDateTime SENT_BACK = LocalDateTime.of(2026, 10, 15, 9, 0);

    /** ซองที่เปิดหลังการส่งกลับ ผู้ยื่นลงนามแล้ว คณบดีลงนามแล้วหรือยังขึ้นกับสถานะซอง */
    private static SignatureRequest applicantSignedEnvelope(SignatureRequestStatus status) {
        SignatureRequest envelope = new SignatureRequest();
        envelope.setCreatedAt(SENT_BACK.plusHours(1));
        envelope.setStatus(status);
        SignatureStep applicant = new SignatureStep();
        applicant.setSlotKey("applicant");
        applicant.setStatus(SignatureStepStatus.SIGNED);
        envelope.addStep(applicant);
        SignatureStep dean = new SignatureStep();
        dean.setSlotKey("dean");
        dean.setStatus(status == SignatureRequestStatus.COMPLETED ? SignatureStepStatus.SIGNED : SignatureStepStatus.WAITING);
        envelope.addStep(dean);
        return envelope;
    }

    @Test
    @DisplayName("ผู้ยื่นลงนามฉบับแก้แล้ว ซองยังรอเจ้าหน้าที่/คณบดี: ยื่นการแก้ไขแล้ว ไม่ใช่หายไปเหมือนจบแล้ว")
    void signedButStillCirculatingIsSubmitted() {
        assertThat(RevisionProgress.stageOf(true, SENT_BACK, null,
                List.of(applicantSignedEnvelope(SignatureRequestStatus.IN_PROGRESS)), true, false))
                .as("คณบดีไม่เคยลงนามก่อนส่งกลับ จึงไม่มีใครต้องลงนามซ้ำ แต่ซองยังไม่ครบ สถานะคำร้องยังเป็นส่งแก้ไข"
                        + " — ถ้านับว่าจบ ผู้ยื่นจะไม่เห็นอะไรบอกว่าส่งไปแล้ว")
                .isEqualTo(Stage.SUBMITTED);
    }

    @Test
    @DisplayName("ซองที่ผู้ยื่นลงนามฉบับแก้ลงนามครบแล้ว: จบ")
    void completedEnvelopeEndsTheRevision() {
        assertThat(RevisionProgress.stageOf(true, SENT_BACK, null,
                List.of(applicantSignedEnvelope(SignatureRequestStatus.COMPLETED)), true, false)).isNull();
    }
}
