package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.RequestStatusHistoryRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * What counts as "still has a request in flight", which is the rule that stops
 * somebody opening a second one.
 *
 * <p>Written because the rule had drifted from the enum that defines it:
 * {@code hasActiveRequest} listed COMPLETED but not
 * COMPLETED_FAIL, so an applicant told their teaching evaluation did not pass
 * could never ask to be evaluated again — with no way out, since the block is
 * on the only page that starts a new request.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_acad_active",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
@DisplayName("คำร้องประเมินการสอน: สถานะไหนถือว่ายังดำเนินการอยู่")
class AcademicRequestActiveStateTest {

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private AcademicRequestRepository requestRepository;

    @Autowired
    private RequestStatusHistoryRepository historyRepository;

    @Autowired
    private UserRepository userRepository;

    private UserDtls applicant;
    private UserDtls admin;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        requestRepository.deleteAll();

        applicant = createUser("acad-applicant-" + java.util.UUID.randomUUID() + "@test.com", "ROLE_USER");
        admin = createUser("acad-admin-" + java.util.UUID.randomUUID() + "@test.com", "ROLE_ADMIN");
    }

    private UserDtls createUser(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("ผู้ใช้ " + email);
        u.setPassword("{noop}x");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setEmailNotificationEnabled(false);
        return userRepository.save(u);
    }

    /**
     * A request sitting at the given status.
     *
     * <p>Set directly rather than walked there through {@code updateStatus}.
     * These tests are about {@code hasActiveRequest} — what a status *means* —
     * not about how a request reaches one, and the transition guard rightly
     * refuses the jumps this used to make (a draft cannot become
     * "แจ้งผล - ไม่ผ่าน" in one step). Walking the real sequence here would add
     * a dozen irrelevant lines to every case.
     */
    private AcademicRequest requestAt(RequestStatus status) {
        AcademicRequest request = requestService.createDraftRequest(applicant);
        request.setCurrentStatus(status);
        return requestService.save(request);
    }

    @Test
    @DisplayName("ผลประเมิน 'ไม่ผ่าน' ต้องขอประเมินใหม่ได้")
    void aFailedEvaluationDoesNotBlockANewRequest() {
        requestAt(RequestStatus.COMPLETED_FAIL);

        assertThat(requestService.hasActiveRequest(applicant.getId()))
                .as("ถูกบล็อกตรงนี้เท่ากับปิดทางก้าวหน้าทางวิชาการถาวร")
                .isFalse();
    }

    @Test
    @DisplayName("คำร้องที่ไม่ผ่าน และที่เสร็จสิ้นแล้ว ก็ยื่นใหม่ได้")
    void failedAndCompletedDoNotBlockEither() {
        requestAt(RequestStatus.COMPLETED_FAIL);
        assertThat(requestService.hasActiveRequest(applicant.getId())).isFalse();

        requestRepository.deleteAll();
        requestAt(RequestStatus.COMPLETED);
        assertThat(requestService.hasActiveRequest(applicant.getId())).isFalse();
    }

    @Test
    @DisplayName("แบบร่างที่ยังทำไม่เสร็จไม่นับเป็นคำร้องที่ค้างอยู่")
    void anUnfinishedDraftIsNotAnActiveRequest() {
        requestService.createDraftRequest(applicant);

        // ฝั่งนี้ต้องกัน DRAFT ออกจาก active เพราะ newRequestForm หา/สร้าง
        // แบบร่างหลังด่านนี้ — ถ้านับเป็น active ผู้ใช้จะกลับเข้าร่างตัวเองไม่ได้
        assertThat(requestService.hasActiveRequest(applicant.getId())).isFalse();
    }

    @Test
    @DisplayName("คำร้องที่ยังเดินอยู่ และผลที่ยังใช้ต่อได้ ต้องยังบล็อกการยื่นใหม่")
    void requestsStillInFlightKeepBlocking() {
        for (RequestStatus status : new RequestStatus[] {
                RequestStatus.RECEIVED,
                RequestStatus.SUB_COMMITTEE_APPOINTED,
                RequestStatus.MEETING_SCHEDULED,
                // ผลที่ผ่านยังใช้ยื่นขอตำแหน่งต่อได้ ส่วนที่ต้องแก้ไขก็ยังไม่จบ
                RequestStatus.COMPLETED_PASS,
                RequestStatus.COMPLETED_REVISE }) {

            requestRepository.deleteAll();
            requestAt(status);

            assertThat(requestService.hasActiveRequest(applicant.getId()))
                    .as("%s ยังไม่จบ จึงต้องบล็อกการยื่นใหม่", status)
                    .isTrue();
        }
    }

    /**
     * Ties the rule to the enum that declares it, so a status added later cannot
     * quietly mean two different things in two places.
     */
    @Test
    @DisplayName("ทุกสถานะที่ enum ประกาศว่าจบแล้ว ต้องยื่นคำร้องใหม่ได้")
    void everyTerminalStatusEndsTheRequest() {
        for (RequestStatus status : RequestStatus.values()) {
            if (!status.isTerminal()) {
                continue;
            }
            requestRepository.deleteAll();
            requestAt(status);

            assertThat(requestService.hasActiveRequest(applicant.getId()))
                    .as("%s ประกาศตัวว่าเป็นสถานะจบ จึงต้องยื่นคำร้องใหม่ได้", status)
                    .isFalse();
        }
    }
}
