package com.ecom.academic.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ecom.model.UserDtls;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@EntityListeners(com.ecom.search.index.SearchIndexListener.class)
@Entity
@Table(name = "position_request", indexes = {
        @Index(name = "idx_pos_req_applicant", columnList = "applicant_id"),
        @Index(name = "idx_pos_req_app_status", columnList = "applicant_id, current_status"),
        @Index(name = "idx_pos_req_status", columnList = "current_status"),
        @Index(name = "idx_pos_req_created", columnList = "created_at DESC")
})
public class PositionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private UserDtls applicant;

    /**
     * ผลประเมินการสอนที่คำร้องนี้ใช้ — หนึ่งต่อหนึ่ง (Flow ข้อ 30: เอกสารประเมินการสอน 1 ชุด)
     * ว่างได้เฉพาะการขอ ศ. ซึ่งไม่ต้องประเมินการสอน
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_evaluation_id", unique = true)
    private AcademicRequest linkedEvaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private PositionRequestStatus currentStatus = PositionRequestStatus.DRAFT;

    @Column(name = "target_position")
    private String targetPosition;

    @Column(name = "evaluation_method")
    private String evaluationMethod;

    @Column(name = "major")
    private String major;

    @Column(name = "major_code")
    private String majorCode;

    @Column(name = "sub_major")
    private String subMajor;

    @Column(name = "sub_major_code")
    private String subMajorCode;

    @Column(name = "submission_date")
    private LocalDateTime submissionDate;

    /** วันที่คณะกรรมการประจำวิทยาลัยฯ มีมติเห็นชอบ (ประกาศ มข. 1670/2569 ข้อ 6) */
    @Column(name = "college_resolution_date")
    private LocalDate collegeResolutionDate;

    /** มติให้แก้ไข: วันที่ได้รับเอกสารแก้ไขครบตามมติ — เป็นวันที่สภามหาวิทยาลัยรับเรื่องแทนวันมติ */
    @Column(name = "corrections_received_date")
    private LocalDate correctionsReceivedDate;

    /** วันที่สภามหาวิทยาลัยมีมติกำหนด/ไม่กำหนดตำแหน่ง (รอบล่าสุด) */
    @Column(name = "council_resolution_date")
    private LocalDate councilResolutionDate;

    /** วันที่ผู้ขอรับทราบมติสภา (รอบล่าสุด) — เริ่มนับ 90 วันของการขอทบทวน (ข้อบังคับ 2569 ข้อ 35) */
    @Column(name = "council_acknowledged_date")
    private LocalDate councilAcknowledgedDate;

    /** ขอทบทวน: วันที่หน่วยงานของส่วนงานรับเรื่อง — ถือเป็นวันที่สภามหาวิทยาลัยรับเรื่อง (ข้อบังคับ 2569 ข้อ 35) รอบล่าสุด */
    @Column(name = "appeal_received_date")
    private LocalDate appealReceivedDate;

    /** ขอทบทวน: วันที่คณะกรรมการประจำส่วนงานเห็นชอบให้เสนอมหาวิทยาลัย (ข้อ 35) รอบล่าสุด */
    @Column(name = "appeal_endorsed_date")
    private LocalDate appealEndorsedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("documentType ASC")
    private List<PositionDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("changedAt DESC")
    private List<PositionStatusHistory> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    public UserDtls getApplicant() {
        return applicant;
    }

    public void setApplicant(UserDtls applicant) {
        this.applicant = applicant;
    }

    public AcademicRequest getLinkedEvaluation() {
        return linkedEvaluation;
    }

    public void setLinkedEvaluation(AcademicRequest linkedEvaluation) {
        this.linkedEvaluation = linkedEvaluation;
    }

    public PositionRequestStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(PositionRequestStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getTargetPosition() {
        if (targetPosition == null) {
            return null;
        }
        String resolved = com.ecom.util.AcademicTitleResolver.resolveThaiAcademicPosition(targetPosition);
        return resolved != null ? resolved : targetPosition;
    }

    public void setTargetPosition(String targetPosition) {
        if (targetPosition == null) {
            this.targetPosition = null;
            return;
        }
        String resolved = com.ecom.util.AcademicTitleResolver.resolveThaiAcademicPosition(targetPosition);
        this.targetPosition = resolved != null ? resolved : targetPosition;
    }

    public String getEvaluationMethod() {
        return evaluationMethod;
    }

    public void setEvaluationMethod(String evaluationMethod) {
        this.evaluationMethod = evaluationMethod;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getMajorCode() {
        return majorCode;
    }

    public void setMajorCode(String majorCode) {
        this.majorCode = majorCode;
    }

    public String getSubMajor() {
        return subMajor;
    }

    public void setSubMajor(String subMajor) {
        this.subMajor = subMajor;
    }

    public String getSubMajorCode() {
        return subMajorCode;
    }

    public void setSubMajorCode(String subMajorCode) {
        this.subMajorCode = subMajorCode;
    }

    public LocalDate getCollegeResolutionDate() {
        return collegeResolutionDate;
    }

    public void setCollegeResolutionDate(LocalDate collegeResolutionDate) {
        this.collegeResolutionDate = collegeResolutionDate;
    }

    public LocalDate getCorrectionsReceivedDate() {
        return correctionsReceivedDate;
    }

    public void setCorrectionsReceivedDate(LocalDate correctionsReceivedDate) {
        this.correctionsReceivedDate = correctionsReceivedDate;
    }

    public LocalDate getCouncilResolutionDate() {
        return councilResolutionDate;
    }

    public void setCouncilResolutionDate(LocalDate councilResolutionDate) {
        this.councilResolutionDate = councilResolutionDate;
    }

    public LocalDate getCouncilAcknowledgedDate() {
        return councilAcknowledgedDate;
    }

    public void setCouncilAcknowledgedDate(LocalDate councilAcknowledgedDate) {
        this.councilAcknowledgedDate = councilAcknowledgedDate;
    }

    public LocalDate getAppealReceivedDate() {
        return appealReceivedDate;
    }

    public void setAppealReceivedDate(LocalDate appealReceivedDate) {
        this.appealReceivedDate = appealReceivedDate;
    }

    public LocalDate getAppealEndorsedDate() {
        return appealEndorsedDate;
    }

    public void setAppealEndorsedDate(LocalDate appealEndorsedDate) {
        this.appealEndorsedDate = appealEndorsedDate;
    }

    public LocalDateTime getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(LocalDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PositionDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<PositionDocument> documents) {
        this.documents = documents;
    }

    public List<PositionStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<PositionStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }
}
