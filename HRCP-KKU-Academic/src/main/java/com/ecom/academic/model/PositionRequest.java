package com.ecom.academic.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ecom.model.UserDtls;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "position_request")
public class PositionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private UserDtls applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_evaluation_id")
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
        return targetPosition;
    }

    public void setTargetPosition(String targetPosition) {
        this.targetPosition = targetPosition;
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
