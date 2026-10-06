package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ข้อ 14-15 — ฉบับแก้ไขเข้าที่ประชุมอนุกรรมการรอบใหม่ จึงต้องมีวันประชุมรอบใหม่
 *
 * <p>เดิมช่องวันประชุมเติมวันของรอบแรกไว้ให้ และเว้นว่างก็ผ่าน เจ้าหน้าที่กดบันทึกโดยไม่ได้แก้
 * คำร้องก็กลายเป็น "นัดหมายแล้ว" ทั้งที่วันในระบบคือวันประชุมที่ผ่านไปแล้ว
 */
@DisplayName("ข้อ 14-15: นัดประชุมรอบพิจารณาฉบับแก้ไขต้องมีวันใหม่")
class RevisionMeetingTest extends AbstractFlowTest {

    private static final LocalDateTime FIRST_MEETING = LocalDateTime.of(2026, 9, 21, 13, 30);

    @Autowired
    private AcademicRequestService service;

    @Autowired
    private AcademicRequestRepository academicRequests;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
    }

    private static UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private AcademicRequest revisionIn() {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.REVISION_SUBMITTED);
        request.setMeetingDate(FIRST_MEETING);
        request.setMeetingLocation("ห้องประชุม 1");
        return academicRequests.save(request);
    }

    private AcademicRequest reload(AcademicRequest request) {
        return service.findById(request.getId()).orElseThrow();
    }

    @Test
    @DisplayName("เปลี่ยนเป็น 'นัดหมาย' โดยไม่ระบุวันใหม่ไม่ได้")
    void needsANewDate() {
        AcademicRequest request = revisionIn();

        assertThatThrownBy(() -> service.updateStatus(request.getId(), RequestStatus.MEETING_SCHEDULED,
                officer, "ส่งต่ออนุฯ", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("วันประชุม");

        assertThat(reload(request).getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
    }

    @Test
    @DisplayName("วันประชุมรอบใหม่ต้องหลังวันประชุมครั้งก่อน")
    void theNewDateMustComeAfterTheLastMeeting() {
        AcademicRequest request = revisionIn();

        for (LocalDateTime notLater : new LocalDateTime[] { FIRST_MEETING, FIRST_MEETING.minusDays(1) }) {
            assertThatThrownBy(() -> service.setMeetingDate(request.getId(), notLater, "ห้อง 2", officer))
                    .as("วัน %s ไม่ได้อยู่หลังวันประชุมครั้งก่อน", notLater)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("หลังวันประชุมครั้งก่อน");
        }
        AcademicRequest unchanged = reload(request);
        assertThat(unchanged.getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
        assertThat(unchanged.getMeetingDate()).isEqualTo(FIRST_MEETING);
    }

    @Test
    @DisplayName("ระบุวันใหม่ที่หลังรอบแรก — นัดหมายได้ และเก็บวันใหม่")
    void aLaterDateSchedulesTheSecondMeeting() {
        AcademicRequest request = revisionIn();
        LocalDateTime second = FIRST_MEETING.plusDays(14);

        service.setMeetingDate(request.getId(), second, "ห้องประชุม 2", officer);

        AcademicRequest after = reload(request);
        assertThat(after.getCurrentStatus()).isEqualTo(RequestStatus.MEETING_SCHEDULED);
        assertThat(after.getMeetingDate()).isEqualTo(second);
        assertThat(after.getMeetingLocation()).isEqualTo("ห้องประชุม 2");
    }

    @Test
    @DisplayName("ฟอร์มเจ้าหน้าที่: ส่งโดยไม่กรอกวัน ได้ข้อความบอกเหตุผล สถานะไม่เปลี่ยน")
    void theFormExplainsAMissingDate() throws Exception {
        AcademicRequest request = revisionIn();

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/status")
                .param("status", "MEETING_SCHEDULED")
                .param("meetingDate", "")
                .with(as(officer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorDetail", org.hamcrest.Matchers.containsString("วันประชุม")));

        assertThat(reload(request).getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
    }

    @Test
    @DisplayName("ฟอร์มเจ้าหน้าที่: ไม่เติมวันรอบแรกไว้ให้ บังคับกรอก และบอกวันประชุมครั้งก่อน")
    void theFormStartsEmptyAndShowsTheLastMeeting() throws Exception {
        AcademicRequest request = revisionIn();

        String html = mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Element fields = Jsoup.parse(html).getElementById("meetingFields");
        Element dayInput = fields.selectFirst("input[name=meetingDay]");
        assertThat(dayInput).isNotNull();
        assertThat(dayInput.attr("value")).as("ห้ามเติมวันประชุมรอบแรก").isEmpty();
        assertThat(dayInput.attr("min")).as("วันต้องไม่ก่อนวันประชุมครั้งก่อน").isEqualTo("2026-09-21");
        assertThat(fields.selectFirst(".meeting-fields").hasAttr("data-required")).isTrue();
        assertThat(fields.selectFirst("input[name=meetingStart]").val()).isEmpty();
        assertThat(fields.selectFirst("input[name=meetingEnd]")).isNotNull();
        assertThat(Jsoup.parse(html).getElementById("previousMeetingHint")).isNotNull();
    }

    private String adminPage(AcademicRequest request) throws Exception {
        return mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(officer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * รอบแรกระบบเลื่อนเป็น "นัดหมาย" เองเมื่อหนังสือเชิญ (เอกสารที่ 5) ลงนามครบ แต่รอบฉบับแก้
     * ต้องไปหาฟอร์มอัปเดตสถานะอีกคอลัมน์ แล้วเลือกสถานะเอง — นัดได้จากรายการเอกสารตรง ๆ แทน
     */
    @Test
    @DisplayName("รายการเอกสาร: มีฟอร์มนัดประชุมรอบฉบับแก้ไขใต้เอกสารที่ 5 ไม่ต้องเลือกสถานะเอง")
    void theDocumentListOffersTheRevisionMeeting() throws Exception {
        AcademicRequest request = revisionIn();

        Element form = Jsoup.parse(adminPage(request)).getElementById("revisionMeetingForm");

        assertThat(form).as("ฟอร์มนัดประชุมรอบฉบับแก้ไขในรายการเอกสาร").isNotNull();
        assertThat(form.attr("action")).endsWith("/request/" + request.getId() + "/status");
        assertThat(form.selectFirst("input[type=hidden][name=status]").val()).isEqualTo("MEETING_SCHEDULED");
        // วันแบบไทย (พ.ศ.) + เวลาเริ่ม/สิ้นสุดแบบ 24 ชั่วโมง — แบบเดียวกับเอกสารที่ 5
        Element day = form.selectFirst("input[name=meetingDay]");
        assertThat(day.val()).as("ห้ามเติมวันประชุมรอบแรก").isEmpty();
        assertThat(day.attr("min")).isEqualTo("2026-09-21");
        assertThat(form.selectFirst(".thai-full-date")).isNotNull();
        assertThat(form.select(".time24[data-target=meetingStart], .time24[data-target=meetingEnd]")).hasSize(2);
        assertThat(form.select("input[type=hidden][name=meetingStart], input[type=hidden][name=meetingEnd]")).hasSize(2);
        assertThat(form.selectFirst("input[name=meetingDate]")).as("ไม่ใช้ช่อง datetime-local (AM/PM) แล้ว").isNull();
        assertThat(form.selectFirst("input[name=meetingLocation]").val()).isEqualTo("ห้องประชุม 1");
    }

    @Test
    @DisplayName("รายการเอกสาร: ไม่มีฟอร์มนัดประชุมรอบฉบับแก้ไขเมื่อไม่ได้รอส่งต่อฉบับแก้")
    void theRevisionMeetingFormOnlyAppearsWhileARevisionWaits() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

        assertThat(Jsoup.parse(adminPage(request)).getElementById("revisionMeetingForm")).isNull();
    }

    @Test
    @DisplayName("รายการเอกสาร: กดนัดประชุมแล้วคำร้องเป็น 'นัดหมาย' พร้อมวันใหม่")
    void submittingTheRevisionMeetingFormSchedules() throws Exception {
        AcademicRequest request = revisionIn();

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/status")
                .param("status", "MEETING_SCHEDULED")
                .param("meetingDay", "2026-10-05")
                .param("meetingStart", "09.00")
                .param("meetingEnd", "12.30")
                .param("meetingLocation", "ห้องประชุม 2")
                .with(as(officer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));

        AcademicRequest after = reload(request);
        assertThat(after.getCurrentStatus()).isEqualTo(RequestStatus.MEETING_SCHEDULED);
        assertThat(after.getMeetingDate()).isEqualTo(LocalDateTime.of(2026, 10, 5, 9, 0));
        assertThat(after.getMeetingEndTime()).isEqualTo(java.time.LocalTime.of(12, 30));
        assertThat(after.getMeetingLocation()).isEqualTo("ห้องประชุม 2");
    }

    @Test
    @DisplayName("เวลาสิ้นสุดต้องหลังเวลาเริ่ม — ได้ข้อความบอกเหตุผล สถานะไม่เปลี่ยน")
    void theMeetingMustEndAfterItStarts() throws Exception {
        AcademicRequest request = revisionIn();

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/status")
                .param("status", "MEETING_SCHEDULED")
                .param("meetingDay", "2026-10-05")
                .param("meetingStart", "13.00")
                .param("meetingEnd", "12.00")
                .with(as(officer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorDetail", org.hamcrest.Matchers.containsString("เวลาสิ้นสุด")));

        assertThat(reload(request).getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
    }

    @Test
    @DisplayName("ระบุวันแต่ไม่ระบุเวลาเริ่ม — ได้ข้อความบอกเหตุผล สถานะไม่เปลี่ยน")
    void theStartTimeIsRequired() throws Exception {
        AcademicRequest request = revisionIn();

        mvc.perform(post("/admin/academic/request/" + request.getId() + "/status")
                .param("status", "MEETING_SCHEDULED")
                .param("meetingDay", "2026-10-05")
                .param("meetingStart", "")
                .with(as(officer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorDetail", org.hamcrest.Matchers.containsString("เวลาเริ่มประชุม")));

        assertThat(reload(request).getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
    }

    @Test
    @DisplayName("ซองเอกสารที่ 5 ปิดระหว่างรอส่งต่อฉบับแก้ — ไม่ข้ามไปนัดหมายด้วยวันเดิม")
    void closingDocumentFiveDoesNotReuseTheOldDate() {
        AcademicRequest request = revisionIn();

        service.autoUpdateStatusByDocument(request.getId(), 5, officer, null, false);

        assertThat(reload(request).getCurrentStatus()).isEqualTo(RequestStatus.REVISION_SUBMITTED);
    }
}
