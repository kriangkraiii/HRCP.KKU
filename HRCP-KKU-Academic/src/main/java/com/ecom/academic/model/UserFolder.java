// [ปิดการใช้งาน] คลังไฟล์ส่วนตัวของผู้ยื่น (user storage)
//
// ระบบเคยมีคลังไฟล์สองชุดที่เป็นฝาแฝดกัน ฝั่งแอดมิน (/admin/file-manager)
// กับฝั่งผู้ยื่น (/user/academic/storage) ตอนนี้เหลือใช้เฉพาะฝั่งแอดมิน
//
// ตาราง user_file และ user_folder ถูก drop ไปแล้วด้วย V16__drop_user_storage.sql
// และไฟล์ใน uploads/user-storage/ ถูกลบทิ้ง โค้ดข้างล่างจึงไม่มีอะไรรองรับ
// เก็บไว้เป็นคอมเมนต์ตามที่ตกลงกันไว้ ไม่ได้ลบทิ้ง
//
// ถ้าจะเปิดกลับ ต้องสร้างตารางทั้งสองขึ้นใหม่ก่อน ไม่ใช่แค่ปลดคอมเมนต์

// package com.ecom.academic.model;
//
// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;
//
// import jakarta.persistence.*;
//
// @Entity
// @Table(name = "user_folder", indexes = {
//         @Index(name = "idx_userfolder_owner_parent", columnList = "owner_id, parent_id")
// })
// public class UserFolder {
//
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;
//
//     @Column(nullable = false)
//     private String name;
//
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "parent_id")
//     private UserFolder parent;
//
//     @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
//     private List<UserFolder> children = new ArrayList<>();
//
//     @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
//     private List<UserFile> files = new ArrayList<>();
//
//     @Column(name = "owner_id", nullable = false)
//     private Integer ownerId;
//
//     @Column(name = "created_at")
//     private LocalDateTime createdAt;
//
//     @PrePersist
//     protected void onCreate() { createdAt = LocalDateTime.now(); }
//
//     public Long getId() { return id; }
//     public void setId(Long id) { this.id = id; }
//     public String getName() { return name; }
//     public void setName(String name) { this.name = name; }
//     public UserFolder getParent() { return parent; }
//     public void setParent(UserFolder parent) { this.parent = parent; }
//     public List<UserFolder> getChildren() { return children; }
//     public void setChildren(List<UserFolder> children) { this.children = children; }
//     public List<UserFile> getFiles() { return files; }
//     public void setFiles(List<UserFile> files) { this.files = files; }
//     public Integer getOwnerId() { return ownerId; }
//     public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }
//     public LocalDateTime getCreatedAt() { return createdAt; }
//     public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
// }
