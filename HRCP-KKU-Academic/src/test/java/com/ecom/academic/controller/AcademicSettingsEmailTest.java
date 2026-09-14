package com.ecom.academic.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;
import com.ecom.service.TwoFactorService;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;

class AcademicSettingsEmailTest {

    private UserRepository userRepository;
    private TwoFactorService twoFactorService;
    private AdminLogService adminLogService;
    private HttpServletRequest httpRequest;
    private JavaMailSender mailSender;
    private AcademicSettingsController controller;

    private UserDtls adminUser;
    private UserDtls normalUser;
    private Principal adminPrincipal;
    private Principal normalUserPrincipal;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        twoFactorService = mock(TwoFactorService.class);
        adminLogService = mock(AdminLogService.class);
        httpRequest = mock(HttpServletRequest.class);
        mailSender = mock(JavaMailSender.class);

        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        controller = new AcademicSettingsController(
                userRepository,
                twoFactorService,
                adminLogService,
                httpRequest,
                mailSender
        );

        adminUser = new UserDtls();
        adminUser.setId(1);
        adminUser.setEmail("admin@kku.ac.th");
        adminUser.setName("ผู้ดูแลระบบ ระบบทดสอบ");
        adminUser.setRole("ROLE_ADMIN");

        normalUser = new UserDtls();
        normalUser.setId(2);
        normalUser.setEmail("lecturer@kku.ac.th");
        normalUser.setName("อาจารย์ ทดสอบ");
        normalUser.setRole("ROLE_USER");

        adminPrincipal = () -> "admin@kku.ac.th";
        normalUserPrincipal = () -> "lecturer@kku.ac.th";

        when(userRepository.findByEmail("admin@kku.ac.th")).thenReturn(adminUser);
        when(userRepository.findByEmail("lecturer@kku.ac.th")).thenReturn(normalUser);
    }

    @Test
    @DisplayName("Admin ส่งอีเมลทดสอบไปยังอีเมลที่ระบุเองสำเร็จ")
    void testSendTestEmailSuccess() {
        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                "custom.recipient@example.ac.th",
                adminPrincipal
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("custom.recipient@example.ac.th", response.getBody().get("recipient"));

        // Verify mailSender.send was called
        verify(mailSender, times(1)).send(any(MimeMessage.class));

        // Verify audit log
        verify(adminLogService, times(1)).log(
                eq("admin@kku.ac.th"),
                eq("ผู้ดูแลระบบ ระบบทดสอบ"),
                eq("TEST_EMAIL_RELAY"),
                contains("custom.recipient@example.ac.th"),
                any()
        );
    }

    @Test
    @DisplayName("Admin ส่งอีเมลทดสอบโดยไม่ระบุ targetEmail จะใช้อีเมลของ admin เอง")
    void testSendTestEmailDefaultAdminEmail() {
        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                null,
                adminPrincipal
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
        assertEquals("admin@kku.ac.th", response.getBody().get("recipient"));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("รูปแบบอีเมลไม่ถูกต้อง จะตอบกลับ 400 Bad Request")
    void testSendTestEmailInvalidFormat() {
        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                "invalid-email-address",
                adminPrincipal
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("รูปแบบอีเมลไม่ถูกต้อง"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("ผู้ใช้ทั่วไป (ROLE_USER) ไม่มีสิทธิ์เรียกใช้งาน ตอบกลับ 403 Forbidden")
    void testSendTestEmailForbiddenForRegularUser() {
        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                "test@kkumail.com",
                normalUserPrincipal
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("ไม่ได้ล็อกอิน (Principal == null) ตอบกลับ 401 Unauthorized")
    void testSendTestEmailUnauthorized() {
        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                "test@kkumail.com",
                null
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("หาก SMTP ขัดข้องหรือปฏิเสธ จะดักจับ Exception และตอบกลับข้อมูลอย่างนุ่มนวล")
    void testSendTestEmailMailExceptionHandled() {
        doThrow(new MailSendException("550 5.1.0 Address rejected")).when(mailSender).send(any(MimeMessage.class));

        ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(
                "rejected@external.com",
                adminPrincipal
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertTrue(response.getBody().get("message").toString().contains("550 5.1.0 Address rejected"));
    }
}
