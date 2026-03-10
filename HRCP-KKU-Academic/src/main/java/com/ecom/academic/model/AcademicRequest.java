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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "academic_request")
public class AcademicRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_code", unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private UserDtls applicant;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, columnDefinition = "varchar(50)")
    private RequestStatus currentStatus;

    @Column(name = "submission_date")
    private LocalDateTime submissionDate;

    @Column(name = "meeting_date")
    private LocalDateTime meetingDate;

    @Column(name = "meeting_location")
    private String meetingLocation;

    @Column(name = "result_file_path")
    private String resultFilePath;

    @Column(name = "revision_file_path")
    private String revisionFilePath;

    @Column(name = "evaluation_expiry_date")
    private LocalDateTime evaluationExpiryDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AcademicDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RequestStatusHistory> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Generate request code from ID after entity gets its auto-generated ID */
    public String generateRequestCode() {
        if (this.id == null)
            return null;
        int beYear = (createdAt != null ? createdAt.getYear() : LocalDateTime.now().getYear()) + 543;
        this.requestCode = String.format("KKU-ACAD-%d-%04d", beYear, this.id);
        return this.requestCode;
    }

    /** Get request code, auto-calculate if not yet set */
    public String getRequestCode() {
        if (requestCode == null && id != null) {
            return generateRequestCode();
        }
        return requestCode;
    }

    public void setRequestCode(String requestCode) {
        this.requestCode = requestCode;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserDtls getApplicant() {
        return applicant;
    }

    public void setApplicant(UserDtls applicant) {
        this.applicant = applicant;
    }

    public RequestStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(RequestStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public LocalDateTime getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(LocalDateTime submissionDate) {
        this.submissionDate = submissionDate;
    }

    public LocalDateTime getMeetingDate() {
        return meetingDate;
    }

    public void setMeetingDate(LocalDateTime meetingDate) {
        this.meetingDate = meetingDate;
    }

    public String getMeetingLocation() {
        return meetingLocation;
    }

    public void setMeetingLocation(String meetingLocation) {
        this.meetingLocation = meetingLocation;
    }

    public String getResultFilePath() {
        return resultFilePath;
    }

    public void setResultFilePath(String resultFilePath) {
        this.resultFilePath = resultFilePath;
    }

    public String getRevisionFilePath() {
        return revisionFilePath;
    }

    public void setRevisionFilePath(String revisionFilePath) {
        this.revisionFilePath = revisionFilePath;
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

    public List<AcademicDocument> getDocuments() {
        return documents;
    }

    public void setDocuments(List<AcademicDocument> documents) {
        this.documents = documents;
    }

    public List<RequestStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(List<RequestStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public LocalDateTime getEvaluationExpiryDate() {
        return evaluationExpiryDate;
    }

    public void setEvaluationExpiryDate(LocalDateTime evaluationExpiryDate) {
        this.evaluationExpiryDate = evaluationExpiryDate;
    }
}
