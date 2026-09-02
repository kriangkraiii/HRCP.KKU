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

// package com.ecom.academic.repository;
//
// import java.util.List;
// import org.springframework.data.jpa.repository.JpaRepository;
// import com.ecom.academic.model.UserFile;
//
// public interface UserFileRepository extends JpaRepository<UserFile, Long> {
//     List<UserFile> findByOwnerIdAndFolderIsNullAndIsDeletedFalseOrderByOriginalFilenameAsc(Integer ownerId);
//     List<UserFile> findByOwnerIdAndFolderIdAndIsDeletedFalseOrderByOriginalFilenameAsc(Integer ownerId, Long folderId);
//     List<UserFile> findByOwnerIdAndIsDeletedTrue(Integer ownerId);
//     long countByOwnerIdAndIsDeletedTrue(Integer ownerId);
//     List<UserFile> findByOwnerIdAndIsDeletedFalse(Integer ownerId);
//
//     @org.springframework.data.jpa.repository.Query("SELECT f FROM UserFile f WHERE f.ownerId = :ownerId AND f.isDeleted = false AND LOWER(f.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY f.originalFilename ASC")
//     List<UserFile> searchByOwner(@org.springframework.data.repository.query.Param("ownerId") Integer ownerId, @org.springframework.data.repository.query.Param("keyword") String keyword);
//
//     List<UserFile> findByIsDeletedTrueAndDeletedAtBefore(java.time.LocalDateTime cutoff);
// }
