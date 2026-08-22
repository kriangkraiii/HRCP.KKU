package com.ecom.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications", indexes = {
		@Index(name = "idx_notif_recipient_active", columnList = "recipient_id, isDeleted, isRead"),
		@Index(name = "idx_notif_recipient_created", columnList = "recipient_id, createdAt DESC"),
		@Index(name = "idx_notif_recipient_starred", columnList = "recipient_id, isDeleted, isStarred"),
		@Index(name = "idx_notif_recipient_important", columnList = "recipient_id, isDeleted, isImportant")
})
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_id", nullable = false)
	private UserDtls recipient;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id", nullable = true)
	private UserDtls actor;

	@Column(length = 255)
	private String title;

	@Column(nullable = false, length = 1000)
	private String message;

	@Column(length = 255)
	private String link;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false)
	private Boolean isRead = false;

	@Column(nullable = false)
	private Boolean isStarred = false;

	@Column(nullable = false)
	private Boolean isImportant = false;

	@Column(name = "snoozed_until")
	private LocalDateTime snoozedUntil;

	@Column(nullable = false)
	private Boolean isDeleted = false;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (isRead == null) isRead = false;
		if (isStarred == null) isStarred = false;
		if (isImportant == null) isImportant = false;
		if (isDeleted == null) isDeleted = false;
	}

	// Constructors
	public Notification() {
	}

	public Notification(UserDtls recipient, UserDtls actor, NotificationType type, String message) {
		this.recipient = recipient;
		this.actor = actor;
		this.type = type;
		this.message = message;
		this.title = type != null ? type.getThaiLabel() : "การแจ้งเตือน";
		this.isRead = false;
		this.isStarred = false;
		this.isImportant = false;
		this.isDeleted = false;
		this.createdAt = LocalDateTime.now();
	}

	public Notification(UserDtls recipient, UserDtls actor, String title, String message, String link, NotificationType type, Boolean isImportant) {
		this.recipient = recipient;
		this.actor = actor;
		this.title = title != null && !title.isBlank() ? title : (type != null ? type.getThaiLabel() : "การแจ้งเตือน");
		this.message = message;
		this.link = link;
		this.type = type != null ? type : NotificationType.SYSTEM;
		this.isRead = false;
		this.isStarred = false;
		this.isImportant = isImportant != null ? isImportant : false;
		this.isDeleted = false;
		this.createdAt = LocalDateTime.now();
	}

	// Helper for Thai Relative Time
	public String getRelativeTime() {
		if (createdAt == null) return "";
		LocalDateTime now = LocalDateTime.now();
		Duration duration = Duration.between(createdAt, now);
		long seconds = duration.getSeconds();

		if (seconds < 60) {
			return "เมื่อสักครู่";
		} else if (seconds < 3600) {
			long minutes = seconds / 60;
			return minutes + " นาทีที่แล้ว";
		} else if (seconds < 86400) {
			long hours = seconds / 3600;
			return hours + " ชั่วโมงที่แล้ว";
		} else if (seconds < 172800) {
			return "เมื่อวานนี้ " + createdAt.format(DateTimeFormatter.ofPattern("HH:mm"));
		} else if (seconds < 604800) {
			long days = seconds / 86400;
			return days + " วันที่แล้ว";
		} else {
			return createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
		}
	}

	public String getFormattedCreatedAt() {
		if (createdAt == null) return "";
		return createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
	}

	public boolean isSnoozed() {
		return snoozedUntil != null && snoozedUntil.isAfter(LocalDateTime.now());
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public UserDtls getRecipient() {
		return recipient;
	}

	public void setRecipient(UserDtls recipient) {
		this.recipient = recipient;
	}

	public UserDtls getActor() {
		return actor;
	}

	public void setActor(UserDtls actor) {
		this.actor = actor;
	}

	public String getTitle() {
		return title != null && !title.isBlank() ? title : (type != null ? type.getThaiLabel() : "การแจ้งเตือน");
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public NotificationType getType() {
		return type;
	}

	public void setType(NotificationType type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public Boolean getIsRead() {
		return isRead;
	}

	public void setIsRead(Boolean isRead) {
		this.isRead = isRead;
	}

	public Boolean getRead() {
		return isRead;
	}

	public void setRead(Boolean read) {
		this.isRead = read;
	}

	public Boolean getIsStarred() {
		return isStarred != null && isStarred;
	}

	public void setIsStarred(Boolean isStarred) {
		this.isStarred = isStarred;
	}

	public Boolean getIsImportant() {
		return isImportant != null && isImportant;
	}

	public void setIsImportant(Boolean isImportant) {
		this.isImportant = isImportant;
	}

	public LocalDateTime getSnoozedUntil() {
		return snoozedUntil;
	}

	public void setSnoozedUntil(LocalDateTime snoozedUntil) {
		this.snoozedUntil = snoozedUntil;
	}

	public Boolean getIsDeleted() {
		return isDeleted != null && isDeleted;
	}

	public void setIsDeleted(Boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}