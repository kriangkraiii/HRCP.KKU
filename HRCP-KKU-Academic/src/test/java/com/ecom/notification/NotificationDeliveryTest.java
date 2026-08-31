package com.ecom.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.academic.service.PositionEmailService;
import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * What the system tells people, and who it tells.
 *
 * <p>Every notification here has two halves that fail independently: a row in
 * the in-app inbox and an e-mail. The e-mail half is the one with real-world
 * consequences — steps 11, 23 and 29 of the flow are all "แจ้งผลให้ผู้เสนอขอฯ
 * ทราบ" — so both are asserted separately rather than assuming one implies the
 * other.
 *
 * <p>No message leaves the JVM: the transport is
 * {@link com.ecom.support.RecordingMailSender}, installed for the whole suite by
 * {@link AbstractFlowTest}.
 */
@DisplayName("การแจ้งเตือน: อีเมลและกล่องข้อความในระบบ")
class NotificationDeliveryTest extends AbstractFlowTest {

    @Autowired
    private AcademicEmailService academicEmail;

    @Autowired
    private PositionEmailService positionEmail;

    @Autowired
    private NotificationRepository notifications;

    private List<Notification> inboxOf(UserDtls user) {
        return notifications.findByRecipientOrderByCreatedAtDesc(user);
    }

    @Nested
    @DisplayName("เฟส 1 — ประเมินการสอน")
    class AcademicNotifications {

        @Test
        @DisplayName("เปลี่ยนสถานะ → ผู้ยื่นได้ทั้งการแจ้งเตือนในระบบและอีเมล")
        void statusChangeReachesApplicant() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);

            academicEmail.sendStatusChangeEmail(request.getId(),
                    RequestStatus.MEETING_SCHEDULED, RequestStatus.COMPLETED_PASS);

            awaitMailCount(1);

            assertThat(mail().to(TestDataFactory.APPLICANT_EMAIL))
                    .as("ผู้ยื่นต้องได้รับอีเมลแจ้งผล")
                    .hasSize(1)
                    .allSatisfy(sent -> assertThat(sent.subjectContains("แจ้งผล - ผ่าน")).isTrue());

