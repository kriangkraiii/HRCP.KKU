package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.repository.AcademicDocumentEditLogRepository;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.RequestStatusHistoryRepository;
import com.ecom.academic.repository.SignatureRequestRepository;

/**
 * ประตูแก้ไขเอกสารของผู้ยื่น
 *
 * <p>
 * เดิมผู้ยื่นแก้เอกสารที่ 0 และ 1 ได้ตลอดแม้ส่งคำร้องไปแล้ว ทั้งที่คู่มือและ
 * อีเมลของระบบบอกว่าเอกสารจะถูกล็อก กติกาใหม่คือหลังส่งคำร้องแล้วเปิดให้แก้ได้
 * ก็ต่อเมื่อแอดมินกด "ขอให้แก้ไขและลงนามใหม่" ส่งกลับมาเท่านั้น
 */
@DisplayName("ผู้ยื่นแก้ไขเอกสารได้เมื่อไหร่")
class ApplicantDocumentEditGateTest {

    private AcademicDocumentRepository documentRepository;
    private SignatureRequestRepository signatureRequestRepository;
    private AcademicRequestService service;

    private AcademicRequest request;

    @BeforeEach
    void setUp() {
        documentRepository = mock(AcademicDocumentRepository.class);
        signatureRequestRepository = mock(SignatureRequestRepository.class);

        service = new AcademicRequestService(
                mock(AcademicRequestRepository.class),
                documentRepository,
                mock(AcademicAttachmentRepository.class),
                mock(RequestStatusHistoryRepository.class),
                mock(AcademicDocumentEditLogRepository.class),
                mock(AcademicEmailService.class),
                mock(com.ecom.service.AfterCommitRunner.class),
                signatureRequestRepository,
                event -> {
                    /* การส่งคืนให้แก้ไขมีเทสต์ของตัวเองใน ReturnToDraftTest */
                });

        request = new AcademicRequest();
        request.setId(1L);
        request.setCurrentStatus(RequestStatus.RECEIVED);

        // ไม่มีซองลงนามค้าง เว้นแต่เทสต์จะกำหนดเอง
        when(signatureRequestRepository.findBlockingEnvelopes(any(SignatureModule.class), anyLong(), anyInt()))
                .thenReturn(List.of());
    }

    private AcademicDocument document(boolean revisionRequested) {
        AcademicDocument doc = new AcademicDocument();
        doc.setDocumentType(0);
        if (revisionRequested) {
            doc.setRevisionRequestedAt(java.time.LocalDateTime.now());
            doc.setRevisionNote("ข้อมูลในแบบฟอร์มไม่ถูกต้อง");
        }
        return doc;
    }

    @Test
    @DisplayName("ยังเป็นแบบร่าง: แก้ได้ตามปกติ")
    void draftRequest_isEditable() {
        request.setCurrentStatus(RequestStatus.DRAFT);

        assertThat(service.canApplicantEditDocument(request, 0)).isTrue();
    }

    @Test
    @DisplayName("ส่งคำร้องแล้วและแอดมินยังไม่ได้ส่งกลับ: แก้ไม่ได้")
    void submittedRequest_isLocked() {
        when(documentRepository.findByRequestIdAndDocumentType(1L, 0))
                .thenReturn(List.of(document(false)));

        assertThat(service.canApplicantEditDocument(request, 0)).isFalse();
    }

    @Test
    @DisplayName("แอดมินส่งกลับมาให้แก้: แก้ได้เฉพาะเอกสารฉบับนั้น")
    void sentBackByAdmin_isEditable() {
        when(documentRepository.findByRequestIdAndDocumentType(1L, 0))
                .thenReturn(List.of(document(true)));
        when(documentRepository.findByRequestIdAndDocumentType(1L, 1))
                .thenReturn(List.of(document(false)));

        assertThat(service.canApplicantEditDocument(request, 0)).isTrue();
        assertThat(service.canApplicantEditDocument(request, 1)).isFalse();
    }

    @Test
    @DisplayName("ส่งกลับแล้วแต่ผู้ยื่นลงนามใหม่ไปแล้ว: ล็อกกลับทันที")
    void sentBackButAlreadyResigned_isLockedAgain() {
        when(documentRepository.findByRequestIdAndDocumentType(1L, 0))
                .thenReturn(List.of(document(true)));
        when(signatureRequestRepository.findBlockingEnvelopes(SignatureModule.ACADEMIC, 1L, 0))
                .thenReturn(List.of(new SignatureRequest()));

        assertThat(service.canApplicantEditDocument(request, 0)).isFalse();
    }

    @Test
    @DisplayName("คำร้องจบกระบวนการแล้ว: ไม่เปิดให้แก้แม้เคยส่งกลับ")
    void closedRequest_isNeverEditable() {
        when(documentRepository.findByRequestIdAndDocumentType(1L, 0))
                .thenReturn(List.of(document(true)));

        for (RequestStatus closed : List.of(RequestStatus.COMPLETED, RequestStatus.COMPLETED_PASS,
                RequestStatus.COMPLETED_REVISE, RequestStatus.COMPLETED_FAIL)) {
            request.setCurrentStatus(closed);
            assertThat(service.canApplicantEditDocument(request, 0))
                    .as("status %s", closed)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("openDocumentForRevision บันทึกเวลาและเหตุผลลงเอกสารฉบับที่ระบุ")
    void openForRevision_stampsDocument() {
        AcademicDocument doc = document(false);
        when(documentRepository.findByRequestIdAndDocumentType(1L, 1)).thenReturn(List.of(doc));

        service.openDocumentForRevision(1L, 1, "  ข้อมูลไม่ครบถ้วน  ");

        assertThat(doc.getRevisionRequestedAt()).isNotNull();
        assertThat(doc.getRevisionNote()).isEqualTo("ข้อมูลไม่ครบถ้วน");
        assertThat(service.getRevisionNote(1L, 1)).isEqualTo("ข้อมูลไม่ครบถ้วน");
    }

    @Test
    @DisplayName("เหตุผลยาวเกิน 500 ตัวอักษรต้องถูกตัด ไม่ให้ชนขนาดคอลัมน์")
    void openForRevision_truncatesLongReason() {
        AcademicDocument doc = document(false);
        when(documentRepository.findByRequestIdAndDocumentType(eq(1L), eq(1))).thenReturn(List.of(doc));

        service.openDocumentForRevision(1L, 1, "ก".repeat(700));

        assertThat(doc.getRevisionNote()).hasSize(500);
    }
}
