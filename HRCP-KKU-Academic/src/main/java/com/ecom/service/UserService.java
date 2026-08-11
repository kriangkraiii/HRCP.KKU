package com.ecom.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.UserDtls;

public interface UserService {

	public UserDtls saveUser(UserDtls user);

	public UserDtls getUserByEmail(String email);

	public List<UserDtls> getUsers(String role);

	public Boolean updateAccountStatus(Integer id, Boolean status);

	public void increaseFailedAttempt(UserDtls user);

	public void userAccountLock(UserDtls user);

	public boolean unlockAccountTimeExpired(UserDtls user);

	public void resetAttempt(int userId);

	public void updateUserResetToken(String email, String resetToken);

	public UserDtls getUserByToken(String token);

	public UserDtls updateUser(UserDtls user);

	/**
	 * Applies profile edits to the account identified by {@code authenticatedEmail}.
	 * Any id carried on {@code user} is ignored — callers must never let the
	 * submitted form decide which account is written to.
	 *
	 * @return the updated account, or null if no account matches the email
	 */
	public UserDtls updateUserProfile(UserDtls user, MultipartFile img, String authenticatedEmail);

	public UserDtls saveAdmin(UserDtls user);

	public Boolean existsEmail(String email);

	public UserDtls getUserById(Integer id);

	public Integer getNewUsersToday();

	public List<UserDtls> getRecentUsers(int limit);

	public Integer getUsersCount();

	public List<UserDtls> getAllUsers();

	// OTP First-Time Login methods
	public void generateAndSendOtp(String email) throws Exception;

	public boolean verifyOtp(String email, String otpCode);

	public void activateAccount(String email, String password);

	// Admin User Management methods
	public UserDtls updateUserDetails(UserDtls user, MultipartFile img);

	public Boolean deleteUserById(Integer id);

	public Boolean canDeleteUser(Integer id, String currentUserEmail);

	public Boolean hasAcademicRequests(Integer userId);

	public java.util.Map<String, String> updateProfileImageOnly(Integer id, MultipartFile img);

	public Boolean updateEmailNotification(Integer id, Boolean enabled);
}
