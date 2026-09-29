package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เอกสารที่ 5 ออกเป็นสามสำเนา หนึ่งฉบับต่อกรรมการหนึ่งท่าน
 *
 * <p>ทีมเคยเลื่อนเลขเอกสารทั้งชุด (doc0 → doc1) แล้วเลขที่ฮาร์ดโค้ดไว้ในเทมเพลตไม่ได้เลื่อน
 * ตามไปด้วย ป้าย "3 สำเนา" จึงค้างอยู่ที่เอกสารที่ 4 และปุ่มขอให้เซ็นใหม่ยังอ้างเอกสารที่ 0
 * ซึ่งไม่มีอยู่แล้ว เทสต์ชุดนี้ผูกกับ <em>กติกา</em> ไม่ใช่กับเลข เพื่อให้การเลื่อนเลขครั้งหน้า
 * พังที่นี่ก่อนจะไปพังบนหน้าเว็บ
 */
@DisplayName("สำเนาของเอกสารที่ออกหลายฉบับ")
class CommitteeCopiesTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private com.ecom.academic.repository.SignatureRequestRepository envelopes;

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

    /**
     * เนื้อในกล่องของเอกสารฉบับหนึ่งบนหน้ารายละเอียดคำร้อง
     *
     * <p>แบ่งตามกล่อง {@code doc-grid-item} ไม่ใช่ตามข้อความ "เอกสารที่ N:" เพราะข้อความนั้น
     * โผล่ซ้ำใน attribute ของปุ่มด้วย การตัดด้วยข้อความจึงได้กล่องแหว่งกลางคัน
     */
    private static String cardFor(String html, int documentType) {
        Matcher m = Pattern.compile("class=\"doc-grid-item[ \"](.*?)(?=class=\"doc-grid-item[ \"]|$)",
                Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String card = m.group(1);
            if (card.contains("เอกสารที่ " + documentType + ":")) {
                return card;
            }
        }
        throw new AssertionError("ไม่พบกล่องของเอกสารที่ " + documentType + " บนหน้า");
    }

    @Nested
    @DisplayName("ป้ายบอกจำนวนสำเนา")
    class CopiesBadge {

        @Test
        @DisplayName("ขึ้นที่เอกสารที่ 5 ไม่ใช่เอกสารที่ 4")
        void sitsOnTheDocumentThatActuallyHasCopies() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                    .with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(cardFor(html, 5)).contains("3 สำเนา");
            assertThat(cardFor(html, 4)).doesNotContain("สำเนา");
        }
    }

    /**
     * ส่งเอกสารกลับให้ผู้ยื่นแก้และลงนามใหม่ มีที่เดียว คือหน้าเอกสารฉบับนั้น
     *
     * <p>เดิมหน้ารายละเอียดคำร้องมีปุ่ม "ขอให้เซ็นใหม่" ซ้ำอีกชุดในรายการเอกสาร ยิงไปปลายทาง
     * เดียวกัน ({@code request-resign}) แต่เป็นหน้าต่างคนละอัน เจ้าหน้าที่จึงเห็นเป็นสองฟังก์ชัน
     */
    @Nested
    @DisplayName("ส่งกลับให้ผู้ยื่นแก้ไขและลงนามใหม่")
    class SendBack {

        @Test
        @DisplayName("หน้ารายละเอียดคำร้องไม่มีปุ่มขอให้เซ็นใหม่ซ้ำแล้ว")
        void theRequestPageNoLongerDuplicatesIt() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                    .with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(html).doesNotContain("ขอให้เซ็นใหม่").doesNotContain("resignModal");
        }

        @Test
        @DisplayName("ส่งกลับแล้ว — หน้าเอกสารบอกว่ารอผู้ยื่น ไม่มีปุ่มส่งกลับซ้ำหรือฟอร์มเวียนลงนาม")
        void afterSendingBackThePageSaysItIsWaitingForTheApplicant() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"course_code\":\"CP123456\"}");
            String page = "/admin/academic/request/" + request.getId() + "/document/1";

            String before = mvc.perform(get(page).with(as(officer)))
                    .andReturn().getResponse().getContentAsString();
            assertThat(before).as("ก่อนส่งกลับ เจ้าหน้าที่ยังเวียนลงนามได้")
                    .contains("/esign/envelope/create")
                    .doesNotContain("รอผู้ยื่นแก้ไขและลงนามใหม่");

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post(page + "/request-resign")
                    .param("reason", "ชื่อรายวิชาไม่ตรง")
                    .with(org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .with(as(officer)))
                    .andExpect(status().is3xxRedirection());

            String after = mvc.perform(get(page).with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(after)
                    .contains("รอผู้ยื่นแก้ไขและลงนามใหม่")
                    .contains("ชื่อรายวิชาไม่ตรง")
                    .doesNotContain("/esign/envelope/create")
                    .doesNotContain("ยืนยันความถูกต้องและส่งเวียนลงนาม")
                    .doesNotContain("id=\"sendBackPanel\"");
        }

        /** เจ้าหน้าที่ส่งเอกสารที่ 1 กลับให้ผู้ยื่นแก้ */
        private AcademicRequest sentBackDocumentOne() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 1, "{\"course_code\":\"CP123456\"}");
            data.academicDocument(request, 2, "{\"staff_check_1\":\"\"}");
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/admin/academic/request/" + request.getId() + "/document/1/request-resign")
                    .param("reason", "ลายเซ็นไม่ชัดเจน")
                    .with(org.springframework.security.test.web.servlet.request
                            .SecurityMockMvcRequestPostProcessors.csrf())
                    .with(as(officer)))
                    .andExpect(status().is3xxRedirection());
            return request;
        }

        @Test
        @DisplayName("ส่งกลับเอกสารหนึ่งฉบับ — เอกสารฉบับอื่นก็พักไว้ บอกว่ารอเอกสารไหน และยังส่งกลับเพิ่มได้")
        void sendingOneBackHoldsEveryOtherDocument() throws Exception {
            AcademicRequest request = sentBackDocumentOne();

            String doc2 = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/2")
                    .with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(doc2)
                    .contains("คำร้องนี้รอผู้ยื่นแก้ไขเอกสารที่ส่งกลับ")
                    .contains("เอกสารที่ 1 (")
                    .doesNotContain("/esign/envelope/create")
                    .doesNotContain("ยืนยันความถูกต้องและส่งเวียนลงนาม")
                    // ส่งกลับเอกสารฉบับนี้เพิ่มได้ รวบข้อแก้ไขส่งไปทีเดียว
                    .contains("/document/2/request-resign");
        }

        @Test
        @DisplayName("ระหว่างพัก — เซิร์ฟเวอร์ไม่รับการบันทึกหรือส่งเวียนลงนามของเจ้าหน้าที่ แม้ยิงตรง")
        void theServerRefusesStaffActionsWhileOnHold() throws Exception {
            AcademicRequest request = sentBackDocumentOne();
            var csrf = org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.csrf();

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/admin/academic/request/" + request.getId() + "/document/2")
                    .param("staff_check_1", "✓")
                    .with(csrf).with(as(officer)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .redirectedUrl("/admin/academic/request/" + request.getId()
                                    + "/document/2?error=on_hold_for_applicant"));

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/esign/envelope/create")
                    .param("module", "ACADEMIC")
                    .param("requestId", String.valueOf(request.getId()))
                    .param("documentType", "2")
                    .with(csrf).with(as(officer)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .flash().attribute("errorMsg",
                                    org.hamcrest.Matchers.containsString("รอผู้ยื่นแก้ไขและลงนามใหม่")));
        }

        @Test
        @DisplayName("ผู้ยื่นลงนามฉบับแก้ไขแล้ว — พักจบ เจ้าหน้าที่ดำเนินการต่อได้")
        void theHoldLiftsOnceTheApplicantSignsAgain() throws Exception {
            AcademicRequest request = sentBackDocumentOne();
            assertThat(academicService.documentsAwaitingApplicant(request.getId())).containsExactly(1);

            // ผู้ยื่นลงนามเอกสารที่ 1 ฉบับแก้ไข — ซองใหม่ที่เปิดหลังการส่งกลับ ช่องผู้ยื่นลงนามแล้ว
            var envelope = new com.ecom.academic.model.SignatureRequest();
            envelope.setModule(com.ecom.academic.model.SignatureModule.ACADEMIC);
            envelope.setRequestId(request.getId());
            envelope.setDocumentType(1);
            envelope.setFrozenJson("{}");
            envelope.setFrozenHash("test");
            envelope.setVerificationCode("HOLD-" + request.getId());
            var step = new com.ecom.academic.model.SignatureStep();
            step.setSignatureRequest(envelope);
            step.setStepOrder(1);
            step.setSlotKey("applicant");
            step.setAnchorPlaceholder("{{sig_applicant}}");
            step.setStatus(com.ecom.academic.model.SignatureStepStatus.SIGNED);
            envelope.getSteps().add(step);
            envelopes.save(envelope);

            assertThat(academicService.documentsAwaitingApplicant(request.getId())).isEmpty();
            String doc2 = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/2")
                    .with(as(officer)))
                    .andReturn().getResponse().getContentAsString();
            assertThat(doc2).doesNotContain("คำร้องนี้รอผู้ยื่นแก้ไขเอกสารที่ส่งกลับ");
        }

        @Test
        @DisplayName("หน้าเอกสารของผู้ยื่นแต่ละฉบับ (1 และ 2) ยังส่งกลับได้")
        void eachApplicantDocumentPageOffersIt() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            for (int type : new int[] { 1, 2 }) {
                String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/" + type)
                        .with(as(officer)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();

                assertThat(html).as("เอกสารที่ " + type)
                        .contains("ส่งกลับให้ผู้ยื่นแก้ไข")
                        .contains("/admin/academic/request/" + request.getId() + "/document/" + type + "/request-resign");
            }
        }
    }

    @Nested
    @DisplayName("ลำดับสำเนา")
    class CopyOrder {

        /**
         * สำเนาที่ 1 คือประธานอนุกรรมการ ที่ 2 คืออนุกรรมการ ที่ 3 คืออนุกรรมการและเลขานุการ
         * ตามลำดับที่เจ้าหน้าที่กรอกในฟอร์ม รายการบนหน้าเว็บและไฟล์ที่ดาวน์โหลดจึงต้องเรียง
         * ตามนั้นเสมอ ไม่ใช่ตามใจฐานข้อมูล
         */
        @Test
        @DisplayName("เรียงตามลำดับที่กรอก แม้จะถูกบันทึกสลับลำดับ")
        void followsTheOrderTheyWereFilledIn() {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            // บันทึกสลับลำดับโดยตั้งใจ
            data.academicDocument(request, 5, "{\"committee_position\":\"อนุกรรมการและเลขานุการ\"}", 3);
            data.academicDocument(request, 5, "{\"committee_position\":\"ประธานอนุกรรมการ\"}", 1);
            data.academicDocument(request, 5, "{\"committee_position\":\"อนุกรรมการ\"}", 2);

            List<AcademicDocument> copies = academicService.getDocumentsByType(request.getId(), 5);

            assertThat(copies).extracting(AcademicDocument::getCopyNumber)
                    .containsExactly(1, 2, 3);
            assertThat(copies.get(0).getJsonData()).contains("ประธานอนุกรรมการ");
            assertThat(copies.get(2).getJsonData()).contains("อนุกรรมการและเลขานุการ");
        }

        @Test
        @DisplayName("แถวร่าง (สำเนาที่ 0) มาก่อนสำเนาจริงเสมอ")
        void theDraftRowComesFirst() {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            data.academicDocument(request, 5, "{\"memo_no\":\"สำเนาที่ 2\"}", 2);
            data.academicDocument(request, 5, "{\"memo_no\":\"แถวร่าง\"}", 0);

            assertThat(academicService.getDocumentsByType(request.getId(), 5))
                    .extracting(AcademicDocument::getCopyNumber)
                    .containsExactly(0, 2);
        }
    }
}
