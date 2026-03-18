package com.ecom.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class TwoFactorService {

    private static final int OTP_LENGTH = 8;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JavaMailSender mailSender;

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
                subject = "รหัส OTP สำหรับเข้าสู่ระบบ - HRCP.KKU";
                purposeText = "เข้าสู่ระบบ";
            } else {
                subject = "รหัส OTP สำหรับยืนยันอีเมล - HRCP.KKU";
                purposeText = "ยืนยันอีเมล";
            }

            String body = buildOtpEmailBody(user.getName(), otp, purposeText);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("OTP email failed: " + e.getMessage());
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

    private String buildOtpEmailBody(String name, String otp, String purpose) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:Sarabun,sans-serif;margin:0;padding:0;background:#f5f5f5;'>");
        sb.append("<div style='max-width:500px;margin:30px auto;background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.1);'>");

        // Header
        sb.append("<div style='background:linear-gradient(135deg,#1a237e,#0d47a1);padding:30px;text-align:center;'>");
        sb.append("<h1 style='color:#fff;margin:0;font-size:22px;'>🔐 รหัส OTP สำหรับ").append(purpose).append("</h1>");
        sb.append("</div>");

        // Body
        sb.append("<div style='padding:30px;'>");
        sb.append("<p style='font-size:16px;color:#333;'>เรียน ").append(name != null ? name : "ผู้ใช้").append("</p>");
        sb.append("<p style='font-size:15px;color:#555;'>รหัส OTP ของคุณคือ:</p>");

        // OTP Display
        sb.append("<div style='text-align:center;margin:25px 0;'>");
        sb.append("<div style='display:inline-block;background:#f0f4ff;border:2px dashed #1a237e;border-radius:12px;padding:18px 35px;'>");
        sb.append("<span style='font-size:36px;font-weight:700;color:#1a237e;letter-spacing:8px;font-family:monospace;'>").append(otp).append("</span>");
        sb.append("</div>");
        sb.append("</div>");

        sb.append("<p style='font-size:14px;color:#888;text-align:center;'>⏱ รหัสนี้จะหมดอายุใน <strong>").append(OTP_EXPIRY_MINUTES).append(" นาที</strong></p>");

        sb.append("<hr style='border:none;border-top:1px solid #eee;margin:20px 0;'>");
        sb.append("<p style='font-size:13px;color:#aaa;'>หากคุณไม่ได้ทำรายการนี้ กรุณาเพิกเฉยอีเมลนี้</p>");
        sb.append("<p style='font-size:13px;color:#aaa;'>วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น</p>");
        sb.append("</div>");

        sb.append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
