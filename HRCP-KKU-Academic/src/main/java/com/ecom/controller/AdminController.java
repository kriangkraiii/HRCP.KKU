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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

import com.ecom.config.ClientIpUtils;
import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.service.AdminLogService;
import com.ecom.service.ImageSyncAuditService;
import com.ecom.service.UserService;
import com.ecom.util.CommonUtil;
import com.ecom.util.FileUtils;
import com.ecom.util.PasswordValidator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

	private final HttpServletRequest request;

	private final UserService userService;

	private final CommonUtil commonUtil;

	private final PasswordEncoder passwordEncoder;

	private final AdminLogService adminLogService;

	private final ImageSyncAuditService imageSyncAuditService;

	private final com.ecom.service.DataRetentionService dataRetentionService;

	public AdminController(
			HttpServletRequest request,
			UserService userService,
			CommonUtil commonUtil,
			PasswordEncoder passwordEncoder,
			AdminLogService adminLogService,
			ImageSyncAuditService imageSyncAuditService,
			com.ecom.service.DataRetentionService dataRetentionService) {
		this.request = request;
		this.userService = userService;
		this.commonUtil = commonUtil;
		this.passwordEncoder = passwordEncoder;
		this.adminLogService = adminLogService;
		this.imageSyncAuditService = imageSyncAuditService;
		this.dataRetentionService = dataRetentionService;
	}

	@ModelAttribute
	public void getUserDetails(Principal p, Model m) {
		if (p != null) {
			String email = p.getName();
			UserDtls userDtls = userService.getUserByEmail(email);
			m.addAttribute("user", userDtls);
		}
	}

	private String getClientIpAddress(HttpServletRequest request) {
		return ClientIpUtils.resolveClientIp(request);
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
			logger.error("Error loading admin dashboard stats: {}", e.getMessage(), e);
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

	/**
	 * Switches a user's 2FA off.
	 *
	 * <p>2FA is the user's own choice everywhere else, and this does not change
	 * that — it exists for the one situation the user cannot get themselves out
	 * of: the OTP goes to a mailbox they can no longer open, so they can never
	 * complete a sign-in and never reach the settings page to turn it back off.
	 *
	 * <p>It is a way past someone's second factor, so it is written to the audit
	 * log naming both the admin and the account.
	 */
	@PostMapping("/reset-2fa")
	public String resetTwoFactor(@RequestParam Integer id, @RequestParam Integer type, HttpSession session) {
		UserDtls target = userService.getUserById(id);
		Boolean done = userService.disableTwoFactor(id);

		if (Boolean.TRUE.equals(done)) {
			session.setAttribute("succMsg", "ปิด 2FA ให้บัญชีนี้แล้ว ผู้ใช้เข้าระบบได้โดยไม่ต้องใช้ OTP");
			Principal p = request.getUserPrincipal();
			if (p != null) {
				UserDtls admin = userService.getUserByEmail(p.getName());
				adminLogService.log(p.getName(), admin != null ? admin.getName() : p.getName(),
						"RESET_2FA",
						"ปิด 2FA ของบัญชี " + (target != null ? target.getEmail() : "ID:" + id),
						getClientIpAddress(request));
			}
		} else {
			session.setAttribute("errorMsg", "ไม่พบบัญชีนี้");
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
			logger.error("Error updating profile image for user ID {}: {}", id, e.getMessage(), e);
			response.put("success", "false");
			response.put("error", "เกิดข้อผิดพลาดในการอัพโหลดรูปภาพ");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	@PostMapping("/system/audit-images")
	@ResponseBody
	public ResponseEntity<?> auditAndCleanImages(
			@RequestParam(defaultValue = "false") boolean purgeOrphans,
			@RequestParam(defaultValue = "false") boolean healMissing,
			Principal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "ไม่ได้เข้าสู่ระบบ"));
		}
		var report = imageSyncAuditService.auditAndSync(purgeOrphans, healMissing);
		return ResponseEntity.ok(report);
	}

	@GetMapping("/system/audit-images")
	@ResponseBody
	public ResponseEntity<?> getAuditReport(Principal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "ไม่ได้เข้าสู่ระบบ"));
		}
		var report = imageSyncAuditService.auditAndSync(false, false);
		return ResponseEntity.ok(report);
	}

	@PostMapping("/system/run-retention")
	@ResponseBody
	public ResponseEntity<?> runDataRetention(Principal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "ไม่ได้เข้าสู่ระบบ"));
		}
		var report = dataRetentionService.runFullRetentionCycle();
		return ResponseEntity.ok(report);
	}

	@GetMapping("/system/retention-status")
	@ResponseBody
	public ResponseEntity<?> getRetentionStatus(Principal principal) {
		if (principal == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "ไม่ได้เข้าสู่ระบบ"));
		}
		Map<String, Object> status = Map.of(
				"notificationDeletedDays", dataRetentionService.getNotificationDeletedDays(),
				"notificationAncientDays", dataRetentionService.getNotificationAncientDays(),
				"adminLogDays", dataRetentionService.getAdminLogDays(),
				"facultySyncDays", dataRetentionService.getFacultySyncDays(),
				"storageTrashDays", dataRetentionService.getStorageTrashDays()
		);
		return ResponseEntity.ok(status);
	}

	@PostMapping("/delete-user")
	public String deleteUser(@RequestParam Integer id, @RequestParam Integer type,
			@RequestParam(value = "confirmEmail", required = false) String confirmEmail,
			HttpSession session, Principal p) {
		try {
			String currentUserEmail = p.getName();

			UserDtls userToDelete = userService.getUserById(id);
			if (userToDelete == null) {
				session.setAttribute("errorMsg", "ไม่พบบัญชีที่ระบุ");
				return "redirect:/admin/users?type=" + type;
			}

			// Verify email confirmation
			if (confirmEmail == null || !confirmEmail.equals(userToDelete.getEmail())) {
				session.setAttribute("errorMsg", "อีเมลที่กรอกไม่ตรงกับบัญชีที่ต้องการลบ");
				return "redirect:/admin/users?type=" + type;
			}

			// Check for existing academic requests
			if (userService.hasAcademicRequests(id)) {
				session.setAttribute("errorMsg", "ไม่สามารถลบบัญชีนี้ได้ เนื่องจากมีคำร้องที่ยื่นไปแล้ว");
				return "redirect:/admin/users?type=" + type;
			}

			Boolean canDelete = userService.canDeleteUser(id, currentUserEmail);
			if (!canDelete) {
				session.setAttribute("errorMsg", "ไม่สามารถลบบัญชีนี้ได้");
				return "redirect:/admin/users?type=" + type;
			}

			String deletedEmail = userToDelete.getEmail();
			Boolean deleted = userService.deleteUserById(id);

			if (deleted) {
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
			logger.error("Error deleting user ID {}: {}", id, e.getMessage(), e);
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
		}

		return "redirect:/admin/users?type=" + type;
	}

	@PostMapping("/delete-admin")
	public String deleteAdmin(@RequestParam Integer id, @RequestParam Integer type,
			@RequestParam(value = "confirmEmail", required = false) String confirmEmail,
			HttpSession session, Principal p) {
		try {
			String currentUserEmail = p.getName();

			UserDtls adminToDelete = userService.getUserById(id);
			if (adminToDelete == null) {
				session.setAttribute("errorMsg", "ไม่พบบัญชีที่ระบุ");
				return "redirect:/admin/users?type=" + type;
			}

			// Verify email confirmation
			if (confirmEmail == null || !confirmEmail.equals(adminToDelete.getEmail())) {
				session.setAttribute("errorMsg", "อีเมลที่กรอกไม่ตรงกับบัญชีที่ต้องการลบ");
				return "redirect:/admin/users?type=" + type;
			}

			Boolean canDelete = userService.canDeleteUser(id, currentUserEmail);
			if (!canDelete) {
				session.setAttribute("errorMsg", "ไม่สามารถลบบัญชีของตัวเองได้");
				return "redirect:/admin/users?type=" + type;
			}

			String deletedEmail = adminToDelete.getEmail();
			Boolean deleted = userService.deleteUserById(id);

			if (deleted) {
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
			logger.error("Error deleting admin ID {}: {}", id, e.getMessage(), e);
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการลบบัญชี");
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
		if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()
				|| user.getLastName() == null || user.getLastName().trim().isEmpty()) {
			session.setAttribute("errorMsg", "กรุณากรอกชื่อและนามสกุล");
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
				if (p != null) {
					String oldEmail = p.getName();
					// Check if admin is editing themselves (compare IDs)
					UserDtls currentAdmin = userService.getUserByEmail(oldEmail);
					boolean isSelfEdit = (currentAdmin == null && updatedAdmin.getEmail().equals(user.getEmail()))
							|| (currentAdmin != null && currentAdmin.getId().equals(user.getId()));

					if (isSelfEdit && !oldEmail.equals(updatedAdmin.getEmail())) {
						// Email changed for self — refresh SecurityContext
						var auth = SecurityContextHolder.getContext().getAuthentication();
						var newAuth = new UsernamePasswordAuthenticationToken(
								updatedAdmin.getEmail(), auth.getCredentials(), auth.getAuthorities());
						SecurityContextHolder.getContext().setAuthentication(newAuth);
					}

					// Log the admin who performed the edit, not the one who was
					// edited — on a self-edit those are the same account, and the
					// updated name is the more accurate one.
					String logEmail = isSelfEdit ? updatedAdmin.getEmail() : oldEmail;
					String logName = isSelfEdit
							? updatedAdmin.getName()
							: (currentAdmin != null ? currentAdmin.getName() : oldEmail);
					adminLogService.log(
							logEmail,
							logName,
							"EDIT_ADMIN_ACCOUNT",
							"แก้ไขบัญชีแอดมิน ID:" + user.getId() + " (" + user.getEmail() + ")",
							getClientIpAddress(request));
				}
				session.setAttribute("succMsg", "อัพเดทข้อมูลแอดมินสำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			}
		} catch (Exception e) {
			logger.error("Error updating admin ID {}: {}", user.getId(), e.getMessage(), e);
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
		}

		int targetType = (user.getRole() != null && "ROLE_USER".equals(user.getRole())) ? 1 : 2;
		return "redirect:/admin/users?type=" + targetType;
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
		if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()
				|| user.getLastName() == null || user.getLastName().trim().isEmpty()) {
			session.setAttribute("errorMsg", "กรุณากรอกชื่อและนามสกุล");
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
				if (p != null) {
					String oldEmail = p.getName();
					UserDtls currentAdmin = userService.getUserByEmail(oldEmail);

					// Admin editing a user (not self) — no SecurityContext change needed
					adminLogService.log(
							oldEmail,
							currentAdmin != null ? currentAdmin.getName() : oldEmail,
							"EDIT_USER_ACCOUNT",
							"แก้ไขบัญชีผู้ใช้ ID:" + user.getId() + " (" + user.getEmail() + ")",
							getClientIpAddress(request));
				}
				session.setAttribute("succMsg", "อัพเดทข้อมูลผู้ใช้สำเร็จ");
			} else {
				session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
			}
		} catch (Exception e) {
			logger.error("Error updating user ID {}: {}", user.getId(), e.getMessage(), e);
			session.setAttribute("errorMsg", "เกิดข้อผิดพลาดในการอัพเดทข้อมูล");
		}

		int targetType = (user.getRole() != null && "ROLE_ADMIN".equals(user.getRole())) ? 2 : 1;
		return "redirect:/admin/users?type=" + targetType;
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
		return CsvExportUtils.escapeCsv(value);
	}
}
