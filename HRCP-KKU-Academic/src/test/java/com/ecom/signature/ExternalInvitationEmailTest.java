package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.RecordingMailSender;

/**
 * หนังสือเชิญผู้ลงนามจากนอก มข. — ผู้รับไม่รู้จักระบบ อีเมลฉบับเดียวต้องบอกว่าเป็นคำร้องของใคร เรื่องอะไร
 * ท่านอยู่ในฐานะอะไร ต้องทำอะไร และเข้าระบบอย่างไร ขึ้นต้นด้วยคำนำหน้าและชื่อแบบหนังสือราชการ
 */
@DisplayName("หนังสือเชิญผู้ลงนามภายนอก")
class ExternalInvitationEmailTest extends AbstractFlowTest {

    private UserDtls officer;
    private AcademicRequest request;

    @BeforeEach
    void anEvaluation() {
        officer = data.admin();
        request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
        data.academicDocument(request, 1, "{\"title\":\"ผศ.ดร.\",\"applicant_name\":\"สมชาย ทดสอบยื่น\",\"chk2\":\"✓\","
                + "\"course_code\":\"CP353001\",\"course_name\":\"วิศวกรรมซอฟต์แวร์\"}");
    }

    private int invite(UserDtls who, String email, String title, Long requestId) throws Exception {
        String context = requestId == null ? ""
                : ",\"module\":\"ACADEMIC\",\"requestId\":" + requestId + ",\"documentType\":3,\"field\":\"committee_2_name\"";
        return mvc.perform(post("/api/people/external").with(csrf())
                .with(user(who.getEmail()).roles(who.getRole().replace("ROLE_", "")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"firstName\":\"วิภา\",\"lastName\":\"ภายนอก\",\"email\":\"" + email
                        + "\",\"affiliation\":\"มหาวิทยาลัยเชียงใหม่\"" + context + "}"))
                .andReturn().getResponse().getStatus();
    }

    private RecordingMailSender.Sent mailTo(String email) {
        awaitCondition("หนังสือเชิญถึง " + email, () -> !mail().to(email).isEmpty());
        return mail().to(email).get(0);
    }

    @Test
    @DisplayName("เชิญเป็นอนุกรรมการภายนอกจากเอกสารที่ 3 — บอกผู้ขอรับการประเมิน เรื่อง ฐานะ สิ่งที่ต้องทำ และวิธีเข้าระบบ")
    void theInvitationExplainsTheCase() throws Exception {
        assertThat(invite(officer, "wipa@partner.example.invalid", "รศ.ดร.", request.getId())).isEqualTo(200);

        RecordingMailSender.Sent sent = mailTo("wipa@partner.example.invalid");
        assertThat(sent.subject()).contains("อนุกรรมการประเมินผลการสอน (ผู้ทรงคุณวุฒิภายนอก)")
                .contains("ผศ.ดร.สมชาย ทดสอบยื่น");
        assertThat(sent.body())
                .contains("รศ.ดร.วิภา ภายนอก")
                .contains("การประเมินผลการสอนของ ผศ.ดร.สมชาย ทดสอบยื่น เพื่อประกอบการขอกำหนดตำแหน่งรองศาสตราจารย์")
                .contains("CP353001")
                .contains("ร่วมประเมินผลการสอน")
                .contains("แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66")
                .contains("KKU SSO").contains("wipa@partner.example.invalid")
                .contains(officer.getEmail())
                .contains("จึงเรียนมาเพื่อโปรดพิจารณา");
    }

    @Test
    @DisplayName("ผู้ยื่นใส่รหัสคำร้องของคนอื่น — หนังสือเชิญไม่มีรายละเอียดคำร้องนั้น")
    void anotherApplicantsRequestIsNotDescribed() throws Exception {
        UserDtls stranger = data.otherApplicant();

        assertThat(invite(stranger, "guest@partner.example.invalid", "นาย", request.getId())).isEqualTo(200);

        RecordingMailSender.Sent sent = mailTo("guest@partner.example.invalid");
        assertThat(sent.body()).doesNotContain("สมชาย ทดสอบยื่น").contains("นายวิภา ภายนอก");
        assertThat(sent.subject()).contains("แจ้งการเป็นผู้ลงนามเอกสารอิเล็กทรอนิกส์");
    }

    @Test
    @DisplayName("ไม่กรอกคำนำหน้า — เพิ่มไม่ได้ เพราะหนังสือต้องขึ้นต้นด้วยคำนำหน้าและชื่อ")
    void aTitleIsRequired() throws Exception {
        assertThat(invite(officer, "notitle@partner.example.invalid", "", request.getId())).isEqualTo(400);
        assertThat(mail().to("notitle@partner.example.invalid")).isEmpty();
    }
}
