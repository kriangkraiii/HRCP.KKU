package com.ecom.controller;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private UserService userService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AdminLogService adminLogService;

	@Autowired
	private HttpServletRequest httpRequest;

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
	public String updateProfile(@ModelAttribute UserDtls user, @RequestParam MultipartFile img, HttpSession session) {
		if (img != null && !img.isEmpty()) {
			user.setProfileImage(img.getOriginalFilename());
		}
		UserDtls updateUserProfile = userService.updateUserProfile(user, img);
		if (ObjectUtils.isEmpty(updateUserProfile)) {
			session.setAttribute("errorMsg", "อัพเดทโปรไฟล์ไม่สำเร็จ");
		} else {
			session.setAttribute("succMsg", "อัพเดทโปรไฟล์สำเร็จ");
			// Log activity
			try {
				adminLogService.log(user.getEmail(), user.getName(),
						"USER_UPDATE_PROFILE",
						"อัพเดทโปรไฟล์ผู้ใช้ (" + user.getEmail() + ")",
						getClientIpAddress());
			} catch (Exception ignored) {}
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
				} catch (Exception ignored) {}
			}
		} else {
			session.setAttribute("errorMsg", "รหัสผ่านปัจจุบันไม่ถูกต้อง");
		}

		return "redirect:/user/profile";
	}

	private String getClientIpAddress() {
		String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0];
		}
		return httpRequest.getRemoteAddr();
	}
}
