package com.ecom.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;
import com.ecom.util.AppConstant;
import com.ecom.util.CommonUtil;

@Service
@Transactional
public class UserServiceImpl implements UserService {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	@Lazy
	private CommonUtil commonUtil;

	@Autowired
	private com.ecom.academic.repository.AcademicRequestRepository academicRequestRepository;

	@Autowired
	private com.ecom.academic.repository.PositionRequestRepository positionRequestRepository;

	@Override
	public Integer getUsersCount() {
		return (int) userRepository.count();
	}

	@Override
	public Integer getNewUsersToday() {
		Date today = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(today);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		Date startOfDay = cal.getTime();
		cal.add(Calendar.DAY_OF_MONTH, 1);
		Date startOfNextDay = cal.getTime();
		return userRepository.countByCreatedDateBetween(startOfDay, startOfNextDay);
	}

	@Override
	public List<UserDtls> getRecentUsers(int limit) {
		return userRepository.findTop5ByOrderByCreatedDateDesc();
	}

	@Override
	public UserDtls saveUser(UserDtls user) {
		user.setRole("ROLE_USER");
		user.setIsEnable(true);
		user.setAccountNonLocked(true);
		user.setFailedAttempt(0);
		user.setIsFirstLogin(true);
		user.setCreatedDate(new Date());
		user.setApplicantId(generateApplicantId());
		if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
			user.setProfileImage("default.png");
		}
		// Generate random placeholder password (user will set via OTP flow)
		String randomPassword = UUID.randomUUID().toString();
		user.setPassword(passwordEncoder.encode(randomPassword));
		return userRepository.save(user);
	}

	@Override
	public UserDtls getUserById(Integer id) {
		Optional<UserDtls> user = userRepository.findById(id);
		return user.orElse(null);
	}

	@Override
	public UserDtls getUserByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	@Override
	public List<UserDtls> getUsers(String role) {
		return userRepository.findByRole(role);
	}

	@Override
	public Boolean updateAccountStatus(Integer id, Boolean status) {
		Optional<UserDtls> findByuser = userRepository.findById(id);
		if (findByuser.isPresent()) {
			UserDtls userDtls = findByuser.get();
			userDtls.setIsEnable(status);
			userRepository.save(userDtls);
			return true;
		}
		return false;
	}

	@Override
	public Boolean updateEmailNotification(Integer id, Boolean enabled) {
		Optional<UserDtls> findByuser = userRepository.findById(id);
		if (findByuser.isPresent()) {
			UserDtls userDtls = findByuser.get();
			userDtls.setEmailNotificationEnabled(enabled);
			userRepository.save(userDtls);
			return true;
		}
		return false;
	}

	@Override
	public void increaseFailedAttempt(UserDtls user) {
		int attempt = user.getFailedAttempt() + 1;
		user.setFailedAttempt(attempt);
		userRepository.save(user);
	}

	@Override
	public void userAccountLock(UserDtls user) {
		user.setAccountNonLocked(false);
		user.setLockTime(new Date());
		userRepository.save(user);
	}

	@Override
	public boolean unlockAccountTimeExpired(UserDtls user) {
		long lockTime = user.getLockTime().getTime();
		long unLockTime = lockTime + AppConstant.UNLOCK_DURATION_TIME;
		long currentTime = System.currentTimeMillis();
		if (unLockTime < currentTime) {
			user.setAccountNonLocked(true);
			user.setFailedAttempt(0);
			user.setLockTime(null);
			userRepository.save(user);
			return true;
		}
		return false;
	}

	@Override
	public void resetAttempt(int userId) {
	}

	@Override
	public void updateUserResetToken(String email, String resetToken) {
		UserDtls findByEmail = userRepository.findByEmail(email);
		findByEmail.setResetToken(resetToken);
		userRepository.save(findByEmail);
	}

	@Override
	public UserDtls getUserByToken(String token) {
		return userRepository.findByResetToken(token);
	}

	@Override
	public UserDtls updateUser(UserDtls user) {
		return userRepository.save(user);
	}

	@Override
	public UserDtls updateUserProfile(UserDtls user, MultipartFile img) {
		UserDtls dbUser = userRepository.findById(user.getId()).get();

		if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
			dbUser.setProfileImage(user.getProfileImage());
		}

		if (!ObjectUtils.isEmpty(dbUser)) {
			dbUser.setTitle(user.getTitle());
			dbUser.setFirstName(user.getFirstName());
			dbUser.setLastName(user.getLastName());
			dbUser.setMobileNumber(user.getMobileNumber());
			dbUser.setAcademicPosition(user.getAcademicPosition());
			dbUser = userRepository.save(dbUser);
		}

		try {
			if (!img.isEmpty()) {
				String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
				File uploadFolder = new File(uploadDir);
				if (!uploadFolder.exists()) {
					uploadFolder.mkdirs();
				}
				Path filePath = Path.of(uploadDir, img.getOriginalFilename());
				Files.copy(img.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dbUser;
	}

	@Override
	public UserDtls saveAdmin(UserDtls user) {
		// ใช้ role ที่เลือกจากฟอร์ม ถ้าไม่ได้เลือกให้ default เป็น ROLE_ADMIN
		if (user.getRole() == null || user.getRole().isEmpty()) {
			user.setRole("ROLE_ADMIN");
		}
		user.setIsEnable(true);
		user.setAccountNonLocked(true);
		user.setFailedAttempt(0);
		user.setIsFirstLogin(true);
		user.setCreatedDate(new Date());
		if ("ROLE_USER".equals(user.getRole())) {
			user.setApplicantId(generateApplicantId());
		}
		if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
			user.setProfileImage("default.png");
		}
		// Generate random placeholder password (admin will set via OTP flow)
		String randomPassword = UUID.randomUUID().toString();
		user.setPassword(passwordEncoder.encode(randomPassword));
		return userRepository.save(user);
	}

	/**
	 * Generates a unique applicant ID in format APP-YYYYMMDD-XXXX.
	 * The 4-digit sequence resets daily.
	 */
	private String generateApplicantId() {
		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String prefix = "APP-" + dateStr + "-";

		UserDtls lastUser = userRepository.findTopByApplicantIdStartingWithOrderByApplicantIdDesc(prefix);
		int nextSeq = 1;
		if (lastUser != null && lastUser.getApplicantId() != null) {
			String lastSeq = lastUser.getApplicantId().substring(prefix.length());
			nextSeq = Integer.parseInt(lastSeq) + 1;
		}
		return prefix + String.format("%04d", nextSeq);
	}

	@Override
	public Boolean existsEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	@Override
	public List<UserDtls> getAllUsers() {
		return userRepository.findAll();
	}

	// ====== OTP First-Time Login Methods ======

	@Override
	public void generateAndSendOtp(String email) throws Exception {
		UserDtls user = userRepository.findByEmail(email);
		if (user == null) {
			throw new Exception("ไม่พบอีเมลนี้ในระบบ");
		}
		if (user.getIsFirstLogin() == null || !user.getIsFirstLogin()) {
			throw new Exception("บัญชีนี้ได้ตั้งรหัสผ่านแล้ว กรุณาเข้าสู่ระบบตามปกติ");
		}

		// Generate 8-digit OTP
		SecureRandom random = new SecureRandom();
		int otpNumber = 10000000 + random.nextInt(90000000);
		String otp = String.valueOf(otpNumber);

		// Save OTP with 5-minute expiry
		user.setOtpCode(otp);
		user.setOtpExpiry(LocalDateTime.now().plusMinutes(5));
		userRepository.save(user);

		// Send OTP email
		commonUtil.sendOtpEmail(email, otp);
	}

	@Override
	public boolean verifyOtp(String email, String otpCode) {
		UserDtls user = userRepository.findByEmail(email);
		if (user == null)
			return false;
		if (user.getOtpCode() == null)
			return false;
		if (user.getOtpExpiry() == null || LocalDateTime.now().isAfter(user.getOtpExpiry()))
			return false;
		return user.getOtpCode().equals(otpCode);
	}

	@Override
	public void activateAccount(String email, String password) {
		UserDtls user = userRepository.findByEmail(email);
		if (user != null) {
			user.setPassword(passwordEncoder.encode(password));
			user.setIsFirstLogin(false);
			user.setOtpCode(null);
			user.setOtpExpiry(null);
			userRepository.save(user);
		}
	}

	// ====== Admin User Management Methods ======

	@Override
	public UserDtls updateUserDetails(UserDtls user, MultipartFile img) {
		try {
			// Fetch existing user from database
			UserDtls dbUser = userRepository.findById(user.getId())
					.orElseThrow(() -> new RuntimeException("User not found"));

			// Check email uniqueness if email is being changed
			if (!dbUser.getEmail().equals(user.getEmail())) {
				// Check if the new email already exists for a different account
				UserDtls existingUser = userRepository.findByEmail(user.getEmail());
				if (existingUser != null && !existingUser.getId().equals(user.getId())) {
					throw new RuntimeException("อีเมลนี้มีในระบบแล้ว");
				}
				// Reset email verification — user must re-verify the new email
				dbUser.setEmailVerified(false);
			}

			dbUser.setTitle(user.getTitle());
			dbUser.setFirstName(user.getFirstName());
			dbUser.setLastName(user.getLastName());
			dbUser.setEmail(user.getEmail());
			dbUser.setMobileNumber(user.getMobileNumber());
			dbUser.setAcademicPosition(user.getAcademicPosition());

			// Handle profile image if provided
			if (img != null && !img.isEmpty()) {
				try {
					String imageName = saveProfileImage(img);
					dbUser.setProfileImage(imageName);
				} catch (Exception e) {
					logger.error("Failed to save profile image for user ID: " + user.getId(), e);
					throw new RuntimeException("Failed to save profile image: " + e.getMessage());
				}
			}

			// Save and return
			UserDtls savedUser = userRepository.save(dbUser);
			logger.info("Successfully updated user details for user ID: " + user.getId());
			return savedUser;
		} catch (RuntimeException e) {
			logger.error("Error updating user details for user ID: " + user.getId(), e);
			throw e; // Re-throw to trigger transaction rollback
		} catch (Exception e) {
			logger.error("Unexpected error updating user details for user ID: " + user.getId(), e);
			throw new RuntimeException("เกิดข้อผิดพลาดในการอัพเดทข้อมูล", e);
		}
	}

	@Override
	public Boolean deleteUserById(Integer id) {
		try {
			Optional<UserDtls> user = userRepository.findById(id);
			if (user.isPresent()) {
				userRepository.deleteById(id);
				logger.info("Successfully deleted user with ID: " + id);
				return true;
			}
			logger.warn("User not found with ID: " + id);
			return false;
		} catch (Exception e) {
			logger.error("Error deleting user with ID: " + id, e);
			throw new RuntimeException("เกิดข้อผิดพลาดในการลบบัญชี", e);
		}
	}

	@Override
	public Boolean canDeleteUser(Integer id, String currentUserEmail) {
		Optional<UserDtls> user = userRepository.findById(id);
		if (user.isEmpty())
			return false;

		// Prevent self-deletion
		if (user.get().getEmail().equals(currentUserEmail)) {
			return false;
		}

		return true;
	}

	@Override
	public Boolean hasAcademicRequests(Integer userId) {
		List<com.ecom.academic.model.AcademicRequest> acRequests = academicRequestRepository.findByApplicantIdOrderByCreatedAtDesc(userId);
		if (acRequests != null && !acRequests.isEmpty()) {
			return true;
		}
		List<com.ecom.academic.model.PositionRequest> posRequests = positionRequestRepository.findByApplicantId(userId);
		if (posRequests != null && !posRequests.isEmpty()) {
			return true;
		}
		return false;
	}

	@Override
	public java.util.Map<String, String> updateProfileImageOnly(Integer id, MultipartFile img) {
		java.util.Map<String, String> result = new java.util.HashMap<>();

		try {
			UserDtls user = userRepository.findById(id)
					.orElseThrow(() -> new RuntimeException("User not found"));

			// Save image file
			String imageName = saveProfileImage(img);
			user.setProfileImage(imageName);
			userRepository.save(user);

			// Return new image URL with timestamp for cache busting
			long timestamp = System.currentTimeMillis();
			String imageUrl = "/img/profile_img/" + imageName + "?t=" + timestamp;

			result.put("success", "true");
			result.put("imageUrl", imageUrl);
			result.put("imageName", imageName);
			
			logger.info("Successfully updated profile image for user ID: " + id);

		} catch (Exception e) {
			logger.error("Error updating profile image for user ID: " + id, e);
			result.put("success", "false");
			result.put("error", "ไม่สามารถบันทึกไฟล์ได้ กรุณาลองใหม่อีกครั้ง");
		}

		return result;
	}

	private String saveProfileImage(MultipartFile img) throws Exception {
		String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
		File uploadFolder = new File(uploadDir);
		if (!uploadFolder.exists()) {
			uploadFolder.mkdirs();
		}

		String imageName = img.getOriginalFilename();
		Path filePath = Path.of(uploadDir, imageName);
		Files.copy(img.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

		return imageName;
	}
}
