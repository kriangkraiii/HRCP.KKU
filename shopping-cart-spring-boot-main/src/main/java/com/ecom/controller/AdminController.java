package com.ecom.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private UserService userService;

	@Autowired
	private CommonUtil commonUtil;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
		}
	}

	private String getClientIpAddress(HttpServletRequest request) {
		String xForwardedFor = request.getHeader("X-Forwarded-For");
		if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
			return xForwardedFor.split(",")[0];
		}
		return request.getRemoteAddr();
	}

	// ====== Dashboard ======

	@GetMapping("/")
	public String index(Model m) {
		try {
			List<UserDtls> allUsers = userService.getUsers("ROLE_USER");
			m.addAttribute("totalUsers", allUsers.size());
			m.addAttribute("newUsersToday", userService.getNewUsersToday());
			List<UserDtls> recentUsers = userService.getRecentUsers(5);
			m.addAttribute("recentUsers", recentUsers);
		} catch (Exception e) {
			e.printStackTrace();
			m.addAttribute("totalUsers", 0);
			m.addAttribute("newUsersToday", 0);
		}
		return "admin/index";
	}

	// ====== User Management ======

	@GetMapping("/users")
	public String getAllUsers(Model m, @RequestParam Integer type) {
		List<UserDtls> users = null;
		if (type == 1) {
			users = userService.getUsers("ROLE_USER");
		} else {
			users = userService.getUsers("ROLE_ADMIN");
		}
		m.addAttribute("userType", type);
		m.addAttribute("users", users);
		return "admin/users";
	}

	@GetMapping("/updateSts")
	public String updateUserAccountStatus(@RequestParam Boolean status, @RequestParam Integer id,
			@RequestParam Integer type, HttpSession session) {
		Boolean f = userService.updateAccountStatus(id, status);
		if (f) {
			session.setAttribute("succMsg", "อัพเดทสถานะบัญชีสำเร็จ");
		} else {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาด");
		}
		return "redirect:/admin/users?type=" + type;
	}

	// ====== Add User/Admin ======

	@GetMapping("/add-admin")
	public String loadAdminAdd() {
		return "admin/add_admin";
	}

	@PostMapping("/save-admin")
	public String saveAdmin(@ModelAttribute UserDtls user, @RequestParam("img") MultipartFile file,
			HttpSession session) throws IOException {

		// Check if email already exists
		if (userService.existsEmail(user.getEmail())) {
			session.setAttribute("errorMsg", "อีเมลนี้มีในระบบแล้ว");
			return "redirect:/admin/add-admin";
		}

		String imageName = file.isEmpty() ? "default.png" : file.getOriginalFilename();
		user.setProfileImage(imageName);

		UserDtls saveUser = userService.saveAdmin(user);

		if (!ObjectUtils.isEmpty(saveUser)) {
			if (!file.isEmpty()) {
				String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
				File uploadFolder = new File(uploadDir);
				if (!uploadFolder.exists()) {
					uploadFolder.mkdirs();
				}
				Path path = Paths.get(uploadDir + file.getOriginalFilename());
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
			}
			session.setAttribute("succMsg", "เพิ่มบัญชีสำเร็จ");
		} else {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาด");
		}

		return "redirect:/admin/add-admin";
	}

	// ====== Profile ======

	@GetMapping("/profile")
	public String profile() {
		return "admin/profile";
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
		}
		return "redirect:/admin/profile";
	}

	@PostMapping("/change-password")
	public String changePassword(@RequestParam String newPassword, @RequestParam String currentPassword, Principal p,
			HttpSession session) {
		UserDtls loggedInUserDetails = commonUtil.getLoggedInUserDetails(p);

		boolean matches = passwordEncoder.matches(currentPassword, loggedInUserDetails.getPassword());

		if (matches) {
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "เปลี่ยนรหัสผ่านไม่สำเร็จ");
			} else {
				session.setAttribute("succMsg", "เปลี่ยนรหัสผ่านสำเร็จ");
			}
		} else {
			session.setAttribute("errorMsg", "รหัสผ่านปัจจุบันไม่ถูกต้อง");
		}

		return "redirect:/admin/profile";
	}
}
