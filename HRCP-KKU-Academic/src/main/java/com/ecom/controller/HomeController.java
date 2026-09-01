package com.ecom.controller;

import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.ecom.model.UserDtls;
import com.ecom.service.AdminLogService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.PasswordValidator;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class HomeController {

	private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

	/** ข้อความกลางสำหรับกรณีส่งอีเมลไม่สำเร็จ (ห้ามเผยรายละเอียดของ SMTP ให้ผู้ใช้เห็น) */
	private static final String MAIL_ERROR_MSG = "ไม่สามารถส่งอีเมลได้ในขณะนี้ กรุณาติดต่อผู้ดูแลระบบ";

	@Autowired
	private UserService userService;

	private final CommonUtil commonUtil;

	private final BCryptPasswordEncoder passwordEncoder;

	private final AdminLogService adminLogService;

	public HomeController(
			UserService userService,
			CommonUtil commonUtil,
			BCryptPasswordEncoder passwordEncoder,
			AdminLogService adminLogService) {
		this.userService = userService;
		this.commonUtil = commonUtil;
		this.passwordEncoder = passwordEncoder;
		this.adminLogService = adminLogService;
	}

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
		}
	}

	@GetMapping("/")
	public String index() {
		return "redirect:/signin";
	}

	@GetMapping("/signin")
	public String login(@org.springframework.web.bind.annotation.RequestParam(name = "code", required = false) String code) {
		if (code != null && !code.isBlank()) {
			return "forward:/auth/callback/login";
		}
		return "guest/login";
	}

	/**
	 * Target of SecurityConfig's accessDeniedPage. Spring Security already sets
	 * 403 before forwarding here; the annotation repeats it so that reaching the
	 * page directly reports 403 as well instead of a misleading 200.
	 */
	@GetMapping("/403")
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String accessDenied() {
		return "error/403";
	}

	// ====== First-Time Login Flow ======

	@GetMapping("/first-login")
	public String showFirstLogin() {
		return "guest/first_login";
	}

	@PostMapping("/first-login")
	public String processFirstLogin(@RequestParam String email, HttpSession session, Model m) {
		try {
			userService.generateAndSendOtp(email);
			session.setAttribute("otpEmail", email);
			session.setAttribute("succMsg", "ส่งรหัส OTP ไปที่อีเมลของคุณแล้ว");
			return "redirect:/first-login/verify-otp";
		} catch (MailException | MessagingException | UnsupportedEncodingException e) {
			logger.error("ส่ง OTP ไปยัง {} ไม่สำเร็จ", email, e);
			session.setAttribute("errorMsg", MAIL_ERROR_MSG);
			return "redirect:/first-login";
		} catch (Exception e) {
			// business error (ไม่พบอีเมล / ตั้งรหัสผ่านแล้ว) — ข้อความเป็นภาษาไทยอยู่แล้ว
			session.setAttribute("errorMsg", e.getMessage());
			return "redirect:/first-login";
		}
	}

	@GetMapping("/first-login/verify-otp")
	public String showVerifyOtp(HttpSession session, Model m) {
		String email = (String) session.getAttribute("otpEmail");
		if (email == null) {
			return "redirect:/first-login";
		}
		m.addAttribute("email", email);
		return "guest/verify_otp";
	}

	@PostMapping("/first-login/verify-otp")
	public String processVerifyOtp(@RequestParam String otp, HttpSession session) {
		String email = (String) session.getAttribute("otpEmail");
		if (email == null) {
			session.setAttribute("errorMsg", "เซสชันหมดอายุ กรุณาเริ่มใหม่");
			return "redirect:/first-login";
		}

		boolean valid = userService.verifyOtp(email, otp);
		if (valid) {
			session.setAttribute("otpVerified", true);
			return "redirect:/first-login/set-password";
		} else {
			session.setAttribute("errorMsg", "รหัส OTP ไม่ถูกต้องหรือหมดอายุ");
			return "redirect:/first-login/verify-otp";
		}
	}

	@GetMapping("/first-login/set-password")
	public String showSetPassword(HttpSession session) {
		String email = (String) session.getAttribute("otpEmail");
		Boolean otpVerified = (Boolean) session.getAttribute("otpVerified");
		if (email == null || otpVerified == null || !otpVerified) {
			return "redirect:/first-login";
		}
		return "guest/set_password";
	}

	@PostMapping("/first-login/set-password")
	public String processSetPassword(@RequestParam String password,
			@RequestParam String confirmPassword,
			HttpSession session) {
		String email = (String) session.getAttribute("otpEmail");
		Boolean otpVerified = (Boolean) session.getAttribute("otpVerified");

		if (email == null || otpVerified == null || !otpVerified) {
			session.setAttribute("errorMsg", "เซสชันหมดอายุ กรุณาเริ่มใหม่");
			return "redirect:/first-login";
		}

		if (!password.equals(confirmPassword)) {
			session.setAttribute("errorMsg", "รหัสผ่านไม่ตรงกัน");
			return "redirect:/first-login/set-password";
		}

		String passwordError = PasswordValidator.validate(password);
		if (passwordError != null) {
			session.setAttribute("errorMsg", passwordError);
			return "redirect:/first-login/set-password";
		}

		userService.activateAccount(email, password);

		// Log activity
		try {
			adminLogService.logWithDetails(email, email,
					"FIRST_LOGIN_SET_PASSWORD",
					"ตั้งรหัสผ่านครั้งแรกสำเร็จ (" + email + ")",
					null, "/first-login/set-password", null);
		} catch (Exception e) {
            auditLogFailed(e);
        }

		// Clear session OTP data
		session.removeAttribute("otpEmail");
		session.removeAttribute("otpVerified");

		session.setAttribute("succMsg", "ตั้งรหัสผ่านสำเร็จ กรุณาเข้าสู่ระบบ");
		return "redirect:/signin";
	}

	// ====== Forgot Password ======

	@GetMapping("/forgot-password")
	public String showForgotPassword() {
		return "guest/forgot_password";
	}

	@PostMapping("/forgot-password")
	public String processForgotPassword(@RequestParam String email, HttpSession session, HttpServletRequest request) {
		UserDtls userByEmail = userService.getUserByEmail(email);
		if (ObjectUtils.isEmpty(userByEmail)) {
			session.setAttribute("errorMsg", "ไม่พบอีเมลนี้ในระบบ");
		} else {
			String resetToken = UUID.randomUUID().toString();
			userService.updateUserResetToken(email, resetToken);
			String url = CommonUtil.generateUrl(request) + "/reset-password?token=" + resetToken;
			try {
				commonUtil.sendMail(url, email);
				session.setAttribute("succMsg", "กรุณาตรวจสอบอีเมลของคุณ ลิงก์รีเซ็ตรหัสผ่านถูกส่งแล้ว");
			} catch (MailException | MessagingException | UnsupportedEncodingException e) {
				logger.error("ส่งลิงก์รีเซ็ตรหัสผ่านไปยัง {} ไม่สำเร็จ", email, e);
				session.setAttribute("errorMsg", MAIL_ERROR_MSG);
			}
		}
		return "redirect:/forgot-password";
	}

	@GetMapping("/reset-password")
	public String showResetPassword(@RequestParam String token, HttpSession session, Model m) {
		UserDtls userByToken = userService.getUserByToken(token);
		if (userByToken == null) {
			m.addAttribute("msg", "ลิงก์ไม่ถูกต้องหรือหมดอายุ");
			return "guest/message";
		}
		m.addAttribute("token", token);
		return "guest/reset_password";
	}

	@PostMapping("/reset-password")
	public String resetPassword(@RequestParam String token,
			@RequestParam String password,
			@RequestParam String confirmPassword,
			HttpSession session, Model m) {
		UserDtls userByToken = userService.getUserByToken(token);
		if (userByToken == null) {
			m.addAttribute("msg", "ลิงก์ไม่ถูกต้องหรือหมดอายุ");
			return "guest/message";
		}
		if (!password.equals(confirmPassword)) {
			session.setAttribute("errorMsg", "รหัสผ่านไม่ตรงกัน");
			m.addAttribute("token", token);
			return "guest/reset_password";
		}
		String passwordError = PasswordValidator.validate(password);
		if (passwordError != null) {
			session.setAttribute("errorMsg", passwordError);
			m.addAttribute("token", token);
			return "guest/reset_password";
		}
		userByToken.setPassword(passwordEncoder.encode(password));
		userByToken.setResetToken(null);
		userService.updateUser(userByToken);

		// Log activity
		try {
			adminLogService.logWithDetails(userByToken.getEmail(), userByToken.getName(),
					"RESET_PASSWORD",
					"รีเซ็ตรหัสผ่านสำเร็จ (" + userByToken.getEmail() + ")",
					null, "/reset-password", null);
		} catch (Exception e) {
            auditLogFailed(e);
        }

		session.setAttribute("succMsg", "รีเซ็ตรหัสผ่านสำเร็จ กรุณาเข้าสู่ระบบใหม่");
		return "redirect:/signin";
	}

	/** Audit logging must never break the user's action, but it must leave a trace. */
	private void auditLogFailed(Exception e) {
		logger.warn("Failed to write audit log: {}", e.toString());
	}
}
