package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.security.Principal;
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
import com.ecom.academic.service.UserStorageService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.servlet.http.HttpServletRequest;

class AttachmentConstraintsTest {

    @TempDir
    Path tempDir;

    private AcademicRequestService requestService;
    private UserRepository userRepository;
    private AcademicApplicantController applicantController;

    private UserDtls applicantUser;
    private AcademicRequest sampleRequest;

    @BeforeEach
    void setUp() {
        requestService = mock(AcademicRequestService.class);
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

        applicantController = new AcademicApplicantController(
                requestService,
                mock(DocumentGenerationService.class),
                mock(StaffMemberService.class),
                userRepository,
                mock(AcademicEmailService.class),
                mock(PositionRequestService.class),
                mock(AdminLogService.class),
                mock(HttpServletRequest.class),
                mock(UserStorageService.class),
                mock(com.ecom.academic.service.DocumentDataAutoFillHelper.class),
                mock(com.ecom.academic.service.DocumentPrewarmService.class),
                mock(com.ecom.academic.service.SignatureWorkflowService.class),
                mock(com.ecom.academic.service.SignedDocumentRenderer.class)
        );
    }

    @Test
    @DisplayName("อนุญาตให้อัปโหลดไฟล์ PDF, DOCX, DOC, ZIP")
    void uploadAllowedFileTypes_shouldSucceed() throws Exception {
        when(requestService.countAttachments(1L)).thenReturn(0L);
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(0L);

        MockMultipartFile filePdf = new MockMultipartFile("files", "test.pdf", "application/pdf", "pdf content".getBytes());
        MockMultipartFile fileDocx = new MockMultipartFile("files", "test.docx", "application/vnd.openxmlformats", "docx content".getBytes());
        MockMultipartFile fileDoc = new MockMultipartFile("files", "test.doc", "application/msword", "doc content".getBytes());
        MockMultipartFile fileZip = new MockMultipartFile("files", "test.zip", "application/zip", "zip content".getBytes());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument1Attachments(
                1L,
                new MultipartFile[]{filePdf, fileDocx, fileDoc, fileZip},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-1");
        assertThat(redirectAttributes.getFlashAttributes().get("success")).isNotNull();
        verify(requestService, org.mockito.Mockito.times(4)).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ปฏิเสธไฟล์ประเภทที่ไม่รองรับ เช่น XLSX, PPTX, PNG, JPG, RAR")
    void uploadDisallowedFileTypes_shouldBeRejected() throws Exception {
        when(requestService.countAttachments(1L)).thenReturn(0L);
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(0L);

        MockMultipartFile fileXlsx = new MockMultipartFile("files", "data.xlsx", "application/vnd.ms-excel", "xlsx".getBytes());
        MockMultipartFile filePng = new MockMultipartFile("files", "image.png", "image/png", "png".getBytes());
        MockMultipartFile fileRar = new MockMultipartFile("files", "archive.rar", "application/x-rar", "rar".getBytes());

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument1Attachments(
                1L,
                new MultipartFile[]{fileXlsx, filePng, fileRar},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-1");
        assertThat(redirectAttributes.getFlashAttributes().get("error")).isNotNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("ประเภทไฟล์ที่ไม่รองรับ");
        verify(requestService, never()).saveAttachment(any(AcademicAttachment.class));
    }

    @Test
    @DisplayName("ปฏิเสธไฟล์เมื่อขนาดไฟล์แนบรวมของคำร้องเกิน 75 MB")
    void uploadExceeding75MBTotal_shouldBeRejected() throws Exception {
        when(requestService.countAttachments(1L)).thenReturn(1L);
        // มีไฟล์อยู่แล้ว 70 MB (73,400,320 bytes)
        long current70MB = 70L * 1024L * 1024L;
        when(requestService.getTotalAttachmentSize(1L)).thenReturn(current70MB);

        // จะอัปโหลดเพิ่ม 10 MB (เกินขีดจำกัด 75 MB รวม)
        byte[] tenMBBytes = new byte[10 * 1024 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("files", "extra_large.pdf", "application/pdf", tenMBBytes);

        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Principal principal = () -> "applicant@kku.ac.th";

        String result = applicantController.uploadDocument1Attachments(
                1L,
                new MultipartFile[]{largeFile},
                principal,
                redirectAttributes
        );

        assertThat(result).isEqualTo("redirect:/user/academic/request/1/document-1");
        assertThat(redirectAttributes.getFlashAttributes().get("error")).isNotNull();
        assertThat((String) redirectAttributes.getFlashAttributes().get("error")).contains("เกิน 75 MB");
        verify(requestService, never()).saveAttachment(any(AcademicAttachment.class));
    }
}
