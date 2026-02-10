package com.ecom.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "recipient_id", nullable = false)
	private UserDtls recipient;

	@ManyToOne
	@JoinColumn(name = "actor_id", nullable = false)
	private UserDtls actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(nullable = false, length = 500)
	private String message;

	@Column(nullable = false)
	private Boolean isRead = false;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	public void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	// Constructors
	public Notification() {
	}

	public Notification(UserDtls recipient, UserDtls actor, NotificationType type, String message) {
		this.recipient = recipient;
		this.actor = actor;
		this.type = type;
		this.message = message;
		this.isRead = false;
		this.createdAt = LocalDateTime.now();
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

	public Boolean getRead() {
		return isRead;
	}

	public void setRead(Boolean read) {
		isRead = read;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}
}