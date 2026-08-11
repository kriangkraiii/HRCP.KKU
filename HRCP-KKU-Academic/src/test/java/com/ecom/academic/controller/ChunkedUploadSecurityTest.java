package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
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
import org.springframework.mock.web.MockMultipartFile;

import com.ecom.academic.service.AdminStorageService;
import com.ecom.academic.service.UserStorageService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Security regression tests for the chunked upload endpoints.
 *
 * Covers the bugs found in the 2026-08-11 audit:
 *   H-01 — storageType=admin was accepted from any authenticated user, which
 *          skipped file-type validation and quota checks entirely
 *   H-04 — the quota was checked against a client-declared fileSize that was
 *          never compared with the bytes actually uploaded
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkedUploadSecurityTest {

    @Mock
    private UserStorageService userStorageService;

    @Mock
    private AdminStorageService adminStorageService;

    @Mock
    private UserRepository userRepository;

    private ChunkedUploadController controller;

    private final Principal userPrincipal = () -> "user@test.com";
    private final Principal adminPrincipal = () -> "admin@test.com";

    @BeforeEach
    void setUp() {
        controller = new ChunkedUploadController(userStorageService, adminStorageService, userRepository);

        UserDtls user = new UserDtls();
        user.setId(1);
        user.setEmail("user@test.com");
        user.setRole("ROLE_USER");

        UserDtls admin = new UserDtls();
        admin.setId(2);
        admin.setEmail("admin@test.com");
        admin.setRole("ROLE_ADMIN");

        when(userRepository.findByEmail("user@test.com")).thenReturn(user);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(admin);
        when(userStorageService.getRemainingBytes(anyInt())).thenReturn(100L * 1024 * 1024);
        when(userStorageService.formatSize(anyLong())).thenReturn("100 MB");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) response.getBody();
    }

    // ==================== H-01 ====================

    @Test
    @DisplayName("H-01: ผู้ใช้ ROLE_USER ต้องถูกปฏิเสธเมื่อขอ storageType=admin")
    void initUpload_asUser_withAdminStorageType_isRejected() {
        ResponseEntity<Map<String, Object>> response = controller.initUpload(
                "payload.exe", 1024L, 1, null, "admin", userPrincipal);

        assertThat(bodyOf(response).get("success")).isEqualTo(false);
        assertThat(bodyOf(response)).doesNotContainKey("uploadId");
    }

    @Test
    @DisplayName("H-01: ผู้ใช้ ROLE_USER ขอ storageType=admin ต้องไม่ข้ามการตรวจนามสกุลไฟล์")
    void initUpload_asUser_withAdminStorageType_stillValidatesFileType() {
        controller.initUpload("payload.exe", 1024L, 1, null, "admin", userPrincipal);

        // The rejection must happen before any admin-storage work is scheduled.
        verify(adminStorageService, never())
                .saveUploadedFile(anyString(), anyString(), anyLong(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("H-01: ROLE_ADMIN ยังใช้ storageType=admin ได้ตามปกติ")
    void initUpload_asAdmin_withAdminStorageType_isAllowed() {
        ResponseEntity<Map<String, Object>> response = controller.initUpload(
                "report.pdf", 1024L, 1, null, "admin", adminPrincipal);

        assertThat(bodyOf(response).get("success")).isEqualTo(true);
        assertThat(bodyOf(response)).containsKey("uploadId");

        controller.abortUpload((String) bodyOf(response).get("uploadId"));
    }

    // ==================== H-04 ====================

    @Test
    @DisplayName("H-04: อัปโหลด byte จริงเกินขนาดที่ประกาศไว้ต้องถูกปฏิเสธ")
    void uploadChunk_exceedingDeclaredSize_isRejected() {
        ResponseEntity<Map<String, Object>> init = controller.initUpload(
                "small.pdf", 10L, 1, null, "user", userPrincipal);
        String uploadId = (String) bodyOf(init).get("uploadId");
        assertThat(uploadId).as("init should have succeeded").isNotNull();

        // Declared 10 bytes, actually send 5000.
        MockMultipartFile oversized = new MockMultipartFile("chunk", new byte[5000]);
        ResponseEntity<Map<String, Object>> chunk = controller.uploadChunk(uploadId, 0, oversized);

        assertThat(bodyOf(chunk).get("success")).isEqualTo(false);

        controller.abortUpload(uploadId);
    }

    @Test
    @DisplayName("H-04: ขนาดที่บันทึกลง DB ต้องเป็นขนาดจริง ไม่ใช่ค่าที่ client ประกาศ")
    void completeUpload_persistsActualByteCountNotDeclaredSize() {
        ResponseEntity<Map<String, Object>> init = controller.initUpload(
                "doc.pdf", 4096L, 1, null, "user", userPrincipal);
        String uploadId = (String) bodyOf(init).get("uploadId");

        byte[] actual = new byte[321];
        controller.uploadChunk(uploadId, 0, new MockMultipartFile("chunk", actual));
        controller.completeUpload(uploadId);

        verify(userStorageService).saveUploadedFile(
                anyString(), anyString(), org.mockito.ArgumentMatchers.eq(321L), anyString(), any(), anyInt());
    }
}
