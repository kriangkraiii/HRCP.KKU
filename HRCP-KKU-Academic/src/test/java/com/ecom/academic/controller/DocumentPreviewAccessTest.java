package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.service.DocumentGenerationService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Access-control tests for academic document preview.
 *
 * Covers M-08 from the 2026-08-11 audit: APPLICANT_ALLOWED_DOCS was declared
 * but never enforced, so an applicant could render admin-only document types
 * (the teaching evaluation form, the meeting certificate) with data of their
 * own choosing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentPreviewAccessTest {

    @Mock
    private DocumentGenerationService documentService;

    @Mock
    private UserRepository userRepository;

    private DocumentPreviewController controller;

    private final Principal applicant = () -> "user@test.com";
    private final Principal admin = () -> "admin@test.com";

    @BeforeEach
    void setUp() throws Exception {
        controller = new DocumentPreviewController(documentService, userRepository);

        UserDtls user = new UserDtls();
        user.setId(1);
        user.setEmail("user@test.com");
        user.setRole("ROLE_USER");

        UserDtls adminUser = new UserDtls();
        adminUser.setId(2);
        adminUser.setEmail("admin@test.com");
        adminUser.setRole("ROLE_ADMIN");

        when(userRepository.findByEmail("user@test.com")).thenReturn(user);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(adminUser);
        when(documentService.generatePreviewDocx(anyInt(), anyString())).thenReturn(new byte[] { 1, 2 });
    }

    private Map<String, String> formData() {
        return new HashMap<>();
    }

    @Test
    @DisplayName("M-08: ผู้ยื่นต้อง preview เอกสารฝั่งแอดมิน (Doc 6) ไม่ได้")
    void applicant_cannotPreviewAdminOnlyDocument() throws Exception {
        ResponseEntity<byte[]> response = controller.previewDocument(6, "docx", formData(), applicant);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(documentService, never()).generatePreviewDocx(anyInt(), anyString());
    }

    @Test
    @DisplayName("M-08: ผู้ยื่นยัง preview เอกสารของตัวเอง (Doc 0) ได้")
    void applicant_canPreviewOwnDocument() throws Exception {
        ResponseEntity<byte[]> response = controller.previewDocument(0, "docx", formData(), applicant);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("M-08: แอดมิน preview ได้ทุกชนิดเอกสาร")
    void admin_canPreviewAnyDocument() throws Exception {
        ResponseEntity<byte[]> response = controller.previewDocument(6, "docx", formData(), admin);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("M-08: ผู้ที่ไม่ได้ล็อกอินต้องถูกปฏิเสธ")
    void anonymous_isRejected() throws Exception {
        ResponseEntity<byte[]> response = controller.previewDocument(0, "docx", formData(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }
}
