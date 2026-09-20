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

    @Nested
    @DisplayName("ปุ่มขอให้เซ็นใหม่")
    class ResignButton {

        @Test
        @DisplayName("ขึ้นครบทุกเอกสารของผู้ยื่น (1 และ 2)")
        void coversEveryApplicantDocument() throws Exception {
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                    .with(as(officer)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(cardFor(html, 1)).contains("ขอให้เซ็นใหม่");
            assertThat(cardFor(html, 2)).contains("ขอให้เซ็นใหม่");
            // เอกสารของเจ้าหน้าที่ไม่ต้องส่งกลับให้ผู้ยื่น
            assertThat(cardFor(html, 3)).doesNotContain("ขอให้เซ็นใหม่");
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
