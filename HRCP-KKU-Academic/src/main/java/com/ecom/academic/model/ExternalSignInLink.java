package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * ลิงก์เข้าสู่ระบบทางอีเมลหนึ่งลิงก์ของผู้ลงนามภายนอก มข. — ใช้ได้ครั้งเดียว มีอายุ เก็บแค่ hash ของ token
 *
 * <p>เลข id ของผู้ใช้และขั้นลงนามเก็บเป็นเลขเฉย ๆ ไม่ใช่ relation แถวจึงอยู่ต่อได้เป็นหลักฐาน
 * ว่าใครเข้าระบบเมื่อไหร่ จากที่ไหน แม้บัญชีหรือซองจะถูกลบไปแล้ว
 */
@Entity
@Table(name = "external_sign_in_link", indexes = {
        @Index(name = "uq_external_sign_in_link_token", columnList = "token_hash", unique = true),
        @Index(name = "idx_external_sign_in_link_user", columnList = "user_id, used_at")
})
public class ExternalSignInLink {

    /** ลิงก์ออกเพราะอะไร — ไว้ตอบคำถามย้อนหลัง */
    public enum Purpose {
        SIGN_REQUEST,
        REMINDER,
        /** ผู้ลงนามขอลิงก์ใหม่เองที่ /auth/link */
        RESEND
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    /** ขั้นลงนามที่ลิงก์พาไป — null ไปกล่องงานลงนาม */
    @Column(name = "step_id")
    private Long stepId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private Purpose purpose;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "used_ip", length = 45)
    private String usedIp;

    protected ExternalSignInLink() {
    }

    public ExternalSignInLink(Integer userId, String tokenHash, Long stepId, Purpose purpose,
            LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.stepId = stepId;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getStepId() {
        return stepId;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public String getUsedIp() {
        return usedIp;
    }
}
