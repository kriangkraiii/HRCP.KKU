package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AcademicRevisionFile;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.AcademicRevisionService;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * ข้อ 13 — ผู้ยื่นส่งเอกสารที่แก้ไขแล้วได้หลายไฟล์และลิงก์ในครั้งเดียว และเจ้าหน้าที่เปิดได้ทุกรายการ
 */
@DisplayName("ข้อ 13: ส่งเอกสารที่แก้ไขแล้วหลายรายการ")
class RevisionSubmissionTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService service;

    @Autowired
    private AcademicRevisionService revisions;

    @Autowired
    private NotificationRepository notifications;

    private UserDtls applicant;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
    }

    private static UserRequestPostProcessor as(UserDtls who) {
        return user(who.getEmail()).roles(who.getRole().replace("ROLE_", ""));
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("files", name, "application/pdf", "%PDF-1.4 revised".getBytes());
    }

    private RequestStatus statusOf(AcademicRequest request) {
        return service.findById(request.getId()).orElseThrow().getCurrentStatus();
    }

    @Test
    @DisplayName("ส่งสองไฟล์กับหนึ่งลิงก์ — เก็บครบเป็นรอบเดียว แล้วสถานะเป็น 'ส่งเอกสารแก้ไขแล้ว'")
    void storesEveryItemAsOneRound() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("แบบประเมิน-แก้ไข.pdf"))
                .file(pdf("แผนการสอน-แก้ไข.pdf"))
                .param("linkUrl", "https://drive.google.com/file/d/abc")
                .param("linkTitle", "วิดีโอการสอน")
                .with(as(applicant)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("succMsg"));

        assertThat(statusOf(request)).isEqualTo(RequestStatus.REVISION_SUBMITTED);
        List<AcademicRevisionFile> latest = revisions.latestRound(request.getId());
        assertThat(latest).extracting(AcademicRevisionFile::getOriginalFilename)
                .containsExactly("แบบประเมิน-แก้ไข.pdf", "แผนการสอน-แก้ไข.pdf", "วิดีโอการสอน");
        assertThat(latest).extracting(AcademicRevisionFile::getRound).containsOnly(1);
    }

    @Test
    @DisplayName("ไม่แนบอะไรเลย — ไม่เปลี่ยนสถานะ และบอกเหตุผล")
    void refusesAnEmptySubmission() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .with(as(applicant)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMsg"));

        assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED_REVISE);
    }

    @Test
    @DisplayName("ไฟล์ชนิดที่ไม่อนุญาตปนมาหนึ่งไฟล์ — ทั้งชุดไม่ถูกบันทึก")
    void oneBadFileRejectsTheWholeBatch() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("ok.pdf"))
                .file(new MockMultipartFile("files", "virus.exe", "application/octet-stream", new byte[] { 1 }))
                .with(as(applicant)).with(csrf()))
                .andExpect(flash().attributeExists("errorMsg"));

        assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED_REVISE);
        assertThat(revisions.byRound(request.getId())).isEmpty();
    }

    @Test
    @DisplayName("ลิงก์ที่ไม่ใช่ http(s) ถูกปฏิเสธ")
    void refusesANonHttpLink() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .param("linkUrl", "javascript:alert(1)")
                .with(as(applicant)).with(csrf()))
                .andExpect(flash().attributeExists("errorMsg"));

        assertThat(statusOf(request)).isEqualTo(RequestStatus.COMPLETED_REVISE);
    }

    @Test
    @DisplayName("ส่งได้เฉพาะตอนรอแก้ไข — หลังส่งไปแล้วส่งซ้ำไม่ได้")
    void onlyWhileARevisionIsRequested() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.REVISION_SUBMITTED);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("late.pdf"))
                .with(as(applicant)).with(csrf()))
                .andExpect(flash().attributeExists("errorMsg"));

        assertThat(revisions.byRound(request.getId())).isEmpty();
    }

    @Test
    @DisplayName("เจ้าหน้าที่เห็นรายการฉบับแก้ และดาวน์โหลดไฟล์/เปิดลิงก์ได้")
    void theOfficerSeesAndOpensEveryItem() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);
        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("แบบประเมิน-แก้ไข.pdf"))
                .param("linkUrl", "https://drive.google.com/file/d/abc")
                .param("linkTitle", "วิดีโอการสอน")
                .with(as(applicant)).with(csrf()));
        List<AcademicRevisionFile> latest = revisions.latestRound(request.getId());

        String html = mvc.perform(get("/admin/academic/request/" + request.getId()).with(as(data.admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("เอกสารฉบับแก้ไขจากผู้ยื่น", "แบบประเมิน-แก้ไข.pdf", "วิดีโอการสอน", "รอบที่ 1");

        String base = "/admin/academic/request/" + request.getId() + "/revision/";
        mvc.perform(get(base + latest.get(0).getId()).with(as(data.admin())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
        mvc.perform(get(base + latest.get(1).getId()).with(as(data.admin())))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://drive.google.com/file/d/abc"));
    }

    @Test
    @DisplayName("ผู้ยื่นเห็นรายการที่ส่งไปแล้ว แต่เปิดของคำร้องคนอื่นไม่ได้")
    void theApplicantSeesOnlyTheirOwn() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);
        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("ของฉัน.pdf"))
                .with(as(applicant)).with(csrf()));
        Long fileId = revisions.latestRound(request.getId()).get(0).getId();

        String html = mvc.perform(get("/user/academic/request/" + request.getId()).with(as(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("เอกสารฉบับแก้ไขที่ส่งแล้ว", "ของฉัน.pdf");

        UserDtls other = data.otherApplicant();
        AcademicRequest othersRequest = data.evaluation(other, RequestStatus.COMPLETED_REVISE);
        mvc.perform(get("/user/academic/request/" + request.getId() + "/revision/" + fileId).with(as(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/user/academic/request/" + othersRequest.getId() + "/revision/" + fileId).with(as(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ขั้นรอแก้ไข หน้าผู้ยื่นมีฟอร์มแนบหลายรายการและ modal เตือนนำส่งฉบับพิมพ์ (กระดาษ)")
    void theFormWarnsAboutTheOriginal() throws Exception {
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        String html = mvc.perform(get("/user/academic/request/" + request.getId()).with(as(applicant)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("id=\"revisionFilePicker\"", "multiple", "id=\"confirmRevisionModal\"")
                .contains("นำส่งเอกสารฉบับพิมพ์ (กระดาษ)");
    }

    /**
     * ข้อ 14 เป็นงานของเจ้าหน้าที่ — เดิมระบบแจ้งแต่ผู้ยื่น คำร้องจึงรอเงียบ ๆ อยู่ในแดชบอร์ด
     * จนกว่าจะมีคนบังเอิญเปิดดู
     */
    @Test
    @DisplayName("ส่งฉบับแก้แล้ว — เจ้าหน้าที่ได้แจ้งเตือนในระบบและอีเมล พร้อมลิงก์ไปคำร้อง")
    void theOfficerIsToldTheRevisionArrived() throws Exception {
        UserDtls officer = data.admin();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.COMPLETED_REVISE);

        mvc.perform(multipart("/user/academic/upload-revision/" + request.getId())
                .file(pdf("แบบประเมิน-แก้ไข.pdf"))
                .with(as(applicant)).with(csrf()))
                .andExpect(flash().attributeExists("succMsg"));

        String adminLink = "/admin/academic/request/" + request.getId();
        awaitCondition("เจ้าหน้าที่ได้แจ้งเตือนในระบบ", () -> notifications.findAll().stream()
                .anyMatch(n -> n.getType() == NotificationType.REVISION_SUBMITTED
                        && n.getRecipient() != null && n.getRecipient().getId().equals(officer.getId())
                        && adminLink.equals(n.getLink())));
        awaitCondition("เจ้าหน้าที่ได้อีเมล", () -> mail().to(officer.getEmail()).stream()
                .anyMatch(m -> m.subjectContains("ฉบับแก้ไข") && m.bodyContains(adminLink)));
    }
}
