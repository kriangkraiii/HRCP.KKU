package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * วันประชุม สถานที่ และรายละเอียดกรรมการ เป็นความลับทางราชการ
 *
 * <p>ผู้ยื่นไม่ได้เข้าประชุม และไม่ควรรู้ว่าใครเป็นกรรมการหรือประชุมกันที่ไหนเมื่อไหร่
 * แต่ระบบเคยแสดงวันและสถานที่ให้เห็นถึงสี่ที่ — แดชบอร์ด หน้ารายละเอียด ประวัติคำร้อง
 * และในอีเมลแจ้งเปลี่ยนสถานะ
 *
 * <p>เทสต์นี้ยิงหน้าเว็บจริงด้วยสิทธิ์ผู้ยื่น แล้วค้นหาค่าที่เป็นความลับใน HTML ตรง ๆ
 * ถ้าใครเผลอเอากลับมาแสดงอีกจะพังที่นี่
 */
@DisplayName("วันประชุมและสถานที่เป็นความลับจากผู้ยื่น")
class MeetingDetailsAreConfidentialTest extends AbstractFlowTest {

    private static final String SECRET_LOCATION = "ห้องประชุมลับชั้น๙";

    @Autowired
    private AcademicRequestRepository academicRequests;

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls applicant;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
    }

    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private AcademicRequest requestWithAMeeting() {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        request.setMeetingDate(LocalDateTime.of(2569 - 543, 9, 21, 13, 30));
        request.setMeetingLocation(SECRET_LOCATION);
        return academicRequests.save(request);
    }

    private String pageAs(String url) throws Exception {
        return mvc.perform(get(url).with(as(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void assertHidesTheMeeting(String html, String where) {
        assertThat(html).as("%s ต้องไม่เปิดเผยสถานที่ประชุม", where)
                .doesNotContain(SECRET_LOCATION);
        assertThat(html).as("%s ต้องไม่เปิดเผยวันประชุม", where)
                .doesNotContain("วันประชุม");
    }

    @Test
    @DisplayName("แดชบอร์ดผู้ยื่นไม่แสดงวันและสถานที่ประชุม")
    void dashboardHidesIt() throws Exception {
        requestWithAMeeting();
        assertHidesTheMeeting(pageAs("/user/academic/dashboard"), "แดชบอร์ด");
    }

    @Test
    @DisplayName("หน้ารายละเอียดคำร้องไม่แสดงวันและสถานที่ประชุม")
    void requestDetailHidesIt() throws Exception {
        AcademicRequest request = requestWithAMeeting();
        assertHidesTheMeeting(pageAs("/user/academic/request/" + request.getId()),
                "หน้ารายละเอียดคำร้อง");
    }

    @Test
    @DisplayName("หน้าประวัติคำร้องไม่แสดงวันและสถานที่ประชุม")
    void historyHidesIt() throws Exception {
        requestWithAMeeting();
        assertHidesTheMeeting(pageAs("/user/academic/history"), "หน้าประวัติคำร้อง");
    }

    /**
     * อีกด้านของกติกาเดียวกัน — ซ่อนจากผู้ยื่น ไม่ใช่ลบทิ้ง
     *
     * <p>เจ้าหน้าที่ต้องกรอกและแก้วันนัดกับสถานที่ได้ตามเดิม ถ้าวันหนึ่งมีคนไล่ช่องเหล่านี้
     * ออกเพราะเข้าใจว่าเป็นความลับ ระบบจะนัดประชุมไม่ได้อีกเลย
     */
    @Test
    @DisplayName("หน้าผู้ดูแลระบบยังกรอกและเห็นวันกับสถานที่ได้ตามเดิม")
    void theOfficerCanStillSeeAndEditIt() throws Exception {
        AcademicRequest request = requestWithAMeeting();

        String html = mvc.perform(get("/admin/academic/request/" + request.getId())
                .with(as(data.admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .as("ช่องกรอกวันนัดและสถานที่ต้องยังอยู่ฝั่งเจ้าหน้าที่")
                .contains("name=\"meetingDate\"")
                .contains("name=\"meetingLocation\"")
                .contains(SECRET_LOCATION);
    }
    @Test
    @DisplayName("อีเมลแจ้งเปลี่ยนสถานะไม่แนบวันและสถานที่ประชุม")
    void theEmailHidesIt() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
        request.setMeetingDate(LocalDateTime.of(2026, 9, 21, 13, 30));
        request.setMeetingLocation(SECRET_LOCATION);
        academicRequests.save(request);

        academicService.updateStatus(request.getId(), RequestStatus.MEETING_SCHEDULED,
                data.admin(), "นัดหมายแล้ว", true);

        awaitCondition("อีเมลถึงผู้ยื่น", () -> !mail().to(applicant.getEmail()).isEmpty());
        assertThat(mail().to(applicant.getEmail()))
                .allSatisfy(sent -> assertThat(sent.body())
                        .doesNotContain(SECRET_LOCATION)
                        .doesNotContain("วันประชุม"));
    }
}
