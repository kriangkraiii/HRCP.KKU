package com.ecom.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class TwoFactorService {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorService.class);
    private static final int OTP_LENGTH = 8;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;

    private final JavaMailSender mailSender;

    public TwoFactorService(UserRepository userRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    /**
     * Generate an 8-digit OTP, save to user, and return it.
     */
    public String generateOtp(UserDtls user) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        String otp = sb.toString();
        user.setOtpCode(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        userRepository.save(user);
        return otp;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    /**
     * Send OTP email to user.
     * @param purpose "LOGIN" or "EMAIL_VERIFY"
     */
    @Async
    public void sendOtpEmail(UserDtls user, String purpose) {
        try {
            String otp = user.getOtpCode();
            if (otp == null) return;

            String subject;
            String purposeText;
            if ("LOGIN".equals(purpose)) {
                subject = "รหัส OTP สำหรับเข้าสู่ระบบ - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น";
                purposeText = "เข้าสู่ระบบ";
            } else {
                subject = "รหัส OTP สำหรับยืนยันอีเมล - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น";
                purposeText = "ยืนยันอีเมล";
            }

            String body = com.ecom.util.EmailTemplateHelper.buildOtpEmail(user.getName(), otp, purposeText, OTP_EXPIRY_MINUTES);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(senderEmail, com.ecom.util.EmailTemplateHelper.SENDER_NAME);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            com.ecom.util.EmailTemplateHelper.attachLogos(helper);
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("OTP email failed for user {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Verify the OTP input against saved code.
     * Returns: "OK", "EXPIRED", "INVALID"
     */
    public String verifyOtp(UserDtls user, String inputOtp) {
        if (user.getOtpCode() == null || user.getOtpExpiry() == null) {
            return "INVALID";
        }
        if (LocalDateTime.now().isAfter(user.getOtpExpiry())) {
            clearOtp(user);
            return "EXPIRED";
        }
        if (!user.getOtpCode().equals(inputOtp)) {
            return "INVALID";
        }
        clearOtp(user);
        return "OK";
    }

    /**
     * Clear OTP data from user.
     */
    public void clearOtp(UserDtls user) {
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
    }

    /**
     * Mask email for display, e.g., s***@gmail.com
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@");
        String local = parts[0];
        if (local.length() <= 1) return local + "***@" + parts[1];
        return local.charAt(0) + "***@" + parts[1];
    }
}
