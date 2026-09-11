package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

class AttachmentConstraintsTest {

    @TempDir
    Path tempDir;

    private AcademicRequestService requestService;
    private DocumentGenerationService documentService;
    private UserRepository userRepository;
    private AcademicApplicantController applicantController;

    private UserDtls applicantUser;
    private AcademicRequest sampleRequest;

    @BeforeEach
    void setUp() {
        requestService = mock(AcademicRequestService.class);
        documentService = mock(DocumentGenerationService.class);
        userRepository = mock(UserRepository.class);

        applicantUser = new UserDtls();
        applicantUser.setId(100);
        applicantUser.setEmail("applicant@kku.ac.th");

        sampleRequest = new AcademicRequest();
        sampleRequest.setId(1L);
        sampleRequest.setApplicant(applicantUser);
        sampleRequest.setCurrentStatus(RequestStatus.DRAFT);

        when(requestService.findById(1L)).thenReturn(Optional.of(sampleRequest));
        when(userRepository.findByEmail("applicant@kku.ac.th")).thenReturn(applicantUser);
        when(requestService.canApplicantEditDocument(any(AcademicRequest.class), any(Integer.class))).thenReturn(true);

        applicantController = new AcademicApplicantController(
                requestService,
                documentService,
                mock(StaffMemberService.class),
                userRepository,
                mock(AcademicEmailService.class),
                mock(PositionRequestService.class),
                mock(AdminLogService.class),
                mock(HttpServletRequest.class),
                mock(com.ecom.util.DocumentFileTypeValidator.class),
                mock(com.ecom.academic.service.DocumentDataAutoFillHelper.class),
                mock(com.ecom.academic.service.DocumentPrewarmService.class),
                mock(com.ecom.academic.service.SignatureWorkflowService.class),
                mock(com.ecom.academic.service.SignedDocumentRenderer.class),
                mock(com.ecom.external.service.KkuDocumentSyncService.class)
        );
    }

