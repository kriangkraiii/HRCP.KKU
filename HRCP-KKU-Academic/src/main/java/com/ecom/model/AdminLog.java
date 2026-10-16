package com.ecom.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "admin_logs", indexes = {
		@Index(name = "idx_admin_logs_timestamp", columnList = "timestamp"),
		@Index(name = "idx_admin_logs_action_ts", columnList = "action, timestamp"),
		@Index(name = "idx_admin_logs_email", columnList = "adminEmail")
})
public class AdminLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String adminEmail;

	@Column(nullable = false)
	private String adminName;

	@Column(nullable = false)
	private String action;

	@Column(length = 1000)
	private String details;

	@Column(nullable = false)
	private LocalDateTime timestamp;

	@Column
	private String ipAddress;

	@Column(length = 500)
	private String resource;

	@Column(length = 500)
	private String userAgent;

	// Constructors
	public AdminLog() {
	}

	public AdminLog(String adminEmail, String adminName, String action, String details, String ipAddress) {
		this.adminEmail = clip(adminEmail, 255);
		this.adminName = clip(adminName, 255);
		this.action = clip(action, 255);
		this.details = clip(details, 1000);
		this.ipAddress = clip(ipAddress, 255);
		this.timestamp = LocalDateTime.now();
	}

	public AdminLog(String adminEmail, String adminName, String action, String details, String ipAddress,
			String resource, String userAgent) {
		this(adminEmail, adminName, action, details, ipAddress);
		this.resource = clip(resource, 500);
		this.userAgent = clip(userAgent, 500);
	}

	/**
	 * ตัดให้พอดีคอลัมน์ — ค่าหลายช่องมาจากผู้ใช้ (อีเมลที่พิมพ์ตอนเข้าสู่ระบบ, URL, User-Agent)
	 * เกินความยาวแล้วการบันทึกบนเธรด async ล้มเงียบ ๆ แถวนั้นหายจากบันทึกกิจกรรมทั้งแถว
	 */
	private static String clip(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getAdminEmail() {
		return adminEmail;
	}

	public void setAdminEmail(String adminEmail) {
		this.adminEmail = adminEmail;
	}

	public String getAdminName() {
		return adminName;
	}

	public void setAdminName(String adminName) {
		this.adminName = adminName;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(LocalDateTime timestamp) {
		this.timestamp = timestamp;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public String getResource() {
		return resource;
	}

	public void setResource(String resource) {
		this.resource = resource;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}
}
