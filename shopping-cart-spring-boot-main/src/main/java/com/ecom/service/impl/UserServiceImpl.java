package com.ecom.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;
import com.ecom.util.AppConstant;
import com.ecom.util.BucketType;
import com.ecom.util.CommonUtil;

@Service
@Transactional
public class UserServiceImpl implements UserService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;
	
@Autowired
@Lazy
private CommonUtil commonUtil;

@Autowired
private FileServiceImpl fileServiceImpl;

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
	    user.setCreatedDate(new Date()); // Add creation date
	    
	    // Set default profile image if none provided
	    if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
	        user.setProfileImage("default.png");
	    }

	    String encodePassword = passwordEncoder.encode(user.getPassword());
	    user.setPassword(encodePassword);
	    UserDtls saveUser = userRepository.save(user);
	    return saveUser;
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
	        dbUser.setName(user.getName());
	        dbUser.setMobileNumber(user.getMobileNumber());
	        dbUser.setAddress(user.getAddress());
	        dbUser.setCity(user.getCity());
	        dbUser.setState(user.getState());
	        dbUser.setPincode(user.getPincode());
	        dbUser = userRepository.save(dbUser);
	    }

	    try {
	        if (!img.isEmpty()) {
	            // Save locally with original filename
	            String uploadDir = System.getProperty("user.dir") + "/uploads/profile_img/";
	            File uploadFolder = new File(uploadDir);
	            if (!uploadFolder.exists()) {
	                uploadFolder.mkdirs();
	            }

	            Path filePath = Paths.get(uploadDir, img.getOriginalFilename());
	            Files.copy(img.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
	        }
	    } catch (Exception e) {
	        e.printStackTrace();
	    }

	    return dbUser;
	}

	@Override
	public UserDtls saveAdmin(UserDtls user) {
	    user.setRole("ROLE_ADMIN");
	    user.setIsEnable(true);
	    user.setAccountNonLocked(true);
	    user.setFailedAttempt(0);
	    user.setCreatedDate(new Date()); // Add creation date
	    
	    // Set default profile image if none provided
	    if (user.getProfileImage() == null || user.getProfileImage().isEmpty()) {
	        user.setProfileImage("default.png");
	    }

	    String encodePassword = passwordEncoder.encode(user.getPassword());
	    user.setPassword(encodePassword);
	    UserDtls saveUser = userRepository.save(user);
	    return saveUser;
	}

	@Override
	public Boolean existsEmail(String email) {
		return userRepository.existsByEmail(email);
	}

	@Override
	public List<UserDtls> getAllUsers() {
		return userRepository.findAll();
	}


}
