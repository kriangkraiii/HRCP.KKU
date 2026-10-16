package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.ecom.model.UserDtls;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * อีเมลแจ้งปัญหาส่งบนอีก thread หลังคำขอจบ — ภาพแนบต้องถูกอ่านเข้าหน่วยความจำก่อน
 *
 * <p>เดิมส่ง {@code MultipartFile} ข้ามไป ไฟล์ชั่วคราวของมันถูก Tomcat ลบเมื่อคำขอจบ การอ่านภาพจึงล้ม
 * และอีเมลทั้งฉบับไม่ออก ทั้งที่หน้าเว็บบอกผู้ใช้ว่าส่งแล้ว
 */
@DisplayName("อีเมลแจ้งปัญหา: ภาพแนบไปถึงผู้รับ")
class FeedbackServiceTest {

    @Test
    @DisplayName("ภาพที่อ่านไว้แล้วถูกแนบไปกับอีเมล แม้ไฟล์อัปโหลดต้นทางจะไม่อยู่แล้ว")
    void attachedImagesReachTheEmail() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        FeedbackService service = new FeedbackService(mailSender);
        ReflectionTestUtils.setField(service, "recipientsCsv", "hr@kku.ac.th");
        ReflectionTestUtils.setField(service, "senderEmail", "noreply@kku.ac.th");

        byte[] png = { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };
        FeedbackService.Image image = FeedbackService.Image.of(
                new MockMultipartFile("images", "หน้าจอ.png", "image/png", png));
        UserDtls sender = new UserDtls();
        sender.setEmail("somchai@kku.ac.th");
        sender.setName("สมชาย");

        service.sendFeedback(sender, "ปัญหาระบบ", "กดบันทึกไม่ได้", "รายละเอียด", List.of(image));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent.getValue().writeTo(raw);
        assertThat(raw.toString(java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("image/png")
                .contains(java.util.Base64.getMimeEncoder().encodeToString(png));
    }
}
