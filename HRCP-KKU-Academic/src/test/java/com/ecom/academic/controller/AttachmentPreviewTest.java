package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicRequest;
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

class AttachmentPreviewTest {

    @TempDir
    Path tempDir;

    private AcademicRequestService requestService;
    private DocumentGenerationService documentService;
    private UserRepository userRepository;
    private AcademicApplicantController applicantController;
    private AcademicAdminController adminController;

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
        applicantUser.setName("อาจารย์ผู้ยื่น");

        sampleRequest = new AcademicRequest();
        sampleRequest.setId(1L);
        sampleRequest.setApplicant(applicantUser);

        when(requestService.findById(1L)).thenReturn(Optional.of(sampleRequest));
        when(userRepository.findByEmail("applicant@kku.ac.th")).thenReturn(applicantUser);

        applicantController = new AcademicApplicantController(
                requestService,
                documentService,
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

        adminController = new AcademicAdminController(
                requestService,
                documentService,
                mock(StaffMemberService.class),
                userRepository,
                mock(AdminLogService.class),
                mock(PositionRequestService.class),
                mock(HttpServletRequest.class),
                mock(com.ecom.academic.service.DocumentDataAutoFillHelper.class),
                mock(com.ecom.academic.service.DocumentPrewarmService.class),
                mock(com.ecom.academic.service.SignatureWorkflowService.class),
                mock(com.ecom.academic.service.SignedDocumentRenderer.class)
        );
    }

    @Test
    @DisplayName("เปิดดูไฟล์แนบ DOCX จะต้องถูกแปลงเป็น PDF เพื่อแสดงผลแบบ inline บนเบราว์เซอร์")
    void viewDocxAttachment_shouldConvertToPdfInline() throws Exception {
        // สร้างไฟล์ DOCX จำลอง
        File docxFile = tempDir.resolve("test_doc.docx").toFile();
        try (FileOutputStream fos = new FileOutputStream(docxFile)) {
            fos.write("dummy docx content".getBytes());
        }

        AcademicAttachment attachment = new AcademicAttachment();
        attachment.setId(10L);
        attachment.setRequest(sampleRequest);
        attachment.setOriginalFilename("test_doc.docx");

        attachment.setStoredFilePath(docxFile.getAbsolutePath());

        when(requestService.findAttachmentById(10L)).thenReturn(Optional.of(attachment));

        byte[] fakePdfBytes = "%PDF-1.4 Fake PDF Content".getBytes();
        when(documentService.convertDocxToPdfCached(any())).thenReturn(fakePdfBytes);

        Principal principal = () -> "applicant@kku.ac.th";

        // When
        ResponseEntity<Resource> response = applicantController.viewAttachment(1L, 10L, principal);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("inline");
        assertThat(disposition).contains("test_doc.pdf");
    }

    @Test
    @DisplayName("เปิดดูไฟล์แนบ PDF จะส่งกลับเป็น application/pdf แบบ inline")
    void viewPdfAttachment_shouldReturnPdfInline() throws Exception {
        File pdfFile = tempDir.resolve("report.pdf").toFile();
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            fos.write("%PDF-1.4 sample content".getBytes());
        }

        AcademicAttachment attachment = new AcademicAttachment();
        attachment.setId(11L);
        attachment.setRequest(sampleRequest);
        attachment.setOriginalFilename("report.pdf");

        attachment.setStoredFilePath(pdfFile.getAbsolutePath());

        when(requestService.findAttachmentById(11L)).thenReturn(Optional.of(attachment));

        Principal principal = () -> "applicant@kku.ac.th";

        ResponseEntity<Resource> response = applicantController.viewAttachment(1L, 11L, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("inline");
        assertThat(disposition).contains("report.pdf");
    }

    @Test
    @DisplayName("ดาวน์โหลดไฟล์แนบ DOCX จะต้องส่งกลับไฟล์ DOCX ดั้งเดิมแบบ attachment")
    void downloadDocxAttachment_shouldReturnOriginalDocx() throws Exception {
        File docxFile = tempDir.resolve("original.docx").toFile();
        try (FileOutputStream fos = new FileOutputStream(docxFile)) {
            fos.write("raw docx bytes".getBytes());
        }

        AcademicAttachment attachment = new AcademicAttachment();
        attachment.setId(12L);
        attachment.setRequest(sampleRequest);
        attachment.setOriginalFilename("original.docx");

        attachment.setStoredFilePath(docxFile.getAbsolutePath());

        when(requestService.findAttachmentById(12L)).thenReturn(Optional.of(attachment));

        Principal principal = () -> "applicant@kku.ac.th";

        ResponseEntity<Resource> response = applicantController.downloadAttachment(1L, 12L, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("original.docx");
    }

    @Test
    @DisplayName("แอดมินเปิดดูไฟล์แนบ DOCX จะแปลงเป็น PDF inline")
    void adminViewDocxAttachment_shouldConvertToPdfInline() throws Exception {
        File docxFile = tempDir.resolve("admin_doc.docx").toFile();
        try (FileOutputStream fos = new FileOutputStream(docxFile)) {
            fos.write("dummy docx content".getBytes());
        }

        AcademicAttachment attachment = new AcademicAttachment();
        attachment.setId(13L);
        attachment.setRequest(sampleRequest);
        attachment.setOriginalFilename("admin_doc.docx");

        attachment.setStoredFilePath(docxFile.getAbsolutePath());

        when(requestService.findAttachmentById(13L)).thenReturn(Optional.of(attachment));

        byte[] fakePdfBytes = "%PDF-1.4 Fake Admin PDF".getBytes();
        when(documentService.convertDocxToPdfCached(any())).thenReturn(fakePdfBytes);

        ResponseEntity<Resource> response = adminController.viewAttachment(1L, 13L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).contains("inline");
        assertThat(disposition).contains("admin_doc.pdf");
    }
}
