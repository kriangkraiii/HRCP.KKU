package com.ecom.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.TestDataFactory;

/**
 * บันทึกแบบร่างอัตโนมัติต้องรอจนผู้ใช้แตะฟอร์มจริง
 *
 * <p>เดิมแค่เปิดฟอร์มแล้วออกไป สคริปต์บนหน้าที่เติมค่าตั้งต้นให้ก็ปลุก auto-draft ให้บันทึก
 * ค่าที่ระบบเติม (ชื่อ คณะ ตำแหน่งที่ขอ) ลงเอกสาร แบบร่างจึงไม่ว่าง ไม่ถูกลบทิ้ง และค้างอยู่ใน
 * dashboard เป็น "คำร้องฉบับร่าง" ทั้งที่ผู้ใช้ไม่ได้กรอกอะไรเลย (KKU-POS-2569-0003)
 */
@DisplayName("บันทึกแบบร่างอัตโนมัติรอจนผู้ใช้แตะฟอร์ม")
class AutoDraftTouchBrowserTest extends PlaywrightTestBase {

    /** debounce ของ auto_draft.js คือ 1 วินาที — รอเผื่อไว้ให้พ้นแน่ ๆ */
    private static final int WAIT_PAST_DEBOUNCE_MS = 3000;

    @Autowired
    private PositionRequestService positionService;

    private final List<String> draftSaves = new CopyOnWriteArrayList<>();
    private PositionRequest draft;

    @BeforeEach
    void aFreshDraftAndAWatchOnSaves() {
        UserDtls applicant = data.applicant();
        // ลงชื่อเข้าก่อนสร้างแบบร่าง — หน้า dashboard ที่ลงชื่อเข้าแล้วไปถึงลบแบบร่างว่างทิ้ง
        // ซึ่งเป็นพฤติกรรมที่เทสนี้พึ่งอยู่ แต่ต้องไม่ลบแบบร่างของเทสก่อนเริ่ม
        signIn(TestDataFactory.APPLICANT_EMAIL, TestDataFactory.PASSWORD);

        AcademicRequest evaluation = data.evaluationFor(applicant,
                AcademicRank.ASSISTANT_PROFESSOR, "อาจารย์");
        draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                evaluation, "ผู้ช่วยศาสตราจารย์");

        draftSaves.clear();
        page.onRequest(request -> {
            if ("POST".equals(request.method()) && request.url().contains("/api/draft/")) {
                draftSaves.add(request.url());
            }
        });
    }

    private void openDocumentOne() {
        page.navigate(baseUrl() + "/user/position/request/" + draft.getId() + "/document/1");
        page.waitForSelector("input[name='major']");
    }

    @Test
    @DisplayName("เปิดฟอร์มแล้วออกโดยไม่กรอก — ไม่บันทึก และแบบร่างหายไปจาก dashboard")
    void openingAFormWithoutTouchingItSavesNothing() {
        openDocumentOne();
        page.waitForTimeout(WAIT_PAST_DEBOUNCE_MS);

        assertThat(draftSaves).as("ไม่ควรมี POST ไป /api/draft/").isEmpty();
        assertThat(positionService.getDocumentsByType(draft.getId(), 1)).isEmpty();

        page.navigate(baseUrl() + "/user/position/dashboard");
        assertThat(page.content()).doesNotContain("คำร้องฉบับร่าง");
        assertThat(positionService.findById(draft.getId())).isEmpty();
    }

    @Test
    @DisplayName("พิมพ์ลงฟอร์ม — บันทึกอัตโนมัติ และแบบร่างยังอยู่ใน dashboard")
    void typingIntoTheFormSavesTheDraft() {
        openDocumentOne();
        page.locator("input[name='major']").pressSequentially("วิทยาการคอมพิวเตอร์");
        page.waitForTimeout(WAIT_PAST_DEBOUNCE_MS);

        assertThat(draftSaves).isNotEmpty();
        assertThat(positionService.getLatestDocumentData(draft.getId(), 1))
                .containsEntry("major", "วิทยาการคอมพิวเตอร์");

        page.navigate(baseUrl() + "/user/position/dashboard");
        assertThat(page.content()).contains("คำร้องฉบับร่าง");
        assertThat(positionService.findById(draft.getId())).isPresent();
    }
}
