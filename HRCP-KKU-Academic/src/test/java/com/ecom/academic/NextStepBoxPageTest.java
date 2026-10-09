package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

@DisplayName("กล่องขั้นต่อไปในหน้าคำร้องของเจ้าหน้าที่")
class NextStepBoxPageTest extends AbstractFlowTest {

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private String adminPage(String url) throws Exception {
        return mvc.perform(get(url).with(user(officer.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("เฟส 1 รับคำร้อง: บอกให้ทำเอกสารขอรายชื่อและคำสั่งแต่งตั้ง พร้อมลิงก์")
    void phaseOne() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

        String html = adminPage("/admin/academic/request/" + request.getId());

        assertThat(html).contains("id=\"nextStep\"")
                .contains("ขอรายชื่อและแต่งตั้งคณะอนุกรรมการประเมินผลการสอน")
                .contains("href=\"/admin/academic/request/" + request.getId() + "/document/4\"");
    }

    @Test
    @DisplayName("เฟส 2 รับคำร้อง: บอกให้ทำแบบตรวจสอบคุณสมบัติ พร้อมลิงก์")
    void phaseTwo() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);

        String html = adminPage("/admin/position/request/" + request.getId());

        assertThat(html).contains("id=\"nextStep\"")
                .contains("ตรวจสอบเอกสารของผู้ยื่น และทำแบบตรวจสอบคุณสมบัติ")
                .contains("href=\"/admin/position/request/" + request.getId() + "/document/7\"");
    }

    @Test
    @DisplayName("แบบร่างไม่มีกล่อง")
    void draftHasNone() throws Exception {
        PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

        assertThat(adminPage("/admin/position/request/" + request.getId())).doesNotContain("id=\"nextStep\"");
    }
}
