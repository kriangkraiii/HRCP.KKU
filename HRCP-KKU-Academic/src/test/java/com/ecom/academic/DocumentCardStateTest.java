package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * กรอบสีของกล่องเอกสารต้องบอกความจริงสามระดับ
 *
 * <p>เดิมนับว่า "มีแถวของเอกสารนี้" = เสร็จแล้ว กรอบจึงเขียวทันทีที่บันทึกร่างอัตโนมัติ
 * ทั้งที่เจ้าหน้าที่ยังไม่ได้กดบันทึกเอกสารเลย แยกไม่ออกว่าอะไรทำเสร็จจริง
 *
 * <p>ข้อมูลแยกร่างออกได้อยู่แล้วผ่าน {@code isDraft} — {@code saveDraft} ตั้ง true,
 * {@code saveDocument} ตั้ง false หน้าเว็บแค่ไม่ได้ใช้
 */
@DisplayName("กรอบสีของกล่องเอกสาร")
class DocumentCardStateTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    /** กล่องของเอกสารฉบับหนึ่ง แบ่งตาม doc-grid-item ไม่ใช่ตามข้อความ */
    private static String cardFor(String html, int documentType) {
        Matcher m = Pattern.compile("class=\"doc-grid-item[ \"](.*?)(?=class=\"doc-grid-item[ \"]|$)",
                Pattern.DOTALL).matcher(html);
        while (m.find()) {
            if (m.group(1).contains("เอกสารที่ " + documentType + ":")) {
                return m.group(0);
            }
        }
        throw new AssertionError("ไม่พบกล่องของเอกสารที่ " + documentType);
    }

    @Nested
    @DisplayName("เฟส 1")
    class Phase1 {

        private String page(AcademicRequest request) throws Exception {
            return mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("ยังไม่เคยทำ — ไม่มีสี")
        void untouchedHasNoColour() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            String card = cardFor(page(request), 3);
            assertThat(card).doesNotContain("dgi-completed").doesNotContain("dgi-draft");
        }

        @Test
        @DisplayName("บันทึกร่างแล้วแต่ยังไม่ได้บันทึกเอกสาร — เหลือง")
        void draftIsYellow() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicService.saveDraft(request, 3, "{\"dean_name\":\"ร่างไว้ก่อน\"}", "เอกสารที่ 3", null);

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed");
        }

        @Test
        @DisplayName("บันทึกเอกสารแล้ว — เขียว")
        void savedIsGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 3, "{\"dean_name\":\"บันทึกจริง\"}");

            String card = cardFor(page(request), 3);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft");
        }

        @Test
        @DisplayName("เอกสารที่ 5 มีทั้งแถวร่างและสามสำเนา — ต้องเป็นเขียว")
        void aDraftAlongsideRealCopiesIsStillGreen() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicService.saveDraft(request, 5, "{\"memo_no\":\"ร่าง\"}", "เอกสารที่ 5", null);
            for (int copy = 1; copy <= 3; copy++) {
                data.academicDocument(request, 5, "{\"memo_no\":\"จริง\"}", copy);
            }

            String card = cardFor(page(request), 5);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft");
        }
    }

    @Nested
    @DisplayName("เฟส 2")
    class Phase2 {

        private String page(PositionRequest request) throws Exception {
            return mvc.perform(get("/admin/position/request/" + request.getId()).with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }

        @Test
        @DisplayName("บันทึกร่างแล้ว — เหลือง ไม่ใช่ไม่มีสี")
        void draftIsYellow() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            positionService.saveDraft(request, 7, "{\"applicant_name\":\"ร่างไว้ก่อน\"}",
                    "เอกสารที่ 7", "ADMIN");

            String card = cardFor(page(request), 7);
            assertThat(card).contains("dgi-draft").doesNotContain("dgi-completed");
        }

        @Test
        @DisplayName("บันทึกเอกสารแล้ว — เขียว")
        void savedIsGreen() throws Exception {
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 7, "{\"applicant_name\":\"บันทึกจริง\"}");

            String card = cardFor(page(request), 7);
            assertThat(card).contains("dgi-completed").doesNotContain("dgi-draft");
        }
    }
}
