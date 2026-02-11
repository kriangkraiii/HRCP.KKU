package com.ecom.util;

import java.io.UnsupportedEncodingException;
import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CommonUtil {

	@Autowired
	private JavaMailSender mailSender;

	@Autowired
	private UserService userService;

	public Boolean sendMail(String url, String reciepentEmail) throws UnsupportedEncodingException, MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);

		helper.setFrom("seven1aaplus@gmail.com", "ระบบตำแหน่งทางวิชาการ");
		helper.setTo(reciepentEmail);

		String content = "<p>สวัสดี,</p>"
				+ "<p>คุณได้ร้องขอการรีเซ็ตรหัสผ่าน</p>"
				+ "<p>คลิกลิงก์ด้านล่างเพื่อเปลี่ยนรหัสผ่าน:</p>"
				+ "<p><a href=\"" + url + "\">เปลี่ยนรหัสผ่าน</a></p>";

		helper.setSubject("รีเซ็ตรหัสผ่าน - ระบบตำแหน่งทางวิชาการ");
		helper.setText(content, true);
		mailSender.send(message);
		return true;
	}

	public Boolean sendOtpEmail(String recipientEmail, String otp)
			throws UnsupportedEncodingException, MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);

		helper.setFrom("seven1aaplus@gmail.com", "ระบบตำแหน่งทางวิชาการ");
		helper.setTo(recipientEmail);

		String content = "<div style='font-family: Sarabun, sans-serif; max-width: 500px; margin: 0 auto; padding: 20px;'>"
				+ "<div style='background: linear-gradient(135deg, #1a237e, #0d47a1); color: #fff; padding: 20px; border-radius: 12px 12px 0 0; text-align: center;'>"
				+ "<h2 style='margin:0;'>ระบบตำแหน่งทางวิชาการ</h2>"
				+ "<p style='margin:5px 0 0; opacity:0.8;'>วิทยาลัยการคอมพิวเตอร์ มข.</p>"
				+ "</div>"
				+ "<div style='background: #fff; padding: 30px; border: 1px solid #e0e0e0; border-radius: 0 0 12px 12px;'>"
				+ "<p>สวัสดี,</p>"
				+ "<p>รหัส OTP สำหรับการเข้าสู่ระบบครั้งแรกของคุณคือ:</p>"
				+ "<div style='background: #f5f5f5; padding: 15px; text-align: center; border-radius: 8px; margin: 20px 0;'>"
				+ "<span style='font-size: 32px; font-weight: 700; letter-spacing: 5px; color: #1a237e;'>" + otp
				+ "</span>"
				+ "</div>"
				+ "<p style='color: #666; font-size: 14px;'>รหัสนี้จะหมดอายุใน <strong>5 นาที</strong></p>"
				+ "<p style='color: #999; font-size: 12px;'>หากคุณไม่ได้ร้องขอ กรุณาละเว้นอีเมลนี้</p>"
				+ "</div></div>";

		helper.setSubject("รหัส OTP - ระบบตำแหน่งทางวิชาการ");
		helper.setText(content, true);
		mailSender.send(message);
		return true;
	}

	public Boolean sendNotificationEmail(String recipientEmail, String subject, String content)
			throws UnsupportedEncodingException, MessagingException {
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);

		helper.setFrom("seven1aaplus@gmail.com", "ระบบตำแหน่งทางวิชาการ");
		helper.setTo(recipientEmail);
		helper.setSubject(subject);
		helper.setText(content, true);

		mailSender.send(message);
		return true;
	}

	public static String generateUrl(HttpServletRequest request) {
		String siteUrl = request.getRequestURL().toString();
		return siteUrl.replace(request.getServletPath(), "");
	}

	public UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		return userService.getUserByEmail(email);
	}
}
