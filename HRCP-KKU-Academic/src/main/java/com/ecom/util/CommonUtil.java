package com.ecom.util;

import java.io.UnsupportedEncodingException;
import java.security.Principal;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CommonUtil {

	private final JavaMailSender mailSender;
	private final UserRepository userRepository;

	@org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@kku.ac.th}")
	private String senderEmail;

	public CommonUtil(JavaMailSender mailSender, UserRepository userRepository) {
		this.mailSender = mailSender;
		this.userRepository = userRepository;
	}

	public Boolean sendMail(String url, String reciepentEmail) throws UnsupportedEncodingException, MessagingException {
		if (EmailTemplateHelper.isTestEmail(reciepentEmail)) {
			return true;
		}
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

		helper.setFrom(senderEmail, EmailTemplateHelper.SENDER_NAME);
		helper.setTo(reciepentEmail);
		helper.setSubject("รีเซ็ตรหัสผ่าน - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)");

		String content = EmailTemplateHelper.buildPasswordResetEmail(url);
		helper.setText(content, true);
		EmailTemplateHelper.attachLogos(helper);
		mailSender.send(message);
		return true;
	}

	public Boolean sendOtpEmail(String recipientEmail, String otp)
			throws UnsupportedEncodingException, MessagingException {
		if (EmailTemplateHelper.isTestEmail(recipientEmail)) {
			return true;
		}
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

		helper.setFrom(senderEmail, EmailTemplateHelper.SENDER_NAME);
		helper.setTo(recipientEmail);
		helper.setSubject("รหัส OTP สำหรับเข้าสู่ระบบครั้งแรก - ระบบตำแหน่งทางวิชาการ (HRCP.KKU)");

		String content = EmailTemplateHelper.buildOtpEmail(recipientEmail, otp, "เข้าสู่ระบบครั้งแรก", 5);
		helper.setText(content, true);
		EmailTemplateHelper.attachLogos(helper);
		mailSender.send(message);
		return true;
	}

	public Boolean sendNotificationEmail(String recipientEmail, String subject, String content)
			throws UnsupportedEncodingException, MessagingException {
		if (EmailTemplateHelper.isTestEmail(recipientEmail)) {
			return true;
		}
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

		helper.setFrom(senderEmail, EmailTemplateHelper.SENDER_NAME);
		helper.setTo(recipientEmail);
		helper.setSubject(subject);

		String fullHtml = EmailTemplateHelper.wrapLayout(subject, "แจ้งเตือนจากระบบ", content);
		helper.setText(fullHtml, true);
		EmailTemplateHelper.attachLogos(helper);

		mailSender.send(message);
		return true;
	}

	public static String generateUrl(HttpServletRequest request) {
		String siteUrl = request.getRequestURL().toString();
		return siteUrl.replace(request.getServletPath(), "");
	}

	public UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		return userRepository.findByEmail(email);
	}
}
