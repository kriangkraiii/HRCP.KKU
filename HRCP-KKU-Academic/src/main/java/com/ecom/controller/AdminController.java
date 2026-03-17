package com.ecom.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.service.AdminLogService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.FileUtils;
import com.ecom.util.PasswordValidator;

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

	@Autowired
	private AdminLogService adminLogService;

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

	/**
	 * Validates uploaded image file for size and type
	 * 
	 * @param file The uploaded file
	 * @return Error message if validation fails, null if valid
	 */
	private String validateImageFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return null; // No file uploaded is acceptable
		}

		// Check file size (max 5MB)
		long maxSize = 5 * 1024 * 1024; // 5MB in bytes
		if (file.getSize() > maxSize) {
			return "ไฟล์มีขนาดใหญ่เกินไป (สูงสุด 5MB)";
		}

		// Check file type by extension and MIME type
		String originalFilename = file.getOriginalFilename();
		String contentType = file.getContentType();

		if (originalFilename != null) {
			String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
			if (!extension.matches("jpg|jpeg|png|gif")) {
				return "รองรับเฉพาะไฟล์รูปภาพ (JPG, PNG, GIF)";
			}
		}

		// Validate MIME type
		if (contentType != null && !contentType.startsWith("image/")) {
			return "รองรับเฉพาะไฟล์รูปภาพ (JPG, PNG, GIF)";
		}

		return null; // Valid file
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

	@PostMapping("/updateEmailNotification")
	public String updateEmailNotification(@RequestParam Boolean enabled, @RequestParam Integer id,
			@RequestParam Integer type, HttpSession session) {
		Boolean f = userService.updateEmailNotification(id, enabled);
		if (f) {
			session.setAttribute("succMsg", "อัพเดทการแจ้งเตือนสำเร็จ");
		} else {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาด");
		}
		return "redirect:/admin/users?type=" + type;
	}

	@PostMapping("/updateSts")
	public String updateUserAccountStatus(@RequestParam Boolean status, @RequestParam Integer id,
			@RequestParam Integer type, HttpSession session) {
		Boolean f = userService.updateAccountStatus(id, status);
		if (f) {
			session.setAttribute("succMsg", "อัพเดทสถานะบัญชีสำเร็จ");
			Principal p = request.getUserPrincipal();
			if (p != null) {
				UserDtls admin = userService.getUserByEmail(p.getName());
				adminLogService.log(p.getName(), admin != null ? admin.getName() : p.getName(),
						"UPDATE_ACCOUNT_STATUS", "เปลี่ยนสถานะบัญชี ID:" + id + " เป็น " + (status ? "เปิด" : "ปิด"),
						getClientIpAddress(request));
			}
		} else {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาด");
		}
		return "redirect:/admin/users?type=" + type;
	}

	// ====== AJAX Profile Image Update ======

	@PostMapping("/update-profile-image")
	@ResponseBody
	public ResponseEntity<Map<String, String>> updateProfileImage(
			@RequestParam Integer id,
			@RequestParam("img") MultipartFile file,
			Principal principal) {

		Map<String, String> response = new HashMap<>();

		// SEC-03: Verify admin is authenticated
		if (principal == null) {
			response.put("success", "false");
			response.put("error", "ไม่ได้เข้าสู่ระบบ");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
		}

		// Validate file upload
		String fileValidationError = validateImageFile(file);
		if (fileValidationError != null) {
			response.put("success", "false");
			response.put("error", fileValidationError);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}

		try {
			// Call service to update profile image only
			Map<String, String> result = userService.updateProfileImageOnly(id, file);

			if ("true".equals(result.get("success"))) {
				response.put("success", "true");
				response.put("imageUrl", result.get("imageUrl"));
				response.put("imageName", result.get("imageName"));
				return ResponseEntity.ok(response);
			} else {
				response.put("success", "false");
				response.put("error", result.get("error"));
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
			}
		} catch (Exception e) {
			response.put("success", "false");
			response.put("error", "เกิดข้อผิดพลาดในการอัพโหลดรูปภาพ");
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/delete-user")
	public String deleteUser(@RequestParam Integer id, @RequestParam Integer type, HttpSession session, Principal p) {
		try {
			// Get current user's email
			String currentUserEmail = p.getName();

			// Get user details before deletion for logging
			UserDtls userToDelete = userService.getUserById(id);
			if (userToDelete == null) {
				session.setAttribute("errorMsg", "ไม่พบบัญชีที่ระบุ");
				return "redirect:/admin/users?type=" + type;
			}

			// Check if deletion is allowed
			Boolean canDelete = userService.canDeleteUser(id, currentUserEmail);
			if (!canDelete) {
				session.setAttribute("errorMsg", "ไม่สามารถลบบัญชีนี้ได้");
				return "redirect:/admin/users?type=" + type;
			}

			// Store email for logging before deletion
			String deletedEmail = userToDelete.getEmail();

			// Delete the user
			Boolean deleted = userService.deleteUserById(id);

			if (deleted) {
				// Log the action
				UserDtls admin = userService.getUserByEmail(currentUserEmail);
				adminLogService.log(
						currentUserEmail,
						admin != null ? admin.getName() : currentUserEmail,
						"DELETE_USER_ACCOUNT",
						"ลบบัญชีผู้ใช้ ID:" + id + " (" + deletedEmail + ")",
						getClientIpAddress(request));
				session.setAttribute("succMsg", "ลบบัญชีผู้ใช้สำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
			}
		} catch (Exception e) {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
			e.printStackTrace();
		}

		return "redirect:/admin/users?type=" + type;
	}

	@PostMapping("/delete-admin")
	public String deleteAdmin(@RequestParam Integer id, @RequestParam Integer type, HttpSession session, Principal p) {
		try {
			// Get current user's email
			String currentUserEmail = p.getName();

			// Get admin details before deletion for logging
			UserDtls adminToDelete = userService.getUserById(id);
			if (adminToDelete == null) {
				session.setAttribute("errorMsg", "ไม่พบบัญชีที่ระบุ");
				return "redirect:/admin/users?type=" + type;
			}

			// Check if deletion is allowed (prevent self-deletion)
			Boolean canDelete = userService.canDeleteUser(id, currentUserEmail);
			if (!canDelete) {
				session.setAttribute("errorMsg", "ไม่สามารถลบบัญชีของตัวเองได้");
				return "redirect:/admin/users?type=" + type;
			}

			// Store email for logging before deletion
			String deletedEmail = adminToDelete.getEmail();

			// Delete the admin
			Boolean deleted = userService.deleteUserById(id);

			if (deleted) {
				// Log the action
				UserDtls admin = userService.getUserByEmail(currentUserEmail);
				adminLogService.log(
						currentUserEmail,
						admin != null ? admin.getName() : currentUserEmail,
						"DELETE_ADMIN_ACCOUNT",
						"ลบบัญชีแอดมิน ID:" + id + " (" + deletedEmail + ")",
						getClientIpAddress(request));
				session.setAttribute("succMsg", "ลบบัญชีแอดมินสำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
			}
		} catch (Exception e) {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
			e.printStackTrace();
		}

		return "redirect:/admin/users?type=" + type;
	}

	@PostMapping("/update-admin")
	public String updateAdmin(@ModelAttribute UserDtls user, @RequestParam("img") MultipartFile file,
			@RequestParam Integer type, HttpSession session, Principal p) {

		// Validate email format
		if (user.getEmail() == null || !user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
			session.setAttribute("errorMsg", "รูปแบบอีเมลไม่ถูกต้อง");
			return "redirect:/admin/edit-admin?id=" + user.getId();
		}

		// Validate required fields
		if (user.getName() == null || user.getName().trim().isEmpty()) {
			session.setAttribute("errorMsg", "กรุณากรอกชื่อ");
			return "redirect:/admin/edit-admin?id=" + user.getId();
		}

		// Validate file upload if provided
		String fileValidationError = validateImageFile(file);
		if (fileValidationError != null) {
			session.setAttribute("errorMsg", fileValidationError);
			return "redirect:/admin/edit-admin?id=" + user.getId();
		}

		// Check email uniqueness (exclude current admin)
		UserDtls existingUser = userService.getUserByEmail(user.getEmail());
		if (existingUser != null && !existingUser.getId().equals(user.getId())) {
			session.setAttribute("errorMsg", "อีเมลนี้มีในระบบแล้ว");
			return "redirect:/admin/edit-admin?id=" + user.getId();
		}

		try {
			// Update admin details
			UserDtls updatedAdmin = userService.updateUserDetails(user, file);

			if (updatedAdmin != null) {
				// Log the action
				if (p != null) {
					UserDtls admin = userService.getUserByEmail(p.getName());
					adminLogService.log(
							p.getName(),
							admin != null ? admin.getName() : p.getName(),
							"EDIT_ADMIN_ACCOUNT",
							"แก้ไขบัญชีแอดมิน ID:" + user.getId() + " (" + user.getEmail() + ")",
							getClientIpAddress(request));
				}
				session.setAttribute("succMsg", "อัพเดทข้อมูลแอดมินสำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			}
		} catch (Exception e) {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			e.printStackTrace();
		}

		return "redirect:/admin/users?type=" + type;
	}

	@GetMapping("/edit-user")
	public String loadEditUser(@RequestParam Integer id, Model m) {
		UserDtls editUser = userService.getUserById(id);
		m.addAttribute("editUser", editUser);
		return "admin/edit_user";
	}

	@GetMapping("/edit-admin")
	public String loadEditAdmin(@RequestParam Integer id, Model m) {
		UserDtls admin = userService.getUserById(id);
		m.addAttribute("admin", admin);
		return "admin/edit_admin";
	}

	@PostMapping("/update-user")
	public String updateUser(@ModelAttribute UserDtls user, @RequestParam("img") MultipartFile file,
			@RequestParam Integer type, HttpSession session, Principal p) {

		// Validate email format
		if (user.getEmail() == null || !user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
			session.setAttribute("errorMsg", "รูปแบบอีเมลไม่ถูกต้อง");
			return "redirect:/admin/edit-user?id=" + user.getId();
		}

		// Validate required fields
		if (user.getName() == null || user.getName().trim().isEmpty()) {
			session.setAttribute("errorMsg", "กรุณากรอกชื่อ");
			return "redirect:/admin/edit-user?id=" + user.getId();
		}

		// Validate file upload if provided
		String fileValidationError = validateImageFile(file);
		if (fileValidationError != null) {
			session.setAttribute("errorMsg", fileValidationError);
			return "redirect:/admin/edit-user?id=" + user.getId();
		}

		// Check email uniqueness (exclude current user)
		UserDtls existingUser = userService.getUserByEmail(user.getEmail());
		if (existingUser != null && !existingUser.getId().equals(user.getId())) {
			session.setAttribute("errorMsg", "อีเมลนี้มีในระบบแล้ว");
			return "redirect:/admin/edit-user?id=" + user.getId();
		}

		try {
			// Update user details
			UserDtls updatedUser = userService.updateUserDetails(user, file);

			if (updatedUser != null) {
				// Log the action
				if (p != null) {
					UserDtls admin = userService.getUserByEmail(p.getName());
					adminLogService.log(
							p.getName(),
							admin != null ? admin.getName() : p.getName(),
							"EDIT_USER_ACCOUNT",
							"แก้ไขบัญชีผู้ใช้ ID:" + user.getId() + " (" + user.getEmail() + ")",
							getClientIpAddress(request));
				}
				session.setAttribute("succMsg", "อัพเดทข้อมูลผู้ใช้สำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			}
		} catch (Exception e) {
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			e.printStackTrace();
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
				Path path = Path.of(uploadDir + FileUtils.sanitizeFilename(file.getOriginalFilename()));
				Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
			}
			session.setAttribute("succMsg", "เพิ่มบัญชีสำเร็จ");
			Principal p = request.getUserPrincipal();
			if (p != null) {
				UserDtls admin = userService.getUserByEmail(p.getName());
				adminLogService.log(p.getName(), admin != null ? admin.getName() : p.getName(),
						"CREATE_ACCOUNT", "สร้างบัญชี: " + user.getEmail() + " (" + user.getRole() + ")",
						getClientIpAddress(request));
			}
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
			String passwordError = PasswordValidator.validate(newPassword);
			if (passwordError != null) {
				session.setAttribute("errorMsg", passwordError);
				return "redirect:/admin/profile";
			}
			String encodePassword = passwordEncoder.encode(newPassword);
			loggedInUserDetails.setPassword(encodePassword);
			UserDtls updateUser = userService.updateUser(loggedInUserDetails);
			if (ObjectUtils.isEmpty(updateUser)) {
				session.setAttribute("errorMsg", "เปลี่ยนรหัสผ่านไม่สำเร็จ");
			} else {
				session.setAttribute("succMsg", "เปลี่ยนรหัสผ่านสำเร็จ");
				adminLogService.log(p.getName(), loggedInUserDetails.getName(),
						"CHANGE_PASSWORD", "เปลี่ยนรหัสผ่านสำเร็จ", getClientIpAddress(request));
			}
		} else {
			session.setAttribute("errorMsg", "รหัสผ่านปัจจุบันไม่ถูกต้อง");
		}

		return "redirect:/admin/profile";
	}

	// ====== Activity Logs ======

	@GetMapping("/activity-logs")
	public String activityLogs(Model m,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String dateFrom,
			@RequestParam(required = false) String dateTo) {
		Page<AdminLog> logs = adminLogService.getAllLogs(page, 20, search, action, dateFrom, dateTo);
		m.addAttribute("logs", logs);
		m.addAttribute("currentPage", page);
		m.addAttribute("search", search);
		m.addAttribute("actionFilter", action);
		m.addAttribute("dateFrom", dateFrom);
		m.addAttribute("dateTo", dateTo);

		// Summary counts
		m.addAttribute("createCount", adminLogService.countByAction("CREATE_ACCOUNT"));
		m.addAttribute("updateCount", adminLogService.countByAction("UPDATE_ACCOUNT_STATUS"));
		m.addAttribute("docCount", adminLogService.countByAction("GENERATE_DOCUMENT"));
		return "admin/activity_logs";
	}

	@GetMapping("/activity-logs/export")
	public void exportActivityLogs(
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String dateFrom,
			@RequestParam(required = false) String dateTo,
			jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

		response.setContentType("text/csv; charset=UTF-8");
		response.setHeader("Content-Disposition", "attachment; filename=activity_logs.csv");

		java.io.OutputStream out = response.getOutputStream();
		out.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

		java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(out, "UTF-8"));
		writer.println("ID,วันเวลา,ผู้ดำเนินการ,อีเมล,การกระทำ,รายละเอียด,IP Address,Resource,User Agent");

		java.util.List<AdminLog> logs = adminLogService.getAllLogsForExport(search, action, dateFrom, dateTo);
		java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

		for (AdminLog log : logs) {
			writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
					log.getId(),
					log.getTimestamp() != null ? log.getTimestamp().format(fmt) : "",
					escapeCsv(log.getAdminName()),
					escapeCsv(log.getAdminEmail()),
					escapeCsv(log.getAction()),
					escapeCsv(log.getDetails()),
					escapeCsv(log.getIpAddress()),
					escapeCsv(log.getResource()),
					escapeCsv(log.getUserAgent()));
		}
		writer.flush();
	}

	private String escapeCsv(String value) {
		if (value == null)
			return "";
		return value.replace("\"", "\"\"");
	}
}
