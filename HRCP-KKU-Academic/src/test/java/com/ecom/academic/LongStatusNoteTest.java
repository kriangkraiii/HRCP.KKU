package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;

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
 * หมายเหตุของการเปลี่ยนสถานะยาวเกิน 255 ตัวอักษรได้
 *
 * <p>คอลัมน์ note ของประวัติสถานะถูกสร้างโดย Hibernate ddl-auto สมัยก่อนมี migration จึงเป็น varchar(255)
 * ขณะที่ช่องเหตุผลขอทบทวนผลรับได้ 2,000 ตัวอักษร และช่องหมายเหตุของเจ้าหน้าที่ไม่จำกัด — เขียนยาวเกินแล้วบันทึกไม่ได้
 */
@DisplayName("หมายเหตุการเปลี่ยนสถานะยาวได้")
class LongStatusNoteTest extends AbstractFlowTest {

    private static final String LONG = "เหตุผลยาว ".repeat(60); // 600 ตัวอักษร

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Test
    @DisplayName("ขอทบทวนผลด้วยเหตุผลยาว — บันทึกได้ครบ")
    void aLongAppealReasonIsKept() {
        UserDtls applicant = data.applicant();
        AcademicRequest failed = data.evaluation(applicant, RequestStatus.COMPLETED_FAIL);

        academicService.submitAppeal(failed.getId(), applicant, LONG);

        assertThat(academicService.getStatusHistory(failed.getId()))
                .anyMatch(h -> h.getNote() != null && h.getNote().contains(LONG.trim()));
    }

    @Test
    @DisplayName("เจ้าหน้าที่เปลี่ยนสถานะคำร้องขอตำแหน่งพร้อมหมายเหตุยาว — บันทึกได้ครบ")
    void aLongPositionStatusNoteIsKept() {
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED, null);
        PositionRequestStatus next = PositionRequestStatus.DOCUMENT_RECEIVED.allowedNext().iterator().next();

        positionService.updateStatus(request.getId(), next, data.admin(), LONG, false);

        assertThat(positionService.getStatusHistory(request.getId()))
                .anyMatch(h -> LONG.equals(h.getNote()));
    }
}
