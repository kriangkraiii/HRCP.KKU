package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * บันทึกเอกสารครั้งที่สองขึ้นไป ต้องอ่านกลับได้ค่าที่เพิ่งบันทึก
 *
 * <p>ลำดับที่ผู้ยื่นทำจริง: พิมพ์ (บันทึกแบบร่างอัตโนมัติ) → กดบันทึก → พิมพ์ต่อ → กดบันทึกอีกครั้ง
 * แบบร่างรอบสองเป็นแถวใหม่ข้างฉบับที่บันทึกไว้ แล้วการกดบันทึกเปลี่ยนแถวนั้นเป็นฉบับจริง
 */
@DisplayName("เอกสารคำร้องขอตำแหน่ง: บันทึกซ้ำแล้วอ่านได้ค่าล่าสุด")
class PositionDocumentLatestSaveTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    @Test
    @DisplayName("แบบร่าง → บันทึก → แบบร่าง → บันทึก: ค่าล่าสุดคือค่าที่บันทึกครั้งหลัง")
    void theSecondSaveIsTheOneReadBack() {
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DRAFT, null);

        positionService.saveDraft(request, 1, "{\"major\":\"หนึ่ง\"}", "เอกสารที่ 1", "APPLICANT");
        positionService.saveDocument(request, 1, "{\"major\":\"หนึ่ง\"}", null, "เอกสารที่ 1", null, "APPLICANT");
        positionService.saveDraft(request, 1, "{\"major\":\"สอง\"}", "เอกสารที่ 1", "APPLICANT");
        positionService.saveDocument(request, 1, "{\"major\":\"สอง\"}", null, "เอกสารที่ 1", null, "APPLICANT");

        assertThat(positionService.getLatestDocumentData(request.getId(), 1)).containsEntry("major", "สอง");

        positionService.saveDraft(request, 1, "{\"major\":\"สาม\"}", "เอกสารที่ 1", "APPLICANT");
        positionService.saveDocument(request, 1, "{\"major\":\"สาม\"}", null, "เอกสารที่ 1", null, "APPLICANT");

        assertThat(positionService.getLatestDocumentData(request.getId(), 1)).containsEntry("major", "สาม");
        assertThat(positionService.getDocumentsByType(request.getId(), 1))
                .as("ฉบับที่บันทึกแล้วของเอกสารเดียวกันมีแถวเดียว")
                .hasSize(1);
    }

    @Test
    @DisplayName("เจ้าหน้าที่กดดาวน์โหลด — แบบร่างที่ผู้ยื่นพิมพ์ค้างไว้ต้องไม่หาย")
    void downloadingDoesNotOverwriteAPendingDraft() throws Exception {
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED, null);
        positionService.saveDocument(request, 1, "{\"major\":\"หนึ่ง\"}", null, "เอกสารที่ 1", null, "APPLICANT");
        positionService.saveDraft(request, 1, "{\"major\":\"สอง\"}", "เอกสารที่ 1", "APPLICANT");
        UserDtls admin = data.admin();

        mvc.perform(get("/admin/position/request/" + request.getId() + "/document/1/download")
                .param("format", "docx").with(user(admin.getEmail()).roles("ADMIN")));

        assertThat(positionService.getLatestDocumentData(request.getId(), 1))
                .as("เดิมหน้าดาวน์โหลดเรียก saveDocument ด้วยเนื้อหาฉบับเก่า ซึ่งทับแบบร่างแล้วบันทึกเป็นฉบับจริง")
                .containsEntry("major", "สอง");
        assertThat(positionService.getDocumentsByType(request.getId(), 1))
                .anyMatch(d -> Boolean.TRUE.equals(d.getIsDraft()));
    }

    @Test
    @DisplayName("เอกสารที่ถูกส่งกลับให้แก้ — บันทึกฉบับแก้แล้ว ธงส่งกลับและเหตุผลต้องยังอยู่")
    void aSendBackSurvivesTheApplicantsNextSave() {
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DOCUMENT_RECEIVED, null);
        positionService.saveDocument(request, 1, "{\"major\":\"หนึ่ง\"}", null, "เอกสารที่ 1", null, "APPLICANT");
        positionService.openDocumentForRevision(request.getId(), 1, "แก้สาขาวิชา");

        positionService.saveDraft(request, 1, "{\"major\":\"สอง\"}", "เอกสารที่ 1", "APPLICANT");
        positionService.saveDocument(request, 1, "{\"major\":\"สอง\"}", null, "เอกสารที่ 1", null, "APPLICANT");

        assertThat(positionService.isRevisionRequested(request.getId(), 1))
                .as("ธงอยู่บนแถวเดิมซึ่งถูกปิดไป — ต้องย้ายมาแถวที่เหลือ ไม่อย่างนั้นด่านลงนามใหม่หาย")
                .isTrue();
        assertThat(positionService.getRevisionNote(request.getId(), 1)).isEqualTo("แก้สาขาวิชา");
        assertThat(positionService.latestRevisionRequestedAt(request.getId(), 1)).isNotNull();
    }
}
