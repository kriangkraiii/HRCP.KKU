package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * Flow ข้อ 2: คณบดีไม่เห็นชอบ → กลับไปข้อ 1 (หน่วยสารบรรณรับเรื่อง).
 *
 * <p>ไม่มีการปฏิเสธที่ทำให้คำร้องตกไป แอดมินส่งคำร้องฉบับเดิมกลับเป็นแบบร่างพร้อมเหตุผล
 * ผู้ยื่นแก้แล้วยื่นใหม่ ส่งคืนได้เฉพาะตอนคำร้องอยู่ที่ขั้นรับคำร้อง เพราะหลังจากนั้นการตีกลับ
 * เป็นวงจรแก้ไขของคณะอนุกรรมการ (ข้อ 12-15)
 */
@DisplayName("คณบดีไม่เห็นชอบ — ส่งคืนคำร้องให้ผู้ยื่นแก้ไข")
class ReturnToDraftTest extends AbstractFlowTest {

    private static final String REASON = "คณบดีไม่เห็นชอบ: รายวิชาไม่ตรงกับสาขาที่ขอ";

    @Autowired
    private AcademicRequestService service;

    private String returnAs(UserDtls officer, AcademicRequest request, String note) throws Exception {
        return mvc.perform(post("/admin/academic/request/" + request.getId() + "/status")
                .param("status", "DRAFT")
                .param("note", note)
                .with(csrf()).with(user(officer.getEmail()).roles("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    @Test
    @DisplayName("ไม่ใส่เหตุผล — ส่งคืนไม่ได้ คำร้องยังอยู่ที่ขั้นรับคำร้อง")
    void aReturnWithoutAReasonIsRefused() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        String redirect = returnAs(data.admin(), request, "   ");

        assertThat(redirect).isEqualTo("/admin/academic/request/" + request.getId()
                + "?error=status_update_failed");
        assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.RECEIVED);
    }

    @Test
    @DisplayName("ใส่เหตุผล — คำร้องกลับเป็นแบบร่าง และประวัติเก็บเหตุผลไว้")
    void aReturnWithAReasonGoesBackToDraft() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        String redirect = returnAs(data.admin(), request, REASON);

        assertThat(redirect).isEqualTo("/admin/academic/request/" + request.getId()
                + "?success=status_updated");
        assertThat(service.findById(request.getId()).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.DRAFT);
        assertThat(service.getStatusHistory(request.getId()))
                .anyMatch(h -> h.getNewStatus() == RequestStatus.DRAFT && REASON.equals(h.getNote()));
    }

    @Test
    @DisplayName("ผู้ยื่นเห็นเหตุผลที่ถูกส่งคืน และกลับมาแก้เอกสารที่ 1 ได้")
    void theApplicantSeesTheReasonAndCanEditAgain() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
        service.updateStatus(request.getId(), RequestStatus.DRAFT, data.admin(), REASON, false);

        AcademicRequest returned = service.findById(request.getId()).orElseThrow();
        assertThat(service.canApplicantEditDocument(returned, 1)).isTrue();

        String workspace = mvc.perform(get("/user/academic/new-request")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(workspace).contains(REASON);

        String dashboard = mvc.perform(get("/user/academic/dashboard")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(dashboard).contains(REASON);
    }

    @Test
    @DisplayName("ผู้ยื่นได้รับอีเมลแจ้งว่าถูกส่งคืน พร้อมเหตุผล")
    void theApplicantIsEmailedTheReason() {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        service.updateStatus(request.getId(), RequestStatus.DRAFT, data.admin(), REASON, true);

        awaitCondition("อีเมลส่งคืนถึงผู้ยื่นพร้อมเหตุผล",
                () -> mail().to(TestDataFactory.APPLICANT_EMAIL).stream()
                        .anyMatch(m -> m.bodyContains(REASON)));
    }

    @Test
    @DisplayName("ผ่านขั้นรับคำร้องไปแล้ว — ส่งคืนเป็นแบบร่างไม่ได้")
    void aRequestPastReceptionCannotBeSentBack() {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);

        assertThatThrownBy(() -> service.updateStatus(request.getId(), RequestStatus.DRAFT,
                data.admin(), REASON, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("หน้าแอดมินมีตัวเลือกส่งคืน และไม่มี 'ไม่รับคำร้อง'")
    void theOfficerIsOfferedTheReturnNotARefusal() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                .with(user(data.admin().getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("ส่งคืนให้ผู้ยื่นแก้ไข").doesNotContain("ไม่รับคำร้อง");
    }
}
