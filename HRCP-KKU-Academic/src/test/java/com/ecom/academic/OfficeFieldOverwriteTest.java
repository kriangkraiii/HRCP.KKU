package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignedDocumentRenderer;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เลขที่หนังสือและวันที่เอกสารเขียนทับได้หลังลงนามครบ
 *
 * <p>งานสารบรรณเดินคนละจังหวะกับการลงนาม: เลขที่หนังสือออกให้ <em>หลัง</em> เอกสารลงนามเสร็จ
 * ประตูล็อกเดิมปฏิเสธทั้งก้อน เจ้าหน้าที่จึงกรอกเลขไม่ได้เลย และ auto-draft เด้ง 409 ทุกครั้ง
 * ที่พิมพ์ โดยไม่มีข้อความบอกว่าเพราะอะไร
 *
 * <p>ที่เปิดให้เขียนทับคือ <em>สองช่องนี้เท่านั้น</em> และ <em>เฉพาะเมื่อลงนามครบแล้ว</em> —
 * ระหว่างที่ยังเวียนลงนามอยู่ทุกช่องยังปิดตายเหมือนเดิม เพราะคนที่ยังไม่เซ็นต้องเห็นสิ่งเดียว
 * กับคนที่เซ็นไปแล้ว
 */
@DisplayName("เขียนทับเลขที่หนังสือหลังลงนามครบ")
class OfficeFieldOverwriteTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private SignatureRequestRepository envelopes;

    @Autowired
    private SignedDocumentRenderer renderer;

    private UserDtls applicant;
    private UserDtls officer;

    private static String textOf(byte[] docx) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** ซองในสถานะที่ต้องการ โดยไม่ต้องเดินเวียนลงนามจริงทั้งกระบวนการ */
    private SignatureRequest envelopeFor(Long requestId, int docType, SignatureRequestStatus status) {
        String frozen = "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\",\"memo_no\":\"อว 660301.26.8/\"}";
        SignatureRequest envelope = new SignatureRequest();
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setRequestId(requestId);
        envelope.setDocumentType(docType);
        envelope.setStatus(status);
        envelope.setFrozenJson(frozen);
        envelope.setFrozenHash(SignatureWorkflowService.sha256(frozen));
        envelope.setVerificationCode("VC" + System.nanoTime());
        envelope.setCreatedAt(LocalDateTime.now());
        return envelopes.save(envelope);
    }

    @Nested
    @DisplayName("เมื่อลงนามครบแล้ว")
    class OnceSigned {

        @Test
        @DisplayName("กรอกเลขที่หนังสือและวันที่ได้")
        void officeFieldsRemainWritable() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");
            envelopeFor(request.getId(), 1, SignatureRequestStatus.COMPLETED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13")
                    .param("date", "๒๑ กันยายน ๒๕๖๙"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 660301.26.8/13")
                    .containsEntry("date", "๒๑ กันยายน ๒๕๖๙");
        }

        @Test
        @DisplayName("ช่องอื่นยังแก้ไม่ได้ แม้ส่งมาพร้อมกันในครั้งเดียว")
        void everythingElseStaysLocked() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1,
                    "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\",\"course_name\":\"วิชาของผู้ยื่น\"}");
            envelopeFor(request.getId(), 1, SignatureRequestStatus.COMPLETED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13")
                    .param("applicant_name", "แอดมินแอบแก้")
                    .param("course_name", "วิชาที่แอดมินแต่ง"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 660301.26.8/13")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้")
                    .containsEntry("course_name", "วิชาของผู้ยื่น");
        }

        @Test
        @DisplayName("auto-draft ต้องไม่ตอบ 409 อีกต่อไป")
        void autoDraftNoLongerConflicts() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");
            envelopeFor(request.getId(), 1, SignatureRequestStatus.COMPLETED);

            mvc.perform(post("/api/draft/academic/" + request.getId() + "/1")
                    .with(as(officer)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo_no\":\"อว 660301.26.8/13\",\"applicant_name\":\"แอดมินแอบแก้\"}"))
                    .andExpect(status().isOk());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 660301.26.8/13")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }

        /**
         * จุดตายของทั้งงานนี้ — เอกสารที่ลงนามแล้ว render จาก {@code frozenJson}
         * ถ้าไม่เติมทับตอน render เลขที่กรอกจะบันทึกสำเร็จแต่ไม่ปรากฏบนเอกสาร
         */
        @Test
        @DisplayName("เลขที่ที่กรอกทีหลังต้องขึ้นบนเอกสารที่ลงนามแล้วจริง")
        void theNewNumberReachesTheRenderedDocument() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");
            SignatureRequest envelope =
                    envelopeFor(request.getId(), 1, SignatureRequestStatus.COMPLETED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13"))
                    .andExpect(status().is3xxRedirection());

            byte[] docx = renderer.renderDocx(envelopes.findById(envelope.getId()).orElseThrow());

            assertThat(textOf(docx)).contains("อว 660301.26.8/13");
        }

        /**
         * เขียนทับช่องสารบรรณต้องไม่ไปแตะหลักฐานลายเซ็น — แฮชคิดจาก {@code frozenJson}
         * ซึ่งอยู่บนซอง ไม่ใช่จากแถวเอกสาร การเติมค่าจึงเกิดตอน render เท่านั้น
         */
        @Test
        @DisplayName("ผนึกของซองต้องยังตรงหลังเขียนทับ")
        void theEnvelopeSealStaysIntact() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");
            SignatureRequest envelope =
                    envelopeFor(request.getId(), 1, SignatureRequestStatus.COMPLETED);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13"))
                    .andExpect(status().is3xxRedirection());

            SignatureRequest after = envelopes.findById(envelope.getId()).orElseThrow();
            assertThat(SignatureWorkflowService.sha256(after.getFrozenJson()))
                    .isEqualTo(after.getFrozenHash());
            assertThat(after.getFrozenJson()).doesNotContain("อว 660301.26.8/13");
        }
    }

    @Nested
    @DisplayName("ระหว่างที่ยังเวียนลงนาม")
    class WhileCirculating {

        @Test
        @DisplayName("แม้แต่เลขที่หนังสือก็เขียนทับไม่ได้")
        void evenTheMemoNumberIsRefused() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1,
                    "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\",\"memo_no\":\"อว 660301.26.8/\"}");
            envelopeFor(request.getId(), 1, SignatureRequestStatus.IN_PROGRESS);

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13"))
                    .andExpect(status().is3xxRedirection());

            assertThat(academicService.getLatestDocumentData(request.getId(), 1))
                    .containsEntry("memo_no", "อว 660301.26.8/");
        }

        @Test
        @DisplayName("auto-draft ยังตอบ 409 พร้อมข้อความที่อ่านรู้เรื่อง")
        void autoDraftStillConflictsWithAReadableReason() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"memo_no\":\"อว 660301.26.8/\"}");
            envelopeFor(request.getId(), 1, SignatureRequestStatus.IN_PROGRESS);

            String body = mvc.perform(post("/api/draft/academic/" + request.getId() + "/1")
                    .with(as(officer)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memo_no\":\"อว 660301.26.8/13\"}"))
                    .andExpect(status().isConflict())
                    .andReturn().getResponse().getContentAsString();

            assertThat(body).contains("เวียนลงนาม");
        }
    }

    @Nested
    @DisplayName("เอกสารที่ยังไม่ได้ส่งลงนาม")
    class NotYetCirculating {

        @Test
        @DisplayName("ทำงานเหมือนเดิมทุกอย่าง")
        void behavesExactlyAsBefore() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"applicant_name\":\"ผู้ยื่นกรอกไว้\"}");

            mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/1")
                    .with(as(officer)).with(csrf())
                    .param("action", "draft")
                    .param("memo_no", "อว 660301.26.8/13")
                    .param("applicant_name", "แอดมินแอบแก้"))
                    .andExpect(status().is3xxRedirection());

            Map<String, String> stored = academicService.getLatestDocumentData(request.getId(), 1);
            assertThat(stored)
                    .containsEntry("memo_no", "อว 660301.26.8/13")
                    .containsEntry("applicant_name", "ผู้ยื่นกรอกไว้");
        }
    }
}
