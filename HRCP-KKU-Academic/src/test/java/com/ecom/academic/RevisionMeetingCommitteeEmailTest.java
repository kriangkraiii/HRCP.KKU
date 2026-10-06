package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AcademicRevisionFile;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.AcademicRevisionFileRepository;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.CommitteeDocumentMailer;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.RecordingMailSender;

/**
 * ข้อ 14-15 — นัดประชุมพิจารณาฉบับแก้ไขแล้ว กรรมการสามท่านตามคำสั่งแต่งตั้ง (เอกสารที่ 4)
 * ได้อีเมลนัดรอบใหม่ พร้อมทางเปิดไฟล์ฉบับแก้ของผู้ยื่น
 *
 * <p>เดิมการนัดรอบใหม่แจ้งเฉพาะผู้ยื่น กรรมการไม่รู้วันประชุมและไม่เคยเห็นฉบับแก้
 */
@DisplayName("ข้อ 14-15: อีเมลนัดประชุมพิจารณาฉบับแก้ไขถึงกรรมการ")
class RevisionMeetingCommitteeEmailTest extends AbstractFlowTest {

    private static final String SUBJECT = "ประชุมพิจารณาเอกสารฉบับแก้ไข";
    private static final String LINK_URL = "https://drive.example.invalid/revised-plan";

    @Autowired
    private AcademicRequestService service;

    @Autowired
    private AcademicRequestRepository academicRequests;

    @Autowired
    private AcademicRevisionFileRepository revisionFiles;

    private UserDtls applicant;
    private UserDtls officer;
    private UserDtls[] committee;
    private AcademicRequest request;

    @BeforeEach
    void revisionIsIn() {
        applicant = data.applicant();
        officer = data.admin();
        committee = data.committee();
        request = data.evaluation(applicant, RequestStatus.REVISION_SUBMITTED);
        request.setMeetingDate(LocalDateTime.of(2026, 9, 21, 13, 30));
        request = academicRequests.save(request);
        data.academicDocument(request, 3, data.appointmentOrder());
        data.academicDocument(request, 4, data.appointmentOrder());
    }

    private AcademicRevisionFile revision(String name, String path, String type) {
        AcademicRevisionFile f = new AcademicRevisionFile();
        f.setRequest(request);
        f.setRound(1);
        f.setOriginalFilename(name);
        f.setStoredPath(path);
        f.setFileType(type);
        f.setFileSize(2048L);
        f.setUploadedAt(LocalDateTime.now());
        return revisionFiles.save(f);
    }

    private List<RecordingMailSender.Sent> meetingMailTo(UserDtls who) {
        return mail().to(who.getEmail()).stream().filter(m -> m.subjectContains(SUBJECT)).toList();
    }

    @Test
    @DisplayName("นัดรอบฉบับแก้ไข — กรรมการทั้งสามได้อีเมลท่านละฉบับ มีวันเวลาสถานที่และไฟล์ฉบับแก้")
    void eachMemberGetsTheNewMeeting() {
        revision("แผนการสอน_แก้ไข.pdf", "academic/" + request.getId() + "/revisions/plan.pdf", "PDF");
        revision("สื่อการสอนฉบับแก้", LINK_URL, "LINK");

        service.scheduleMeeting(request.getId(), LocalDateTime.of(2026, 10, 5, 9, 0), LocalTime.of(12, 30),
                "ห้องประชุมสารบรรณ ชั้น 1", officer);

        awaitCondition("อีเมลนัดประชุมถึงกรรมการครบสามท่าน",
                () -> java.util.Arrays.stream(committee).allMatch(m -> !meetingMailTo(m).isEmpty()));
        for (UserDtls member : committee) {
            List<RecordingMailSender.Sent> sent = meetingMailTo(member);
            assertThat(sent).as("%s ได้อีเมลฉบับเดียว", member.getEmail()).hasSize(1);
            assertThat(sent.get(0).to()).as("ส่งแยกท่านละฉบับ").hasSize(1);
            assertThat(sent.get(0).body())
                    .contains(SignerNameResolver.printedName(member))
                    .contains("วันจันทร์ที่ 5 ตุลาคม 2569")
                    .contains("09.00 – 12.30 น.")
                    .contains("ห้องประชุมสารบรรณ ชั้น 1")
                    .contains("แผนการสอน_แก้ไข.pdf")
                    .contains(LINK_URL)
                    .contains(CommitteeDocumentMailer.filesPath(request.getId()));
        }
        assertThat(meetingMailTo(applicant)).as("ผู้ยื่นไม่ได้อีเมลนัดกรรมการ — วันประชุมเป็นความลับ").isEmpty();
        assertThat(service.getEditHistory(request.getId()))
                .as("มีบันทึกว่าส่งอีเมลนัดประชุมถึงกรรมการแล้ว")
                .anyMatch(e -> e.getAction() == com.ecom.academic.model.AcademicDocumentEditLog.EditAction.COMMITTEE_EMAILED
                        && e.getDocumentLabel() != null && e.getDocumentLabel().contains("ฉบับแก้ไข"));
    }

