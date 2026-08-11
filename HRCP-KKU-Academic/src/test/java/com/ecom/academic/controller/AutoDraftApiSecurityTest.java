package com.ecom.academic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Security regression tests for the auto-draft API.
 *
 * Covers H-02 from the 2026-08-11 audit: the endpoints loaded a request by id
 * and saved attacker-supplied JSON into it without ever checking that the
 * caller owned that request.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoDraftApiSecurityTest {

    @Mock
    private AcademicRequestService academicService;

    @Mock
    private PositionRequestService positionService;

    @Mock
    private UserRepository userRepository;

    private AutoDraftApiController controller;

    private final Principal attackerPrincipal = () -> "attacker@test.com";
    private final Principal ownerPrincipal = () -> "owner@test.com";
    private final Principal adminPrincipal = () -> "admin@test.com";

    private UserDtls owner;

    @BeforeEach
    void setUp() {
        controller = new AutoDraftApiController(academicService, positionService, userRepository);

        UserDtls attacker = new UserDtls();
        attacker.setId(1);
        attacker.setEmail("attacker@test.com");
        attacker.setRole("ROLE_USER");

        owner = new UserDtls();
        owner.setId(2);
        owner.setEmail("owner@test.com");
        owner.setRole("ROLE_USER");

        UserDtls admin = new UserDtls();
        admin.setId(3);
        admin.setEmail("admin@test.com");
        admin.setRole("ROLE_ADMIN");

        when(userRepository.findByEmail("attacker@test.com")).thenReturn(attacker);
        when(userRepository.findByEmail("owner@test.com")).thenReturn(owner);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(admin);

        AcademicRequest academicRequest = new AcademicRequest();
        academicRequest.setId(99L);
        academicRequest.setApplicant(owner);
        when(academicService.findById(99L)).thenReturn(Optional.of(academicRequest));

        PositionRequest positionRequest = new PositionRequest();
        positionRequest.setId(77L);
        positionRequest.setApplicant(owner);
        when(positionService.findById(77L)).thenReturn(Optional.of(positionRequest));
        when(positionService.getDocLabel(anyInt())).thenReturn("เอกสาร");
    }

    // ==================== H-02: academic ====================

    @Test
    @DisplayName("H-02: ผู้ใช้อื่นต้องเขียนร่างเอกสารวิชาการของเจ้าของไม่ได้")
    void academicDraft_byNonOwner_isForbidden() {
        ResponseEntity<?> response = controller.academicDraft(99L, 0, "{\"x\":\"1\"}", attackerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(academicService, never()).saveDraft(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("H-02: เจ้าของคำร้องยังบันทึกร่างของตัวเองได้")
    void academicDraft_byOwner_isAllowed() {
        ResponseEntity<?> response = controller.academicDraft(99L, 0, "{\"x\":\"1\"}", ownerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(academicService).saveDraft(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("H-02: แอดมินยังบันทึกร่างของคำร้องใดก็ได้")
    void academicDraft_byAdmin_isAllowed() {
        ResponseEntity<?> response = controller.academicDraft(99L, 0, "{\"x\":\"1\"}", adminPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // ==================== H-02: position ====================

    @Test
    @DisplayName("H-02: ผู้ใช้อื่นต้องเขียนร่างเอกสารตำแหน่งของเจ้าของไม่ได้")
    void positionDraft_byNonOwner_isForbidden() {
        ResponseEntity<?> response = controller.positionDraft(77L, 1, "{\"x\":\"1\"}", attackerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(positionService, never()).saveDraft(any(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("H-02: เจ้าของคำร้องตำแหน่งยังบันทึกร่างของตัวเองได้")
    void positionDraft_byOwner_isAllowed() {
        ResponseEntity<?> response = controller.positionDraft(77L, 1, "{\"x\":\"1\"}", ownerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(positionService).saveDraft(any(), anyInt(), anyString(), anyString(), anyString());
    }

    // ==================== M-05 / M-06 ====================

    @Test
    @DisplayName("M-05/M-06: ข้อผิดพลาดภายในต้องไม่ส่งข้อความ exception กลับไปหา client")
    void internalError_doesNotLeakExceptionMessage() {
        when(academicService.findById(99L)).thenThrow(new RuntimeException("jdbc://secret-host/db exploded"));

        ResponseEntity<?> response = controller.academicDraft(99L, 0, "{}", ownerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(String.valueOf(response.getBody())).doesNotContain("secret-host");
    }

    @Test
    @DisplayName("M-06: exception ที่ getMessage() เป็น null ต้องไม่ทำให้เกิด NPE ซ้อน")
    void internalErrorWithNullMessage_doesNotThrow() {
        when(academicService.findById(99L)).thenThrow(new NullPointerException());

        ResponseEntity<?> response = controller.academicDraft(99L, 0, "{}", ownerPrincipal);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
