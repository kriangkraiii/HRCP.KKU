package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.util.EmailTemplateHelper;

/** อีเมลทุกฉบับเป็นจดหมายทางการ: เรื่อง / เรียน (คำนำหน้า + ชื่อ) / เนื้อหาที่บอกว่าหมายถึงอะไร / คำลงท้าย */
@DisplayName("อีเมลแบบทางการ")
class FormalEmailTest {

    private static UserDtls person(String title, String name) {
        UserDtls u = new UserDtls();
        u.setTitle(title);
        u.setName(name);
        return u;
    }

    @Test
    @DisplayName("ขึ้นต้นด้วยคำนำหน้าติดชื่อ ไม่มีคำนำหน้าใช้ \"คุณ\" — ไม่ขึ้นต้นด้วยชื่อเปล่า")
    void salutationCarriesTheTitle() {
        assertThat(EmailTemplateHelper.formalName(person("รศ.ดร.", "วิภา ภายนอก"))).isEqualTo("รศ.ดร.วิภา ภายนอก");
        assertThat(EmailTemplateHelper.formalName(person(null, "เกรียง ไกร"))).isEqualTo("คุณเกรียง ไกร");
        assertThat(EmailTemplateHelper.formalName(null)).isEqualTo("ท่าน");
    }

    @Test
    @DisplayName("แจ้งสถานะ: มีเรื่อง เรียน คำอธิบายว่าสถานะหมายถึงอะไร และลงท้ายแบบหนังสือราชการ")
    void statusEmailIsALetter() {
        String html = EmailTemplateHelper.buildStatusChangeEmail("ผศ.ดร.สมชาย ใจดี", "คำร้องขอรับการประเมินผลการสอน",
                "AC-0001", "รับคำร้อง", "แต่งตั้งอนุกรรมการ", "#000",
                AcademicEmailService.meaningOf(RequestStatus.RECEIVED, RequestStatus.SUB_COMMITTEE_APPOINTED), "");

        assertThat(html).contains("เรื่อง").contains("เรียน").contains("ผศ.ดร.สมชาย ใจดี")
                .contains("คณบดีได้ลงนามคำสั่งแต่งตั้งคณะอนุกรรมการ")
                .contains("จึงเรียนมาเพื่อโปรดทราบ")
                .contains(EmailTemplateHelper.SIGN_OFF_UNIT);
    }

    @Test
    @DisplayName("ทุกสถานะของทั้งสองเฟสมีคำอธิบายให้ผู้ยื่น")
    void everyStatusIsExplained() {
        for (RequestStatus status : RequestStatus.values()) {
            assertThat(AcademicEmailService.meaningOf(RequestStatus.RECEIVED, status)).as(status.name()).isNotBlank();
        }
        for (PositionRequestStatus status : PositionRequestStatus.values()) {
            assertThat(PositionEmailService.meaningOf(status)).as(status.name()).isNotBlank();
        }
    }

    @Test
    @DisplayName("ข้อความที่ผู้ใช้กรอกถูก escape — ใส่ HTML ในข้อเสนอแนะไม่ได้")
    void userTextIsEscaped() {
        String html = EmailTemplateHelper.buildSuggestionEmail("คุณสมชาย", "AC-1", "<script>alert(1)</script>");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
