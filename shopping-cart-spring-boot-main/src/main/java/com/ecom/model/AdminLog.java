package com.ecom.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "admin_logs")
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

	// Constructors
	public AdminLog() {
	}

	public AdminLog(String adminEmail, String adminName, String action, String details, String ipAddress) {
		this.adminEmail = adminEmail;
		this.adminName = adminName;
		this.action = action;
		this.details = details;
		this.ipAddress = ipAddress;
		this.timestamp = LocalDateTime.now();
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
}
