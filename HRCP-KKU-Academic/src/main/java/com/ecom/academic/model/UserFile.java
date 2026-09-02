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
//
// import jakarta.persistence.*;
//
// @Entity
// @Table(name = "user_file", indexes = {
//         @Index(name = "idx_userfile_owner_folder_del", columnList = "owner_id, folder_id, is_deleted"),
//         @Index(name = "idx_userfile_owner_del", columnList = "owner_id, is_deleted")
// })
// public class UserFile {
//
//     @Id
//     @GeneratedValue(strategy = GenerationType.IDENTITY)
//     private Long id;
//
//     @Column(name = "original_filename", nullable = false)
//     private String originalFilename;
//
//     @Column(name = "stored_file_path", nullable = false)
//     private String storedFilePath;
//
//     @Column(name = "file_size")
//     private Long fileSize;
//
//     @Column(name = "content_type")
//     private String contentType;
//
//     @ManyToOne(fetch = FetchType.LAZY)
//     @JoinColumn(name = "folder_id")
//     private UserFolder folder;
//
//     @Column(name = "owner_id", nullable = false)
//     private Integer ownerId;
//
//     @Column(name = "is_deleted")
//     private Boolean isDeleted = false;
//
//     @Column(name = "deleted_at")
//     private LocalDateTime deletedAt;
//
//     @Column(name = "created_at")
//     private LocalDateTime createdAt;
//
//     @PrePersist
//     protected void onCreate() { createdAt = LocalDateTime.now(); }
//
//     public Long getId() { return id; }
//     public void setId(Long id) { this.id = id; }
//     public String getOriginalFilename() { return originalFilename; }
//     public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
//     public String getStoredFilePath() { return storedFilePath; }
//     public void setStoredFilePath(String storedFilePath) { this.storedFilePath = storedFilePath; }
//     public Long getFileSize() { return fileSize; }
//     public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
//     public String getContentType() { return contentType; }
//     public void setContentType(String contentType) { this.contentType = contentType; }
//     public UserFolder getFolder() { return folder; }
//     public void setFolder(UserFolder folder) { this.folder = folder; }
//     public Integer getOwnerId() { return ownerId; }
//     public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }
//     public Boolean getIsDeleted() { return isDeleted; }
//     public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
//     public LocalDateTime getDeletedAt() { return deletedAt; }
//     public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
//     public LocalDateTime getCreatedAt() { return createdAt; }
//     public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
//
//     public String getFileExtension() {
//         if (originalFilename == null) return "";
//         int dot = originalFilename.lastIndexOf('.');
//         return dot > 0 ? originalFilename.substring(dot + 1).toUpperCase() : "";
//     }
//
//     public String getFormattedSize() {
//         if (fileSize == null || fileSize == 0) return "-";
//         if (fileSize < 1024) return fileSize + " B";
//         if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
//         if (fileSize < 1024L * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
//         return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
//     }
// }
