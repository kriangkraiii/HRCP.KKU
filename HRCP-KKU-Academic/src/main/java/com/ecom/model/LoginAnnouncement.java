package com.ecom.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ประกาศบนหน้าเข้าสู่ระบบ — มีแถวเดียวทั้งระบบ ({@link #SINGLETON_ID}) แอดมินพิมพ์ข้อความและเปิด/ปิดเอง
 */
@Entity
@Table(name = "login_announcement")
public class LoginAnnouncement {

    public static final int SINGLETON_ID = 1;

    /** ยาวสุดที่รับ — ประกาศบนหน้าเข้าสู่ระบบควรสั้น ยาวกว่านี้ดันฟอร์มเข้าสู่ระบบตกจอ */
    public static final int MAX_LENGTH = 1000;

    @Id
    private Integer id = SINGLETON_ID;

    @Column(nullable = false)
    private boolean enabled;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    public Integer getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    /** แสดงบนหน้าเข้าสู่ระบบหรือไม่ — เปิดอยู่และมีข้อความ */
    public boolean isShowing() {
        return enabled && message != null && !message.isBlank();
    }
}
