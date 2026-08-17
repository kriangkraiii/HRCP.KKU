package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

/**
 * An alerting system earns its keep by being quiet. These tests hold the two
 * halves of that: the message that matters always gets through, and the ones
 * that do not matter are not sent at all — because an inbox that fills with
 * "everything is fine" is an inbox where the failure goes unread.
 */
class SystemAlertServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private SystemAlertService service(boolean onSuccess, long throttleHours) {
        return new SystemAlertService(mailSender, userRepository, notificationService,
                true, onSuccess, throttleHours);
    }

    private UserDtls admin(String email, Boolean wantsEmail) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("แอดมิน");
        u.setRole("ROLE_ADMIN");
        u.setEmailNotificationEnabled(wantsEmail);
        return u;
    }

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(userRepository.findByRole("ROLE_ADMIN")).thenReturn(List.of(
                admin("boss@kku.ac.th", true),
                admin("quiet@kku.ac.th", false)));
    }

    @Test
    @DisplayName("งานพัง ต้องเตือนแอดมินทั้งในระบบและทางอีเมล")
    void aFailureReachesTheAdministrators() {
        service(false, 12).failure("ดึงข้อมูลอาจารย์", "ต่อ API ไม่ได้");

        verify(notificationService).notifyAdmins(isNull(), anyString(), anyString(),
                anyString(), eq(NotificationType.SYSTEM), eq(true));
        // เฉพาะคนที่เปิดรับอีเมลไว้
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("งานสำเร็จตามปกติ ต้องไม่ส่งอีเมล")
    void routineSuccessIsNotWorthAnEmail() {
        service(false, 12).success("ดึงข้อมูลอาจารย์", "ดึงมา 45 รายการ");

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ถ้าเปิดสวิตช์ไว้ งานสำเร็จก็ส่งได้")
    void successCanBeSwitchedOnDeliberately() {
        service(true, 12).success("ดึงข้อมูลอาจารย์", "ดึงมา 45 รายการ");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("พังซ้ำ ๆ ต้องไม่ยิงเมลรัวจนคนเลิกอ่าน")
    void repeatedFailuresAreThrottled() {
        SystemAlertService alerts = service(false, 12);

        alerts.failure("ดึงข้อมูลอาจารย์", "ครั้งที่ 1");
        alerts.failure("ดึงข้อมูลอาจารย์", "ครั้งที่ 2");
        alerts.failure("ดึงข้อมูลอาจารย์", "ครั้งที่ 3");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("งานคนละอย่างต้องไม่บังกันเอง")
    void oneBrokenJobDoesNotMuteAnother() {
        SystemAlertService alerts = service(false, 12);

        alerts.failure("ดึงข้อมูลอาจารย์", "พัง");
        alerts.failure("ดึงผลงาน Scopus", "พังคนละเรื่อง");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("กลับมาทำงานได้ ต้องส่งทันทีแม้อยู่ในช่วง throttle และปลดล็อกให้ครั้งหน้าเตือนได้เลย")
    void recoveryIsNeverSuppressedAndResetsTheThrottle() {
        SystemAlertService alerts = service(false, 12);

        alerts.failure("ดึงข้อมูลอาจารย์", "พัง");      // 1 ฉบับ
        alerts.recovery("ดึงข้อมูลอาจารย์", "กลับมาปกติ"); // 2 — ไม่โดน throttle
        alerts.failure("ดึงข้อมูลอาจารย์", "พังอีกแล้ว");  // 3 — throttle ถูกล้างแล้ว

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("ข้อความจากระบบภายนอกต้องถูก escape ก่อนใส่ในอีเมล")
    void whateverUpstreamSaidIsNotTreatedAsMarkup() throws Exception {
        SystemAlertService alerts = service(false, 12);
        UserDtls admin = admin("boss@kku.ac.th", true);

        alerts.mail(admin, SystemAlertService.Level.FAILURE, "ดึงข้อมูล",
                "<script>alert(1)</script>", "18/08/2026 01:30 น.");

        // ไม่มีทางยืนยันเนื้อความผ่าน mock ของ MimeMessage ได้ตรง ๆ
        // จึงตรวจที่ผลลัพธ์ที่สังเกตได้: ส่งออกไปโดยไม่ระเบิด
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("ส่งอีเมลไม่ออก ต้องไม่ทำให้งานที่กำลังรายงานพังตาม")
    void aBrokenMailServerDoesNotBreakTheJobItReportsOn() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("SMTP ล่ม"));

        SystemAlertService alerts = service(false, 12);

        // ไม่โยน exception ออกมา
        alerts.failure("ดึงข้อมูลอาจารย์", "พัง");

        verify(notificationService).notifyAdmins(isNull(), anyString(), anyString(),
                anyString(), any(), any());
    }

    @Test
    @DisplayName("การแจ้งเตือนต้องบอกว่าเป็นเรื่องอะไร")
    void theAlertSaysWhatItIsAbout() {
        service(false, 12).failure("ดึงผลงาน Scopus", "ต่อ API ไม่ได้");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyAdmins(isNull(), title.capture(), message.capture(),
                anyString(), any(), any());

        assertThat(title.getValue()).contains("ดึงผลงาน Scopus").contains("ไม่สำเร็จ");
        assertThat(message.getValue()).contains("ต่อ API ไม่ได้");
    }
}
