package com.ecom.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * The single gate every outgoing email passes through.
 *
 * <p>Two things must hold at once: rehearsing with a test account must not
 * reach real staff, and a genuine user's notifications must still be delivered.
 * Getting only the first right would make the system silent in production.
 */
@DisplayName("ประตูกลางกันอีเมลจริงตอนเทส")
class GuardedJavaMailSenderTest {

    private final JavaMailSender delegate = mock(JavaMailSender.class);
    private final TestAccountRegistry registry =
            new TestAccountRegistry("user@user.com,admin@admin.com");
    private final GuardedJavaMailSender sender = new GuardedJavaMailSender(delegate, registry);

    private final Session session = Session.getInstance(new Properties());

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    private MimeMessage messageTo(String... recipients) throws Exception {
        MimeMessage message = new MimeMessage(session);
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        helper.setFrom("noreply@kku.ac.th");
        helper.setTo(recipients);
        helper.setSubject("แจ้งเตือน");
        helper.setText("เนื้อหา", true);
        message.saveChanges();
        return message;
    }

    private void signedInAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "x",
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    @Test
    @DisplayName("ผู้ใช้จริงส่งหาผู้ใช้จริง — ต้องส่งออกตามปกติ")
    void ordinaryMailIsDelivered() throws Exception {
        when(delegate.createMimeMessage()).thenReturn(new MimeMessage(session));
        signedInAs("somchai@kku.ac.th");

        sender.send(messageTo("dean@kku.ac.th"));

        verify(delegate).send(any(MimeMessage[].class));
    }

    @Test
    @DisplayName("บัญชีทดสอบเป็นคนกด — ห้ามส่งออกไปหาใครเลย แม้ผู้รับเป็นคนจริง")
    void nothingLeavesWhenATestAccountIsDriving() throws Exception {
        signedInAs("user@user.com");

        sender.send(messageTo("dean@kku.ac.th", "head@kku.ac.th"));

        verify(delegate, never()).send(any(MimeMessage[].class));
    }

    @Test
    @DisplayName("จ่าหน้าถึงบัญชีทดสอบ — ตัดผู้รับนั้นออก แต่คนจริงยังได้รับ")
    void testRecipientsAreDroppedFromAMixedList() throws Exception {
        signedInAs("somchai@kku.ac.th");

        sender.send(messageTo("admin@admin.com", "dean@kku.ac.th"));

        ArgumentCaptor<MimeMessage[]> sent = ArgumentCaptor.forClass(MimeMessage[].class);
        verify(delegate).send(sent.capture());
        assertThat(sent.getValue()).hasSize(1);
        assertThat(sent.getValue()[0].getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("dean@kku.ac.th");
    }

    @Test
    @DisplayName("ผู้รับทุกคนเป็นบัญชีทดสอบ — ไม่ต้องยิง SMTP เลย")
    void nothingIsSentWhenEveryRecipientIsATestAccount() throws Exception {
        signedInAs("somchai@kku.ac.th");

        sender.send(messageTo("user@user.com", "admin@admin.com"));

        verify(delegate, never()).send(any(MimeMessage[].class));
    }

    @Test
    @DisplayName("งาน cron ไม่มีผู้ล็อกอิน — ยังส่งหาคนจริงได้")
    void scheduledJobsStillDeliver() throws Exception {
        SecurityContextHolder.clearContext();

        sender.send(messageTo("dean@kku.ac.th"));

        verify(delegate).send(any(MimeMessage[].class));
    }
}
