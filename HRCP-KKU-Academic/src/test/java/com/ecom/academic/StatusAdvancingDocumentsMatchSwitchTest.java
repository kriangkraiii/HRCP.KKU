package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * รายชื่อเอกสารที่เลื่อนสถานะ ต้องตรงกับ switch จริง
 *
 * <p>รายชื่อนี้เคยถูกคัดลอกไว้ทั้งในเซอร์วิสและใน JavaScript แล้วเพี้ยนกันมาสองรอบตอนทีม
 * เลื่อนเลขเอกสารทั้งชุด ตอนนี้หน้าเว็บอ่านจากค่าคงที่ตัวเดียว เทสต์นี้จึงเฝ้าอีกด้าน:
 * เอกสารที่ <em>ไม่</em> อยู่ในรายชื่อต้องไม่ขยับสถานะจริง ๆ
 *
 * <p>ถ้าใครเพิ่ม {@code case} ลงใน switch โดยลืมเพิ่มในค่าคงที่ เทสต์นี้จะพัง
 */
@DisplayName("รายชื่อเอกสารที่เลื่อนสถานะตรงกับ switch")
class StatusAdvancingDocumentsMatchSwitchTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    @Test
    @DisplayName("เฟส 1 — เอกสารนอกรายชื่อไม่ขยับสถานะ")
    void phase1DocumentsOutsideTheSetDoNothing() {
        for (int type = 1; type <= 9; type++) {
            if (AcademicRequestService.STATUS_ADVANCING_DOCUMENTS.contains(type)) {
                continue;
            }
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            academicService.autoUpdateStatusByDocument(request.getId(), type, officer, "{}", false);

            assertThat(academicService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("เอกสารเฟส 1 ที่ %d ไม่อยู่ในรายชื่อ จึงต้องไม่เลื่อนสถานะ", type)
                    .isEqualTo(RequestStatus.RECEIVED);
        }
    }

    @Test
    @DisplayName("เฟส 2 — เอกสารนอกรายชื่อไม่ขยับสถานะ")
    void phase2DocumentsOutsideTheSetDoNothing() {
        for (int type = 1; type <= 9; type++) {
            if (PositionRequestService.STATUS_ADVANCING_DOCUMENTS.contains(type)) {
                continue;
            }
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

            positionService.autoUpdateStatusByDocument(request.getId(), type, officer, "{}", false);

            assertThat(positionService.findById(request.getId()).orElseThrow().getCurrentStatus())
                    .as("เอกสารเฟส 2 ที่ %d ไม่อยู่ในรายชื่อ จึงต้องไม่เลื่อนสถานะ", type)
                    .isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
        }
    }
}