            awaitCondition("การแจ้งเตือนในระบบ", () -> !inboxOf(applicant).isEmpty());
            assertThat(inboxOf(applicant))
                    .singleElement()
                    .satisfies(n -> {
                        assertThat(n.getType()).isEqualTo(NotificationType.ACADEMIC_STATUS_UPDATE);
                        assertThat(n.getTitle()).contains("แจ้งผล - ผ่าน");
                        assertThat(n.getLink()).isEqualTo("/user/academic/request/" + request.getId());
                    });
        }

        /**
         * The four outcomes an applicant must not miss are flagged important, so
         * they survive a full inbox. A routine step forward is not.
         */
        @Test
        @DisplayName("ผลลัพธ์ปลายทาง (ผ่าน/แก้ไข/ไม่ผ่าน/ไม่รับคำร้อง) ถูกทำเครื่องหมายว่าสำคัญ")
        void terminalOutcomesAreMarkedImportant() {
            UserDtls applicant = data.applicant();

            for (RequestStatus outcome : List.of(RequestStatus.COMPLETED_PASS,
                    RequestStatus.COMPLETED_REVISE, RequestStatus.COMPLETED_FAIL,
                    RequestStatus.REJECTED)) {

                AcademicRequest request = data.evaluation(applicant, RequestStatus.MEETING_SCHEDULED);
                academicEmail.sendStatusChangeEmail(request.getId(),
                        RequestStatus.MEETING_SCHEDULED, outcome);
            }

            awaitCondition("การแจ้งเตือนครบ 4 รายการ", () -> inboxOf(applicant).size() >= 4);
            assertThat(inboxOf(applicant))
                    .as("ทั้งสี่สถานะปลายทางต้องเป็น important")
                    .allSatisfy(n -> assertThat(n.getIsImportant()).isTrue());
        }

        @Test
        @DisplayName("ก้าวหน้าตามปกติ (นัดหมายวันประชุม) ไม่ถูกทำเครื่องหมายว่าสำคัญ")
        void routineProgressIsNotImportant() {
            UserDtls applicant = data.applicant();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);

            academicEmail.sendStatusChangeEmail(request.getId(),
                    RequestStatus.SUB_COMMITTEE_APPOINTED, RequestStatus.MEETING_SCHEDULED);

            awaitCondition("การแจ้งเตือน", () -> !inboxOf(applicant).isEmpty());
            assertThat(inboxOf(applicant).get(0).getIsImportant()).isFalse();
        }

        @Test
        @DisplayName("คำร้องใหม่ → แอดมินทุกคนได้รับแจ้ง")
        void newRequestReachesAdmins() {
            UserDtls applicant = data.applicant();
            UserDtls admin = data.admin();
            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);

            academicEmail.sendNewRequestNotificationToAdmins(request.getId());

            awaitCondition("แอดมินได้รับการแจ้งเตือน", () -> !inboxOf(admin).isEmpty());
            assertThat(inboxOf(admin))
                    .anySatisfy(n -> {
                        assertThat(n.getType()).isEqualTo(NotificationType.ACADEMIC_NEW_REQUEST);
                        assertThat(n.getMessage()).contains(applicant.getName());
                        assertThat(n.getLink()).isEqualTo("/admin/academic/request/" + request.getId());
                    });

            awaitCondition("อีเมลถึงแอดมิน", () -> !mail().to(TestDataFactory.ADMIN_EMAIL).isEmpty());
        }

        @Test
        @DisplayName("แอดมินที่ถูกปิดบัญชี ไม่ได้รับทั้งอีเมลและการแจ้งเตือน")
        void disabledAdminIsSkipped() {
            UserDtls applicant = data.applicant();
            UserDtls disabled = data.user("retired@" + TestDataFactory.DOMAIN,
                    "อดีต", "ผู้ดูแล", "ROLE_ADMIN");
            disabled.setIsEnable(false);
            data.saveUser(disabled);

            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicEmail.sendNewRequestNotificationToAdmins(request.getId());

            settle();
            assertThat(inboxOf(disabled)).as("บัญชีที่ปิดแล้วต้องไม่มีการแจ้งเตือน").isEmpty();
            assertThat(mail().to("retired@" + TestDataFactory.DOMAIN))
                    .as("บัญชีที่ปิดแล้วต้องไม่ได้รับอีเมล").isEmpty();
        }

        /**
         * The in-app inbox and the e-mail opt-out are separate settings, and the
         * distinction matters: turning off e-mail must not also make a person
         * stop seeing that a request is waiting for them.
         */
        @Test
        @DisplayName("แอดมินที่ปิดรับอีเมล ยังได้การแจ้งเตือนในระบบ แต่ไม่ได้อีเมล")
        void adminOptedOutOfEmailStillSeesInAppNotification() {
            UserDtls applicant = data.applicant();
            UserDtls quiet = data.user("quiet@" + TestDataFactory.DOMAIN,
                    "เงียบ", "ผู้ดูแล", "ROLE_ADMIN");
            quiet.setEmailNotificationEnabled(false);
            data.saveUser(quiet);

            AcademicRequest request = data.evaluation(applicant, RequestStatus.RECEIVED);
            academicEmail.sendNewRequestNotificationToAdmins(request.getId());

            awaitCondition("การแจ้งเตือนในระบบ", () -> !inboxOf(quiet).isEmpty());
            assertThat(mail().to("quiet@" + TestDataFactory.DOMAIN))
                    .as("ปิดรับอีเมลแล้วต้องไม่ได้รับอีเมล").isEmpty();
        }
    }

    @Nested
    @DisplayName("เฟส 2 — ขอกำหนดตำแหน่ง")
    class PositionNotifications {

        @Test
        @DisplayName("เปลี่ยนสถานะ → ผู้ยื่นได้ทั้งการแจ้งเตือนในระบบและอีเมล")
        void statusChangeReachesApplicant() {
            UserDtls applicant = data.applicant();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.COLLEGE_COMMITTEE, null);

            positionEmail.sendStatusChangeEmail(request.getId(),
                    PositionRequestStatus.COLLEGE_COMMITTEE, PositionRequestStatus.COLLEGE_APPROVED);

            awaitMailCount(1);
            assertThat(mail().to(TestDataFactory.APPLICANT_EMAIL)).hasSize(1);

            awaitCondition("การแจ้งเตือนในระบบ", () -> !inboxOf(applicant).isEmpty());
            assertThat(inboxOf(applicant))
                    .anySatisfy(n -> assertThat(n.getType())
                            .isEqualTo(NotificationType.POSITION_STATUS_UPDATE));
        }

        @Test
        @DisplayName("ส่งออกกองทรัพยากรบุคคล (สถานะสุดท้าย) ต้องแจ้งผู้ยื่นด้วย")
        void finalHandoverIsAnnounced() {
            UserDtls applicant = data.applicant();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.COLLEGE_APPROVED, null);

            positionEmail.sendStatusChangeEmail(request.getId(),
                    PositionRequestStatus.COLLEGE_APPROVED, PositionRequestStatus.SENT_TO_HR);

            awaitMailCount(1);
            assertThat(mail().to(TestDataFactory.APPLICANT_EMAIL))
                    .singleElement()
                    .satisfies(sent -> assertThat(sent.subjectContains("กองทรัพยากรบุคคล")).isTrue());
        }
    }

    @Nested
    @DisplayName("ประตูกันอีเมลจริง ทำงานผ่านเส้นทางจริงของระบบ")
    class SuppressionThroughRealPath {

        /**
         * The unit test for {@code GuardedJavaMailSender} covers the rule.
         * This covers the wiring: that the guard is actually in the chain a
         * notification travels down, which is the part a configuration change
         * can silently break.
         */
        @Test
        @DisplayName("บัญชีทดสอบเป็นผู้ยื่น — ไม่มีอีเมลออกไปหาแอดมินตัวจริงเลย")
        void rehearsalByTestAccountSendsNothing() {
            UserDtls tester = data.user("user@user.com", "ผู้ใช้", "ทดสอบ", "ROLE_USER");
            data.admin();
            AcademicRequest request = data.evaluation(tester, RequestStatus.RECEIVED);

            authenticateAs("user@user.com");
            try {
                academicEmail.sendNewRequestNotificationToAdmins(request.getId());
                settle();
            } finally {
                SecurityContextHolder.clearContext();
            }

            assertThat(mail().captured())
                    .as("การซ้อมด้วยบัญชีทดสอบต้องไม่ส่งอีเมลถึงใครทั้งสิ้น")
                    .isEmpty();
        }

        @Test
        @DisplayName("ผู้ยื่นเป็นบัญชีทดสอบ — ไม่ส่งอีเมลถึงตัวเอง")
        void testAccountRecipientIsDropped() {
            UserDtls tester = data.user("user@user.com", "ผู้ใช้", "ทดสอบ", "ROLE_USER");
            AcademicRequest request = data.evaluation(tester, RequestStatus.MEETING_SCHEDULED);

            academicEmail.sendStatusChangeEmail(request.getId(),
                    RequestStatus.MEETING_SCHEDULED, RequestStatus.COMPLETED_PASS);

            settle();
            assertThat(mail().to("user@user.com")).isEmpty();
        }
    }

    /** Signs in on this thread, so the guard can see who set an action off. */
    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
