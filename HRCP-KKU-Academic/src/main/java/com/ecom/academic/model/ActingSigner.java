package com.ecom.academic.model;

import java.time.LocalDateTime;

import com.ecom.model.UserDtls;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * ผู้รักษาการแทนของตำแหน่งลงนามหนึ่งตำแหน่ง (คณบดี / รองคณบดี / หัวหน้าสาขาวิชา)
 *
 * <p>หนึ่งแถวต่อหนึ่ง {@code slotKey} ใช้กับช่องลงนามของตำแหน่งนั้นในทุกเอกสาร ทั้งเฟส 1 และ 2
 * แอดมินเปิด/ปิดเอง — ปิดแล้วข้อมูลผู้รักษาการยังอยู่ เปิดรอบหน้าไม่ต้องกรอกใหม่
 */
@Entity
@Table(name = "acting_signer")
public class ActingSigner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_key", nullable = false, unique = true, length = 50)
    private String slotKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_user_id")
    private UserDtls actingUser;

    /** ตำแหน่งเต็มที่รักษาการแทน ไม่รวมคำว่า "รักษาการแทน" เช่น "คณบดีวิทยาลัยการคอมพิวเตอร์" */
    @Column(name = "position_title", length = 255)
    private String positionTitle;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private UserDtls updatedBy;

    public ActingSigner() {
    }

    public ActingSigner(String slotKey) {
        this.slotKey = slotKey;
    }

    public Long getId() {
        return id;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public UserDtls getActingUser() {
        return actingUser;
    }

    public void setActingUser(UserDtls actingUser) {
        this.actingUser = actingUser;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UserDtls getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UserDtls updatedBy) {
        this.updatedBy = updatedBy;
    }
}
