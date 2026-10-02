package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/** เอกสารที่ 6: มีหรือไม่มีข้อเสนอแนะก็ดำเนินการต่อได้ แจ้งผู้ยื่นเพื่อทราบ ไม่ส่งกลับให้แก้ไข */
@DisplayName("เอกสารที่ 6 — ดำเนินการต่อโดยไม่ส่งกลับแก้ไข")
class Doc6ProceedTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @MockitoBean
    private AcademicEmailService emailService;

    private UserDtls officer;
    private UserDtls applicant;
    private AcademicRequest request;

    @BeforeEach
    void cast() {
        officer = data.admin();
        applicant = data.applicant();
        request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOfficer() {
        return user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", ""));
    }

    private String applicantPage() throws Exception {
        return mvc.perform(get("/user/academic/request/" + request.getId())
                .with(user(applicant.getEmail()).roles(applicant.getRole().replace("ROLE_", ""))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String doc6() {
        return "/admin/academic/request/" + request.getId() + "/document/6";
    }

    @Test
    @DisplayName("หน้าเอกสารมีปุ่มดำเนินการต่อคู่กับปุ่มส่งกลับแก้ไข")
    void theFormOffersBothChoices() throws Exception {
        String html = mvc.perform(get(doc6()).with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parse(html).getElementById("btnProceedDoc6")).isNotNull();
        assertThat(Jsoup.parse(html).getElementById("btnSendSuggestion")).isNotNull();
    }

    @Test
    @DisplayName("ไม่มีข้อเสนอแนะ: บันทึกว่าไม่มี ไปเอกสารที่ 7 และสถานะคำร้องไม่เปลี่ยน")
    void withoutSuggestionsItMovesOn() throws Exception {
        mvc.perform(post(doc6()).with(asOfficer()).with(csrf())
                .param("action", "submit")
                .param("suggestions_text", "  "))
                .andExpect(redirectedUrl("/admin/academic/request/" + request.getId() + "/document/7"));

        AcademicDocument saved = academicService.getDocumentsByType(request.getId(), 6).get(0);
        assertThat(saved.getIsDraft()).isNotEqualTo(Boolean.TRUE);
        assertThat(academicService.getLatestDocumentData(request.getId(), 6))
                .containsEntry("suggestions_text", "ไม่มีข้อเสนอแนะ");
        assertThat(academicService.findById(request.getId()).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.MEETING_SCHEDULED);
        verify(emailService).sendSuggestionNoticeEmail(request.getId(), "ไม่มีข้อเสนอแนะ");
        verify(emailService, never()).sendSuggestionEmail(eq(request.getId()), anyString());
        assertThat(Jsoup.parse(applicantPage()).getElementById("committeeSuggestions").text())
                .contains("ไม่มีข้อเสนอแนะ", "แจ้งเพื่อทราบ");
    }

    @Test
    @DisplayName("ร่างที่เจ้าหน้าที่กำลังพิมพ์ยังไม่ขึ้นให้ผู้ยื่นเห็น")
    void aDraftStaysHiddenFromTheApplicant() throws Exception {
        mvc.perform(post(doc6()).with(asOfficer()).with(csrf())
                .param("action", "draft")
                .param("suggestions_text", "ยังพิมพ์ไม่เสร็จ"))
                .andExpect(status().is3xxRedirection());

        assertThat(Jsoup.parse(applicantPage()).getElementById("committeeSuggestions")).isNull();
    }

    @Test
    @DisplayName("มีข้อเสนอแนะแต่ไม่ต้องแก้: เก็บข้อเสนอแนะไว้และสถานะคำร้องไม่เปลี่ยน")
    void withSuggestionsItKeepsThemAndMovesOn() throws Exception {
        mvc.perform(post(doc6()).with(asOfficer()).with(csrf())
                .param("action", "submit")
                .param("suggestions_text", "ควรเพิ่มตัวอย่างในบทที่ 2"))
                .andExpect(status().is3xxRedirection());

        assertThat(academicService.getLatestDocumentData(request.getId(), 6))
                .containsEntry("suggestions_text", "ควรเพิ่มตัวอย่างในบทที่ 2");
        assertThat(academicService.findById(request.getId()).orElseThrow().getCurrentStatus())
                .isEqualTo(RequestStatus.MEETING_SCHEDULED);
        verify(emailService).sendSuggestionNoticeEmail(request.getId(), "ควรเพิ่มตัวอย่างในบทที่ 2");
        assertThat(Jsoup.parse(applicantPage()).getElementById("committeeSuggestions").text())
                .contains("ควรเพิ่มตัวอย่างในบทที่ 2");
    }
}