    @Test
    @DisplayName("อนุญาตให้อัปโหลดไฟล์ PDF, DOCX, DOC, ZIP")
    void uploadAllowedFileTypes_shouldSucceed() throws Exception {
        when(requestService.getSlotTotalSize(1L, 1)).thenReturn(0L);

        MockMultipartFile filePdf = new MockMultipartFile("files", "test.pdf", "application/pdf", "pdf content".getBytes());
        MockMultipartFile fileDocx = new MockMultipartFile("files", "test.docx", "application/vnd.openxmlformats", "docx content".getBytes());
        MockMultipartFile fileDoc = new MockMultipartFile("files", "test.doc", "application/msword", "doc content".getBytes());
        MockMultipartFile fileZip = new MockMultipartFile("files", "test.zip", "application/zip", "zip content".getBytes());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument2Attachments(
                1L,
                1,
                new MultipartFile[]{filePdf, fileDocx, fileDoc, fileZip},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat(redirectAttributes.getFlashAttributes().get("success")).isNotNull();
        verify(requestService, org.mockito.Mockito.times(4)).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ปฏิเสธไฟล์ประเภทที่ไม่รองรับ เช่น XLSX, PPTX, PNG, JPG, RAR")
    void uploadDisallowedFileTypes_shouldBeRejected() throws Exception {
        when(requestService.getSlotTotalSize(1L, 1)).thenReturn(0L);

        MockMultipartFile fileXlsx = new MockMultipartFile("files", "data.xlsx", "application/vnd.ms-excel", "xlsx".getBytes());
        MockMultipartFile filePng = new MockMultipartFile("files", "image.png", "image/png", "png".getBytes());
        MockMultipartFile fileRar = new MockMultipartFile("files", "archive.rar", "application/x-rar", "rar".getBytes());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument2Attachments(
                1L,
                1,
                new MultipartFile[]{fileXlsx, filePng, fileRar},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat(redirectAttributes.getFlashAttributes().get("error")).isNotNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("ประเภทไฟล์ที่ไม่รองรับ");
        verify(requestService, never()).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ปฏิเสธไฟล์เมื่อขนาดไฟล์แนบรวมของคำร้องเกิน 75 MB")
    void uploadExceeding75MBTotal_shouldBeRejected() throws Exception {
        // มีไฟล์อยู่แล้ว 70 MB (73,400,320 bytes)
        long current70MB = 70L * 1024L * 1024L;
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(current70MB);

        // จะอัปโหลดเพิ่ม 10 MB (เกินขีดจำกัด 75 MB รวม)
        byte[] tenMBBytes = new byte[10 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("files", "extra_large.pdf", "application/pdf", tenMBBytes);

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument2Attachments(
                1L,
                1,
                new MultipartFile[]{largeFile},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat(redirectAttributes.getFlashAttributes().get("error")).isNotNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("75 MB");
        verify(requestService, never()).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ส่งคำร้องแล้วและแอดมินยังไม่ได้ส่งกลับ: อัปโหลดไฟล์แนบไม่ได้")
    void uploadAfterSubmit_withoutAdminSendBack_shouldBeRejected() throws Exception {
        sampleRequest.setCurrentStatus(RequestStatus.RECEIVED);
        when(requestService.canApplicantEditDocument(any(AcademicRequest.class), eq(2))).thenReturn(false);
        when(requestService.canApplicantEditDocument(any(AcademicRequest.class), eq(1))).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("files", "test.pdf", "application/pdf", "pdf content".getBytes());
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument2Attachments(
                1L,
                1,
                new MultipartFile[]{file},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("แอดมิน");
        verify(requestService, never()).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ส่งคำร้องแล้วและแอดมินยังไม่ได้ส่งกลับ: ลบไฟล์แนบไม่ได้")
    void deleteAfterSubmit_withoutAdminSendBack_shouldBeRejected() {
        sampleRequest.setCurrentStatus(RequestStatus.RECEIVED);
        when(requestService.canApplicantEditDocument(any(AcademicRequest.class), eq(2))).thenReturn(false);
        when(requestService.canApplicantEditDocument(any(AcademicRequest.class), eq(1))).thenReturn(false);

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.deleteAttachment(1L, 5L, principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("แอดมิน");
        verify(requestService, never()).deleteAttachment(any(Long.class));
    }

    @Test
    @DisplayName("บันทึกเอกสารที่ 2 (submit) เมื่อยังไม่มีไฟล์แนบเลย จะถูกปฏิเสธ")
    void submitDoc2_missingSlots_shouldBeRejected() throws Exception {
        when(requestService.countActiveAttachments(1L)).thenReturn(0L);

        Map<String, String> formData = new java.util.HashMap<>();
        formData.put("action", "submit");
        formData.put("title", "ผศ.ดร.");
        formData.put("applicant_name", "สมชาย ใจดี");

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.submitDocument2(1L, formData, "submit", principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2?error=no_attachments");
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("อย่างน้อย 1 รายการ");
    }

    @Test
    @DisplayName("บันทึกเอกสารที่ 2 (submit) เมื่อมีไฟล์แนบและขนาดไม่เกิน 75 MB จะสำเร็จ")
    void submitDoc2_all5SlotsPresent_shouldSucceed() throws Exception {
        when(requestService.countActiveAttachments(1L)).thenReturn(1L);
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(10L * 1024L * 1024L);
        when(documentService.generateDocument(eq(1L), eq(2), anyString(), any())).thenReturn("generated/doc2.docx");

        Map<String, String> formData = new java.util.HashMap<>();
        formData.put("action", "submit");
        formData.put("title", "ผศ.ดร.");
        formData.put("applicant_name", "สมชาย ใจดี");

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.submitDocument2(1L, formData, "submit", principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1?success=doc2_submitted");
        verify(requestService).saveDocument(eq(sampleRequest), eq(2), anyString(), eq("generated/doc2.docx"), anyString(), any());
    }

    @Test
    @DisplayName("บันทึกเอกสารที่ 2 (submit) เมื่อขนาดไฟล์แนบรวมเกิน 75 MB จะถูกปฏิเสธ")
    void submitDoc2_slotOver75MB_shouldBeRejected() throws Exception {
        when(requestService.countActiveAttachments(1L)).thenReturn(1L);
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(76L * 1024L * 1024L); // 76 MB > 75 MB

        Map<String, String> formData = new java.util.HashMap<>();
        formData.put("action", "submit");
        formData.put("title", "ผศ.ดร.");
        formData.put("applicant_name", "สมชาย ใจดี");

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.submitDocument2(1L, formData, "submit", principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("เกินขีดจำกัด 75 MB");
        verify(requestService, never()).saveDocument(any(), eq(2), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("แนบลิงก์ URL ในช่องที่ 3 สำเร็จและบันทึกประเภท LINK")
    void attachDocument2Link_validUrl_shouldSucceed() {
        org.mockito.ArgumentCaptor<AcademicAttachment> captor = org.mockito.ArgumentCaptor.forClass(AcademicAttachment.class);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.attachDocument2Link(
                1L, 3, "https://drive.google.com/file/d/123/view", "บันทึกการสอน Google Drive",
                principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("success")).contains("เรียบร้อยแล้ว");
        verify(requestService).saveAttachment(captor.capture());

        AcademicAttachment saved = captor.getValue();
        assertThat(saved.getFileType()).isEqualTo("LINK");
        assertThat(saved.getFileSize()).isEqualTo(0L);
        assertThat(saved.getChecklistItem()).isEqualTo(3);
        assertThat(saved.getOriginalFilename()).isEqualTo("บันทึกการสอน Google Drive");
        assertThat(saved.getStoredFilePath()).isEqualTo("https://drive.google.com/file/d/123/view");
    }

    @Test
    @DisplayName("แนบลิงก์ URL ที่ไม่ถูกต้อง (ไม่ใช่ http/https) จะถูกปฏิเสธ")
    void attachDocument2Link_invalidUrl_shouldBeRejected() {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.attachDocument2Link(
                1L, 1, "javascript:alert('xss')", "Invalid Link",
                principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("รูปแบบ URL ไม่ถูกต้อง");
        verify(requestService, never()).saveAttachment(any());
    }

    @Test
    @DisplayName("การยื่นเอกสารที่ 2 โดยมีลิงก์แนบ นับเป็นรายการที่ผ่านเกณฑ์")
    void submitDoc2_withLinkAttachments_shouldSucceed() throws Exception {
        when(requestService.countActiveAttachments(1L)).thenReturn(2L);
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(1024L * 1024L);
        when(documentService.generateDocument(eq(1L), eq(2), anyString(), any())).thenReturn("generated/doc2.docx");

        Map<String, String> formData = new java.util.HashMap<>();
        formData.put("action", "submit");
        formData.put("title", "ผศ.ดร.");
        formData.put("applicant_name", "สมชาย ใจดี");

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.submitDocument2(1L, formData, "submit", principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1?success=doc2_submitted");
        verify(requestService).saveDocument(eq(sampleRequest), eq(2), anyString(), eq("generated/doc2.docx"), anyString(), any());
    }

    @Test
    @DisplayName("ลบเอกสารแนบประเภท LINK ลบสำเร็จและแสดงข้อความ ลบลิงก์เรียบร้อยแล้ว โดยไม่เกิดข้อผิดพลาด")
    void deleteAttachment_linkAttachment_shouldSucceed() {
        AcademicAttachment linkAtt = new AcademicAttachment();
        linkAtt.setId(99L);
        linkAtt.setRequest(sampleRequest);
        linkAtt.setFileType("LINK");
        linkAtt.setStoredFilePath("https://drive.google.com/view/123");
        when(requestService.findAttachmentById(99L)).thenReturn(Optional.of(linkAtt));

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.deleteAttachment(1L, 99L, principal, redirectAttributes);

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-2");
        assertThat((String) redirectAttributes.getFlashAttributes().get("success")).isEqualTo("ลบลิงก์เรียบร้อยแล้ว");
        verify(requestService).deleteAttachment(99L);
    }

    @Test
    @DisplayName("เปิดดู/ดาวน์โหลดเอกสารแนบประเภท LINK จะ redirect (302) ไปยัง URL ปลายทางโดยตรง")
    void viewAndDownloadAttachment_linkType_shouldRedirectToExternalUrl() throws Exception {
        AcademicAttachment linkAtt = new AcademicAttachment();
        linkAtt.setId(88L);
        linkAtt.setRequest(sampleRequest);
        linkAtt.setFileType("LINK");
        linkAtt.setStoredFilePath("https://onedrive.live.com/test-file");
        when(requestService.findAttachmentById(88L)).thenReturn(Optional.of(linkAtt));

        Principal principal = () -> "applicant@kku.ac.th";

        var downloadResp = applicantController.downloadAttachment(1L, 88L, principal);
        assertThat(downloadResp.getStatusCode().value()).isEqualTo(302);
        assertThat(downloadResp.getHeaders().getLocation().toString()).isEqualTo("https://onedrive.live.com/test-file");

        var viewResp = applicantController.viewAttachment(1L, 88L, principal);
        assertThat(viewResp.getStatusCode().value()).isEqualTo(302);
        assertThat(viewResp.getHeaders().getLocation().toString()).isEqualTo("https://onedrive.live.com/test-file");
    }
}
