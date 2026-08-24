package com.ecom.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.mail.internet.MimeMessage;

@Service
public class TwoFactorService {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorService.class);
    private static final int OTP_LENGTH = 8;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private final SecureRandom random = new SecureRandom();

    /**
     * In-memory cache for fast OTP verification. Keyed by user ID, stores
     * the SHA-256 hash of the OTP. TTL is set slightly beyond the OTP expiry
     * to allow for clock skew while still auto-evicting stale entries.
     */
    private final Cache<Integer, String> otpCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(OTP_EXPIRY_MINUTES + 1))
            .build();

    private final UserRepository userRepository;

    private final JavaMailSender mailSender;

    public TwoFactorService(UserRepository userRepository, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    /**
     * Generate an 8-digit OTP, hash it, save the hash to DB and cache,
     * and return the plaintext for email delivery only.
     */
    public String generateOtp(UserDtls user) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        String otp = sb.toString();
        String hashedOtp = sha256(otp);

        // Store hash only — plaintext never touches persistent storage
        user.setOtpCode(hashedOtp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        userRepository.save(user);

        // Fast-path for verification (avoids DB round-trip)
        otpCache.put(user.getId(), hashedOtp);

        return otp;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
    private String senderEmail;

    /**
     * Send OTP email to user.
     *
     * <p>The plaintext OTP is retrieved by re-generating from the caller
     * (generateOtp returns it). This method receives the user whose DB row
     * now holds only the hash, so it must be called with the plaintext
     * already captured.
     *
     * @param purpose "LOGIN" or "EMAIL_VERIFY"
     */
    @Async
    public void sendOtpEmail(UserDtls user, String otp, String purpose) {
        try {
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

            if (com.ecom.util.EmailTemplateHelper.isTestEmail(user.getEmail())) {
                logger.info("Test account OTP generated for {} [{}] => OTP: {}", user.getEmail(), purpose, otp);
                return;
            }

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
     * Verify the OTP input against the stored hash.
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

        String inputHash = sha256(inputOtp);

        // Fast path: check in-memory cache first
        String cachedHash = otpCache.getIfPresent(user.getId());
        String storedHash = cachedHash != null ? cachedHash : user.getOtpCode();

        if (!inputHash.equals(storedHash)) {
            return "INVALID";
        }
        clearOtp(user);
        return "OK";
    }

    /**
     * Clear OTP data from user and cache.
     */
    public void clearOtp(UserDtls user) {
        user.setOtpCode(null);
        user.setOtpExpiry(null);
        userRepository.save(user);
        otpCache.invalidate(user.getId());
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

    /**
     * One-way SHA-256 hash. OTP is an 8-digit number so it is not
     * computationally expensive to brute-force, but this still prevents
     * casual exposure from a database dump or backup.
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — this cannot happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
