package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ยื่นโหลดเอกสารได้เฉพาะ PDF — ทั้งปุ่มบนหน้าจอ และการยิง URL ตรง
 */
@DisplayName("ผู้ยื่นได้เฉพาะไฟล์ PDF แอดมินยังได้ Word")
class PdfOnlyForApplicantsTest extends AbstractFlowTest {

    private static final String DOC2 = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดี\"}";

    @Autowired
    private DocumentGenerationService documentService;

    private UserDtls applicant;
    private PositionRequest draft;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        data.positionDocument(draft, 2, DOC2);
    }

    @Test
    @DisplayName("ผู้ยื่นขอ Word จากหน้าดาวน์โหลด แม้คำร้องยังเป็นแบบร่าง ก็ได้ PDF")
    void applicantDownloadIsAlwaysPdf() throws Exception {
        Assumptions.assumeTrue(documentService.isPdfConversionAvailable(), "ต้องมี LibreOffice");

        MockHttpServletResponse response = mvc.perform(
                        get("/user/position/request/" + draft.getId() + "/document/2/download?format=docx")
                                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getContentType()).startsWith("application/pdf");
    }

    @Test
    @DisplayName("API ดูตัวอย่าง: ผู้ยื่นขอ docx ได้ PDF ส่วนแอดมินยังได้ docx")
    void previewApiGivesApplicantsPdfOnly() throws Exception {
        Assumptions.assumeTrue(documentService.isPdfConversionAvailable(), "ต้องมี LibreOffice");

        MockHttpServletResponse asApplicant = mvc.perform(post("/api/position/preview/2?format=docx")
                        .with(user(applicant.getEmail()).roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC2))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(asApplicant.getContentType()).startsWith("application/pdf");

        MockHttpServletResponse asAdmin = mvc.perform(post("/api/position/preview/2?format=docx")
                        .with(user(data.admin().getEmail()).roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC2))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(asAdmin.getHeader(PreviewResponseFactory.FORMAT_HEADER)).isEqualTo("docx");
    }

    @Test
    @DisplayName("หน้าของผู้ยื่นบอกให้หน้าต่างดูตัวอย่างซ่อนปุ่ม Word และไม่มีลิงก์ DOCX")
    void applicantPagesCarryThePdfOnlyFlag() throws Exception {
        String html = mvc.perform(get("/user/position/request/" + draft.getId())
                        .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-pdf-only=\"true\"").doesNotContain("format=docx");
    }
}
