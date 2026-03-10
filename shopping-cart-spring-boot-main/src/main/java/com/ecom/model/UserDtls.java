package com.ecom.model;

import java.time.LocalDateTime;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;

@Entity
public class UserDtls {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	private String title;
	private String name;
	private String mobileNumber;
	private String email;
	private String academicPosition;
	private String password;
	private String profileImage;
	private String role;
	private Boolean isEnable;
	private Boolean accountNonLocked;
	private Integer failedAttempt;
	private Date lockTime;
	private String resetToken;

	@Column(name = "is_first_login")
	private Boolean isFirstLogin;

	@Column(name = "otp_code")
	private String otpCode;

	@Column(name = "otp_expiry")
	private LocalDateTime otpExpiry;

	@Column(name = "created_date")
	private Date createdDate;

	@Column(name = "email_notification_enabled")
	private Boolean emailNotificationEnabled;

	@Column(name = "auto_draft_enabled")
	private Boolean autoDraftEnabled = true;

	@Column(name = "expiry_alert_6m")
	private Boolean expiryAlert6m = true;

	@Column(name = "expiry_alert_3m")
	private Boolean expiryAlert3m = true;

	@Column(name = "expiry_alert_1m")
	private Boolean expiryAlert1m = true;

	@Column(name = "expiry_alert_1w")
	private Boolean expiryAlert1w = true;

	public UserDtls() {
	}

	@PrePersist
	protected void onCreate() {
		if (createdDate == null) {
			createdDate = new Date();
		}
		if (isFirstLogin == null) {
			isFirstLogin = true;
		}
		if (autoDraftEnabled == null) {
			autoDraftEnabled = true;
		}
	}

	// Getters and Setters
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public void setMobileNumber(String mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getAcademicPosition() {
		return academicPosition;
	}

	public void setAcademicPosition(String academicPosition) {
		this.academicPosition = academicPosition;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getProfileImage() {
		return profileImage;
	}

	public void setProfileImage(String profileImage) {
		this.profileImage = profileImage;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public Boolean getIsEnable() {
		return isEnable;
	}

	public void setIsEnable(Boolean isEnable) {
		this.isEnable = isEnable;
	}

	public Boolean getAccountNonLocked() {
		return accountNonLocked;
	}

	public void setAccountNonLocked(Boolean accountNonLocked) {
		this.accountNonLocked = accountNonLocked;
	}

	public Integer getFailedAttempt() {
		return failedAttempt;
	}

	public void setFailedAttempt(Integer failedAttempt) {
		this.failedAttempt = failedAttempt;
	}

	public Date getLockTime() {
		return lockTime;
	}

	public void setLockTime(Date lockTime) {
		this.lockTime = lockTime;
	}

	public String getResetToken() {
		return resetToken;
	}

	public void setResetToken(String resetToken) {
		this.resetToken = resetToken;
	}

	public Boolean getIsFirstLogin() {
		return isFirstLogin;
	}

	public void setIsFirstLogin(Boolean isFirstLogin) {
		this.isFirstLogin = isFirstLogin;
	}

	public String getOtpCode() {
		return otpCode;
	}

	public void setOtpCode(String otpCode) {
		this.otpCode = otpCode;
	}

	public LocalDateTime getOtpExpiry() {
		return otpExpiry;
	}

	public void setOtpExpiry(LocalDateTime otpExpiry) {
		this.otpExpiry = otpExpiry;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	public Boolean getEmailNotificationEnabled() {
		return emailNotificationEnabled;
	}

	public void setEmailNotificationEnabled(Boolean emailNotificationEnabled) {
		this.emailNotificationEnabled = emailNotificationEnabled;
	}

	public Boolean getAutoDraftEnabled() {
		return autoDraftEnabled;
	}

	public void setAutoDraftEnabled(Boolean autoDraftEnabled) {
		this.autoDraftEnabled = autoDraftEnabled;
	}

	public Boolean getExpiryAlert6m() {
		return expiryAlert6m;
	}

	public void setExpiryAlert6m(Boolean expiryAlert6m) {
		this.expiryAlert6m = expiryAlert6m;
	}

	public Boolean getExpiryAlert3m() {
		return expiryAlert3m;
	}

	public void setExpiryAlert3m(Boolean expiryAlert3m) {
		this.expiryAlert3m = expiryAlert3m;
	}

	public Boolean getExpiryAlert1m() {
		return expiryAlert1m;
	}

	public void setExpiryAlert1m(Boolean expiryAlert1m) {
		this.expiryAlert1m = expiryAlert1m;
	}

	public Boolean getExpiryAlert1w() {
		return expiryAlert1w;
	}

	public void setExpiryAlert1w(Boolean expiryAlert1w) {
		this.expiryAlert1w = expiryAlert1w;
	}
}
