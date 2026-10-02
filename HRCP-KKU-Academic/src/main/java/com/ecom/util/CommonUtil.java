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

	@org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username:noreply@kku.ac.th}}")
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

		helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
		helper.setTo(reciepentEmail);
		helper.setSubject("การตั้งรหัสผ่านใหม่ - ระบบพัฒนาบุคลากร วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");

		UserDtls owner = userRepository.findByEmail(reciepentEmail);
		String content = EmailTemplateHelper.buildPasswordResetEmail(
				owner != null ? EmailTemplateHelper.formalName(owner) : null, url);
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

		helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
		helper.setTo(recipientEmail);
		helper.setSubject("รหัสยืนยันตัวตนสำหรับการเข้าสู่ระบบครั้งแรก - วิทยาลัยการคอมพิวเตอร์ มหาวิทยาลัยขอนแก่น");

		// ขึ้นต้นจดหมายด้วยชื่อเจ้าของบัญชี ไม่ใช่อีเมล
		UserDtls owner = userRepository.findByEmail(recipientEmail);
		String content = EmailTemplateHelper.buildOtpEmail(
				owner != null ? EmailTemplateHelper.formalName(owner) : "ผู้ใช้งานระบบ", otp, "การเข้าสู่ระบบครั้งแรก", 5);
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

		helper.setFrom(EmailTemplateHelper.resolveSenderEmail(senderEmail), EmailTemplateHelper.SENDER_NAME);
		helper.setTo(recipientEmail);
		helper.setSubject(subject);

		String fullHtml = EmailTemplateHelper.wrapLayout(subject, "แจ้งเตือนจากระบบ", content);
		helper.setText(fullHtml, true);
		EmailTemplateHelper.attachLogos(helper);

		mailSender.send(message);
		return true;
	}

	/**
	 * ต้นทางของเว็บ (scheme://host[:port][/context]) สำหรับลิงก์ในอีเมล
	 *
	 * <p>เดิมตัด servlet path ออกจาก URL ของคำขอด้วย {@code String.replace} ซึ่งพังเมื่อ servlet path
	 * ว่าง (ได้ลิงก์ {@code /forgot-password/reset-password}) หรือเมื่อข้อความเดียวกันโผล่ในชื่อโฮสต์
	 */
	public static String generateUrl(HttpServletRequest request) {
		return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromContextPath(request)
				.replaceQuery(null).build().toUriString();
	}

	public UserDtls getLoggedInUserDetails(Principal p) {
		String email = p.getName();
		return userRepository.findByEmail(email);
	}
}
