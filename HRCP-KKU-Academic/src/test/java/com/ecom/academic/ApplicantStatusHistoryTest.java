package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;
import com.ecom.academic.repository.RequestStatusHistoryRepository;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ผู้ยื่นเห็นประวัติสถานะคำร้องเฟส 1 เหมือนเฟส 2 — แต่ไม่เห็นหมายเหตุ ซึ่งมีวันนัดประชุมคณะอนุกรรมการ (ความลับทางราชการ)
 * และตัวเลขไฟล์แนบของแอดมินบอกเพดานเดียวกับที่เซิร์ฟเวอร์บังคับ (20 ไฟล์)
 */
@DisplayName("ประวัติสถานะฝั่งผู้ยื่น และเพดานไฟล์แนบบนหน้าแอดมิน")
class ApplicantStatusHistoryTest extends AbstractFlowTest {

    private static final String MEETING_NOTE = "นัดหมายวันประชุม: 20 ตุลาคม 2569 13:30 น.";

    @Autowired
    private RequestStatusHistoryRepository history;

    private void recorded(AcademicRequest request, RequestStatus status, String note) {
        RequestStatusHistory h = new RequestStatusHistory();
        h.setRequest(request);
        h.setNewStatus(status);
        h.setNote(note);
        history.save(h);
    }

    private String page(String url, UserDtls who, String role) throws Exception {
        return mvc.perform(get(url).with(user(who.getEmail()).roles(role)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("ผู้ยื่นเห็นสถานะที่ผ่านมา แต่ไม่เห็นหมายเหตุที่มีวันนัดประชุม")
    void applicantSeesStatusesButNotTheMeetingNote() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
        recorded(request, RequestStatus.RECEIVED, "รับเรื่องแล้ว");
        recorded(request, RequestStatus.MEETING_SCHEDULED, MEETING_NOTE);

        String html = page("/user/academic/request/" + request.getId(), applicant, "USER");

        assertThat(html).contains("id=\"applicantStatusHistory\"");
        assertThat(html).contains(RequestStatus.RECEIVED.getThaiLabel());
        assertThat(html).as("วันนัดประชุมเป็นความลับ").doesNotContain("20 ตุลาคม 2569 13:30").doesNotContain(MEETING_NOTE);
    }

    @Test
    @DisplayName("หน้าแอดมินทั้งสองเฟสบอกเพดานไฟล์แนบ 20 ไฟล์ ตรงกับที่เซิร์ฟเวอร์บังคับ")
    void attachmentBadgeShowsTheRealLimit() throws Exception {
        UserDtls applicant = data.applicant();
        UserDtls admin = data.admin();
        AcademicRequest evaluation = data.evaluation(applicant, RequestStatus.RECEIVED);
        var position = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED,
                data.evaluation(applicant, RequestStatus.COMPLETED));

        assertThat(page("/admin/academic/request/" + evaluation.getId(), admin, "ADMIN"))
                .contains("0 / 20 ไฟล์").doesNotContain("/ 10 ไฟล์");
        assertThat(page("/admin/position/request/" + position.getId(), admin, "ADMIN"))
                .contains("0 / 20 ไฟล์").doesNotContain("/ 10 ไฟล์");
    }
}
