package com.ecom.academic.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * อัปเดตสดของหน้าคำร้องเจ้าหน้าที่ — หน้าที่เปิดคำร้องอยู่ได้รับ "changed" หลังคำร้องเปลี่ยนและ commit แล้ว
 */
@DisplayName("อัปเดตสดของหน้าคำร้อง (SSE)")
class RequestLiveUpdatesTest extends AbstractFlowTest {

    @Autowired
    private RequestLiveUpdates live;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private MvcResult open(String url) throws Exception {
        return mvc.perform(get(url).with(user(officer.getEmail()).roles("ADMIN")))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    @DisplayName("เฟส 2: เปลี่ยนสถานะแล้ว หน้าที่เปิดคำร้องนั้นได้รับแจ้ง — คำร้องอื่นไม่ได้")
    void positionStatusChangeIsPushed() throws Exception {
        PositionRequest watched = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        PositionRequest other = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        MvcResult page = open("/admin/position/request/" + watched.getId() + "/live");
        MvcResult otherPage = open("/admin/position/request/" + other.getId() + "/live");
        assertThat(live.subscriberCount(SignatureModule.POSITION, watched.getId())).isEqualTo(1);

        positionService.updateStatus(watched.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, officer, null, false);

        assertThat(page.getResponse().getContentAsString()).contains("event:changed");
        assertThat(otherPage.getResponse().getContentAsString()).doesNotContain("event:changed");
    }

    @Test
    @DisplayName("เฟส 2: ส่งเอกสารกลับแก้ไข — หน้าได้รับแจ้ง")
    void sendBackIsPushed() throws Exception {
        PositionRequest watched = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(watched, 3, "{}");
        MvcResult page = open("/admin/position/request/" + watched.getId() + "/live");

        positionService.openDocumentForRevision(watched.getId(), 3, "แก้ไข");

        assertThat(page.getResponse().getContentAsString()).contains("event:changed");
    }

    @Test
    @DisplayName("เฟส 1: เปลี่ยนสถานะแล้ว หน้าได้รับแจ้ง")
    void academicStatusChangeIsPushed() throws Exception {
        AcademicRequest watched = data.evaluation(applicant, RequestStatus.RECEIVED);
        MvcResult page = open("/admin/academic/request/" + watched.getId() + "/live");

        academicService.updateStatus(watched.getId(), RequestStatus.DRAFT, officer, "คณบดีไม่เห็นชอบ");

        assertThat(page.getResponse().getContentAsString()).contains("event:changed");
    }

    @Test
    @DisplayName("ผู้ยื่นเปิดช่องอัปเดตสดของเจ้าหน้าที่ไม่ได้")
    void applicantsCannotListen() throws Exception {
        PositionRequest watched = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        int code = mvc.perform(get("/admin/position/request/" + watched.getId() + "/live")
                .with(user(applicant.getEmail()).roles("USER")))
                .andReturn().getResponse().getStatus();

        assertThat(code).isIn(302, 403);
    }
}
