package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * ด่าน "มีคำร้องค้างอยู่หรือไม่" ต้องตอบได้แม้ข้อมูลจะเดินไปอยู่ในสภาพที่ไม่ควรเกิด
 *
 * <p>ระบบตั้งใจให้ผู้ยื่นมีคำร้องที่ยังเดินอยู่ได้ครั้งละหนึ่งฉบับ และ
 * {@code hasActiveRequest} คือด่านที่บังคับกติกานั้น แต่ตัวด่านเองเคยถาม
 * ฐานข้อมูลด้วย {@code Optional} ซึ่งแปลว่า "มีได้ไม่เกินหนึ่งแถว" ทั้งที่ SQL
 * ที่มันยิงไม่ได้รับประกันเรื่องนั้นเลย
 *
 * <p>สภาพสองฉบับเกิดขึ้นได้จริงโดยไม่ต้องผ่าน {@code createRequest}:
 *
 * <ol>
 *   <li>คำร้อง A จบที่ {@code COMPLETED_FAIL} ซึ่งเป็นสถานะปลายทาง</li>
 *   <li>ด่านตอบว่าไม่มีคำร้องค้าง ผู้ยื่นจึงสร้างคำร้อง B ได้ตามกติกา</li>
 *   <li>เจ้าหน้าที่ย้อนสถานะ A กลับมาเป็นสถานะที่ยังเดินอยู่</li>
 * </ol>
 *
 * <p>ผลที่เคยเป็นคือ {@code IncorrectResultSizeDataAccessException} →
 * <b>HTTP 500 ถาวรบนแดชบอร์ดของผู้ยื่นทั้งสองเฟส</b> และผู้ยื่นแก้เองไม่ได้
 * ด่านที่มีไว้กันไม่ให้เกิดสภาพนี้ กลายเป็นสิ่งแรกที่พังเมื่อมันเกิดขึ้น
 *
 * <p>บั๊กนี้ถูกพบโดย {@code com.ecom.render.EveryPageRendersTest} ซึ่งเปิด
 * ทุกหน้าในระบบ ไม่ใช่จากการอ่านโค้ด — ไม่มีเทสใดเคยเปิดแดชบอร์ดของผู้ยื่น
 * ที่มีคำร้องมากกว่าหนึ่งฉบับมาก่อน
 */
@DisplayName("ด่านคำร้องค้าง: ต้องไม่พังเมื่อผู้ยื่นมีคำร้องที่ยังเดินอยู่มากกว่าหนึ่งฉบับ")
class ActiveRequestGuardTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionRequestService;

    /** สร้างสภาพที่คำร้องสองฉบับของคนเดียวกันยังเดินอยู่พร้อมกัน */
    private UserDtls applicantWithTwoLiveRequests() {
        UserDtls applicant = data.applicant();
        data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        return applicant;
    }

    @Test
    @DisplayName("ถามว่ามีคำร้องค้างไหม แล้วต้องได้คำตอบ ไม่ใช่ข้อยกเว้น")
    void theGuardAnswersInsteadOfThrowing() {
        UserDtls applicant = applicantWithTwoLiveRequests();

        assertThat(positionRequestService.hasActiveRequest(applicant.getId()))
                .as("มีคำร้องที่ยังเดินอยู่สองฉบับ คำตอบที่ถูกคือ 'มี'")
                .isTrue();
    }

    @Test
    @DisplayName("แดชบอร์ดคำร้องตำแหน่งยังเปิดได้")
    void thePositionDashboardStillOpens() throws Exception {
        applicantWithTwoLiveRequests();

        mvc.perform(get("/user/position/dashboard")
                .with(user(TestDataFactory.APPLICANT_EMAIL).roles("USER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());
    }

    @Test
    @DisplayName("แดชบอร์ดการประเมินการสอนยังเปิดได้ — หน้านี้ก็ถามด่านเดียวกัน")
    void theEvaluationDashboardStillOpens() throws Exception {
        applicantWithTwoLiveRequests();

        mvc.perform(get("/user/academic/dashboard")
                .with(user(TestDataFactory.APPLICANT_EMAIL).roles("USER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isOk());
    }

    @Test
    @DisplayName("แบบร่างสองฉบับก็ต้องไม่พัง — คืนฉบับใหม่สุด")
    void twoDraftsResolveToTheMostRecentOne() {
        UserDtls applicant = data.applicant();
        data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
        var newer = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

        assertThat(positionRequestService.findDraftByApplicant(applicant.getId()))
                .as("มีแบบร่างสองฉบับต้องได้คำตอบ ไม่ใช่ข้อยกเว้น")
                .isPresent()
                .get()
                .extracting(r -> r.getId())
                .isEqualTo(newer.getId());
    }
}
