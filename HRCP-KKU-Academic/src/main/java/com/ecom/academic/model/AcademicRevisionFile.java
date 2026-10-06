package com.ecom.academic.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * ไฟล์หรือลิงก์ฉบับแก้ไขที่ผู้ยื่นส่งกลับมา (Flow ข้อ 13) — หนึ่งรอบการแก้ไขส่งได้หลายรายการ
 *
 * <p>แยกจาก {@link AcademicAttachment} เพราะนั่นคือไฟล์แนบของเอกสารที่ 2 ที่ผูกกับช่องในเช็กลิสต์
 * ส่วนนี้คือฉบับแก้ที่เจ้าหน้าที่ส่งต่อคณะอนุกรรมการ และเก็บทุกรอบไว้ให้ย้อนดูได้
 */
@Entity
@Table(name = "academic_revision_file", indexes = {
        @Index(name = "idx_acad_rev_req_round", columnList = "request_id, revision_round")
})
public class AcademicRevisionFile {

    public static final String LINK = "LINK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AcademicRequest request;

    /** รอบการส่งฉบับแก้ เริ่มที่ 1 */
    @Column(name = "revision_round", nullable = false)
    private int round;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    /** path ของไฟล์บนดิสก์ หรือ URL เมื่อเป็นลิงก์ */
    @Column(name = "stored_path", nullable = false, length = 2048)
    private String storedPath;

    @Column(name = "file_type", length = 20)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public boolean isLink() {
        return LINK.equalsIgnoreCase(fileType);
    }

    /** ไอคอน Font Awesome ตามชนิดไฟล์ */
    public String getIcon() {
        if (isLink()) {
            return "fa-link";
        }
        String type = fileType == null ? "" : fileType.toUpperCase();
        return switch (type) {
            case "PDF" -> "fa-file-pdf";
            case "DOC", "DOCX" -> "fa-file-word";
            case "ZIP" -> "fa-file-zipper";
            default -> "fa-file";
        };
    }

    /** ขนาดไฟล์สำหรับแสดงผล ว่างเมื่อเป็นลิงก์ */
    public String getSizeLabel() {
        if (isLink() || fileSize == null) {
            return "";
        }
        if (fileSize < 1024 * 1024) {
            return Math.max(1, fileSize / 1024) + " KB";
        }
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AcademicRequest getRequest() {
        return request;
    }

    public void setRequest(AcademicRequest request) {
        this.request = request;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
