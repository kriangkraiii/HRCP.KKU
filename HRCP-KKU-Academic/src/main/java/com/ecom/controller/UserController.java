package com.ecom.controller;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.config.ClientIpUtils;
import com.ecom.model.UserDtls;
import com.ecom.service.AdminLogService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.PasswordValidator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/user")
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	private final UserService userService;

	private final CommonUtil commonUtil;

	private final PasswordEncoder passwordEncoder;

	private final AdminLogService adminLogService;

	private final HttpServletRequest httpRequest;

	public UserController(
			UserService userService,
			CommonUtil commonUtil,
			PasswordEncoder passwordEncoder,
			AdminLogService adminLogService,
			HttpServletRequest httpRequest) {
		this.userService = userService;
		this.commonUtil = commonUtil;
		this.passwordEncoder = passwordEncoder;
		this.adminLogService = adminLogService;
		this.httpRequest = httpRequest;
	}

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
		}
	}

	private UserDtls getLoggedInUserDetails(Principal p) {
		if (p == null)
			return null;
		return userService.getUserByEmail(p.getName());
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/user/academic/dashboard";
	}

	@GetMapping("/profile")
	public String profile() {
		return "user/profile";
	}

	@PostMapping("/update-profile")
	public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, Principal p,
			HttpSession session) {
		if (p == null) {
			return "redirect:/signin";
		}

		// The target account comes from the session — an id in the form is ignored.
		UserDtls updated = userService.updateUserProfile(user, img, p.getName());
		if (ObjectUtils.isEmpty(updated)) {
			session.setAttribute("errorMsg", "อัพเดทโปรไฟล์ไม่สำเร็จ");
		} else {
			session.setAttribute("succMsg", "อัพเดทโปรไฟล์สำเร็จ");
			// Log activity
			try {
				adminLogService.log(updated.getEmail(), updated.getName(),
						"USER_UPDATE_PROFILE",
						"อัพเดทโปรไฟล์ผู้ใช้ (" + updated.getEmail() + ")",
						getClientIpAddress());
			} catch (Exception e) {
				logger.warn("Failed to write audit log for profile update of {}: {}",
						updated.getEmail(), e.getMessage());
			}
		}
		return "redirect:/user/profile";
	}

	@PostMapping("/change-password")
	public String changePassword(@RequestParam String newPassword, @RequestParam String currentPassword, Principal p,
			HttpSession session) {
		UserDtls loggedInUserDetails = getLoggedInUserDetails(p);

		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String passwordError = PasswordValidator.validate(newPassword);
			if (passwordError != null) {
				session.setAttribute("errorMsg", passwordError);
				return "redirect:/user/profile";
			}
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "เปลี่ยนรหัสผ่านไม่สำเร็จ");
			} else {
				session.setAttribute("succMsg", "เปลี่ยนรหัสผ่านสำเร็จ");
				// Log activity
				try {
					adminLogService.log(p.getName(), loggedInUserDetails.getName(),
							"USER_CHANGE_PASSWORD",
							"ผู้ใช้เปลี่ยนรหัสผ่าน (" + p.getName() + ")",
							getClientIpAddress());
				} catch (Exception e) {
					logger.warn("Failed to write audit log for password change of {}: {}",
							p.getName(), e.toString());
				}
			}
		} else {
			session.setAttribute("errorMsg", "รหัสผ่านปัจจุบันไม่ถูกต้อง");
		}

		return "redirect:/user/profile";
	}

	private String getClientIpAddress() {
		return ClientIpUtils.resolveClientIp(httpRequest);
	}
}