    @Test
    @DisplayName("นัดรอบแรก (ไม่ใช่ฉบับแก้) — ไม่ส่งอีเมลนัดรอบฉบับแก้ไข")
    void theFirstMeetingSendsNoRevisionMail() {
        AcademicRequest first = data.evaluation(data.otherApplicant(), RequestStatus.SUB_COMMITTEE_APPOINTED);
        data.academicDocument(first, 4, data.appointmentOrder());

        service.scheduleMeeting(first.getId(), LocalDateTime.of(2026, 10, 5, 9, 0), LocalTime.of(12, 0), "ห้อง 1",
                officer);
        settle();

        assertThat(java.util.Arrays.stream(committee).flatMap(m -> meetingMailTo(m).stream())).isEmpty();
    }

    @Test
    @DisplayName("ผูกกรรมการกับบัญชีไม่ได้ — ไม่ส่งถึงใครเลย และบอกเหตุผล")
    void sendsToNobodyWhenAMemberCannotBeFound() {
        java.util.Map<String, String> order = new java.util.LinkedHashMap<>(data.committeeFields());
        order.remove("committee_3_name__signer");
        order.put("committee_3_name", "ผู้ที่ไม่มี บัญชีในระบบ");
        AcademicRequest broken = data.evaluation(data.otherApplicant(), RequestStatus.MEETING_SCHEDULED);
        broken.setMeetingDate(LocalDateTime.of(2026, 10, 5, 9, 0));
        broken = academicRequests.save(broken);
        data.academicDocument(broken, 4,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(order).toString());

        CommitteeDocumentMailer.Outcome outcome = mailer.sendRevisionMeeting(broken.getId(), officer);

        assertThat(outcome.sent()).isFalse();
        assertThat(outcome.message()).contains("ผู้ที่ไม่มี บัญชีในระบบ");
        assertThat(java.util.Arrays.stream(committee).flatMap(m -> meetingMailTo(m).stream())).isEmpty();
    }

    @Autowired
    private CommitteeDocumentMailer mailer;

    @Test
    @DisplayName("ปุ่มส่งอีกครั้งในหน้าเจ้าหน้าที่ — ส่งอีเมลนัดถึงกรรมการทั้งสามอีกรอบ")
    void theOfficerCanResend() throws Exception {
        request.setCurrentStatus(RequestStatus.MEETING_SCHEDULED);
        request.setMeetingDate(LocalDateTime.of(2026, 10, 5, 9, 0));
        academicRequests.save(request);
        revision("แผนการสอน_แก้ไข.pdf", "academic/" + request.getId() + "/revisions/plan.pdf", "PDF");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/admin/academic/request/" + request.getId() + "/revision-meeting/email-committee")
                .with(user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", "")))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                        .attribute("succMsg", org.hamcrest.Matchers.containsString("ส่งอีเมลนัดประชุม")));

        for (UserDtls member : committee) {
            assertThat(meetingMailTo(member)).as(member.getEmail()).hasSize(1);
        }
    }

    @Test
    @DisplayName("หน้าเอกสารสำหรับกรรมการแสดงไฟล์ฉบับแก้ และกรรมการเปิดได้ — คนนอกเปิดไม่ได้")
    void membersCanOpenTheRevisionFiles() throws Exception {
        AcademicRevisionFile link = revision("สื่อการสอนฉบับแก้", LINK_URL, "LINK");
        String base = CommitteeDocumentMailer.filesPath(request.getId());

        String page = mvc.perform(get(base).with(user(committee[0].getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("สื่อการสอนฉบับแก้").contains(base + "/revision/" + link.getId());

        mvc.perform(get(base + "/revision/" + link.getId()).with(user(committee[0].getEmail()).roles("USER")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", LINK_URL));

        mvc.perform(get(base + "/revision/" + link.getId()).with(user(data.otherApplicant().getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }
}
