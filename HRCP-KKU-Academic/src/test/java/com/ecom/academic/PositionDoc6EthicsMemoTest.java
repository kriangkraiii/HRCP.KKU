package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * เฟส 2 เอกสารจริยธรรมการวิจัยในมนุษย์ (เอกสารที่ 6): ผู้ขอลงนามก่อนคณบดีตามบรรทัดลงนามในเทมเพลต
 * เลือกผลงานจากฐานข้อมูลผลงานได้ และบอกจำนวนงานวิจัยที่ต้องใช้ตามตำแหน่งที่ขอ
 */
@DisplayName("เฟส 2 เอกสารที่ 6: ลงนาม เลือกผลงาน และจำนวนงานวิจัยตามตำแหน่ง")
class PositionDoc6EthicsMemoTest extends AbstractFlowTest {

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private PositionRequestService positionService;

    @Test
    @DisplayName("ผู้ขอลงนามเป็นลำดับแรก แล้วจึงคณบดี")
    void applicantSignsBeforeTheDean() {
        assertThat(SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, 6))
                .extracting(SignatureAnchorRegistry.SignatureSlot::slotKey)
                .containsExactly("applicant", "dean");
    }

    @Test
    @DisplayName("ยังไม่ลงนามเอกสารที่ 6 ส่งคำร้องไม่ได้ — และแก้ไขผ่านการลงนามใหม่ ไม่ใช่ปุ่มยื่นการแก้ไข")
    void theApplicantMustSignIt() {
        UserDtls applicant = data.applicant();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

        assertThat(workflow.getUnsignedApplicantDocTypes(SignatureModule.POSITION, request.getId(), List.of(6)))
                .containsExactly(6);

        PositionRequest submitted = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(submitted, 6, "{\"applicant_name\":\"สมชาย ใจดี\"}");
        positionService.openDocumentForRevision(submitted.getId(), 6, "แก้ข้อมูล");
        assertThat(positionService.canSubmitRevision(submitted, 6)).isFalse();
    }

    @Test
    @DisplayName("หน้าฟอร์มมีปุ่มเลือกจากฐานข้อมูลผลงาน และบอกจำนวนงานวิจัยของตำแหน่งที่ขอ")
    void theFormOffersThePickerAndTheRequiredCount() throws Exception {
        UserDtls applicant = data.applicant();
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null,
                "รองศาสตราจารย์");

        String page = mvc.perform(get("/user/position/request/" + request.getId() + "/document/6")
                .with(user(applicant.getEmail()).roles("USER")))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"btnPickResearch\"").contains("/js/scopus_picker.js");
        assertThat(page).contains("งานวิจัยที่ต้องใช้ขอตำแหน่ง<span>รองศาสตราจารย์</span>")
                .contains("งานวิจัยอย่างน้อย <strong>๑๐ เรื่อง</strong> ใน Scopus Q1–Q2 หลังได้ ผศ.")
                .doesNotContain("หลังได้ รศ.");
    }
}
