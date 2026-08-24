package com.ecom.academic.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * One document sent out to be signed, and the chain of people who must sign it.
 *
 * <p>The important thing this type does is <b>freeze</b>. When an envelope is
 * created it copies the document's form data into {@link #frozenJson} and
 * records its hash. Every signature in the chain is given against that snapshot,
 * and the finished document is rendered from it — not from whatever the form
 * says later. Without that, an administrator could edit a document after the
 * dean signed it and the dean's signature would silently transfer to text they
 * never saw.
 */
@Entity
@Table(name = "signature_request", indexes = {
        @Index(name = "idx_sig_req_document", columnList = "module, request_id, document_type"),
        @Index(name = "idx_sig_req_status_due", columnList = "status, due_at")
})
public class SignatureRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 20, nullable = false)
    private SignatureModule module;

    /** Points at {@code academic_request} or {@code position_request}, per {@link #module}. */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "document_type", nullable = false)
    private Integer documentType;

    @Column(name = "document_label", length = 255)
    private String documentLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private SignatureRequestStatus status = SignatureRequestStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by")
    private UserDtls initiatedBy;

    /** The document's form data as it stood when circulation began. */
    @Column(name = "frozen_json", columnDefinition = "TEXT", nullable = false)
    private String frozenJson;

    /** SHA-256 of {@link #frozenJson}, so tampering is detectable. */
    @Column(name = "frozen_hash", length = 64, nullable = false)
    private String frozenHash;

    /** Public reference for the verification page; unguessable, not sequential. */
    @Column(name = "verification_code", length = 32, nullable = false, unique = true)
    private String verificationCode;

    @Column(name = "signed_docx_path", length = 500)
    private String signedDocxPath;

    @Column(name = "signed_pdf_path", length = 500)
    private String signedPdfPath;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * When staff checked this document and released it for circulation.
     *
     * <p>Null means nobody outside the applicant has been asked to sign yet.
     * The applicant signs their own part straight away, but everything after
     * that waits here until a member of staff has read the document and the
     * files attached to it — the whole point of the review step is that a
     * mistake must not reach the head of department or the dean.
     */
    @Column(name = "circulation_started_at")
    private LocalDateTime circulationStartedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /**
     * Guards against two signers finishing at the same moment.
     *
     * <p>Both would otherwise read "one step left", both would mark the envelope
     * complete, and the second would overwrite the first's work.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "signatureRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<SignatureStep> steps = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** The step currently entitled to sign, if the chain has not finished. */
    public Optional<SignatureStep> activeStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.ACTIVE)
                .min(Comparator.comparingInt(SignatureStep::getStepOrder));
    }

    /** The next step waiting its turn, used to advance the chain. */
    public Optional<SignatureStep> nextWaitingStep() {
        return steps.stream()
                .filter(s -> s.getStatus() == SignatureStepStatus.WAITING)
                .min(Comparator.comparingInt(SignatureStep::getStepOrder));
    }

    public long signedCount() {
        return steps.stream().filter(s -> s.getStatus() == SignatureStepStatus.SIGNED).count();
    }

    public boolean allSigned() {
        return !steps.isEmpty()
                && steps.stream().allMatch(s -> s.getStatus() == SignatureStepStatus.SIGNED);
    }

    /** e.g. "2 / 3" for the progress badge. */
    public String getProgressLabel() {
        return signedCount() + " / " + steps.size();
    }

    public boolean isOverdue() {
        return dueAt != null && status.isOpen() && LocalDateTime.now().isAfter(dueAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SignatureModule getModule() {
        return module;
    }

    public void setModule(SignatureModule module) {
        this.module = module;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Integer getDocumentType() {
        return documentType;
    }

    public void setDocumentType(Integer documentType) {
        this.documentType = documentType;
    }

    public String getDocumentLabel() {
        return documentLabel;
    }

    public void setDocumentLabel(String documentLabel) {
        this.documentLabel = documentLabel;
    }

    public SignatureRequestStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureRequestStatus status) {
        this.status = status;
    }

    public UserDtls getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(UserDtls initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getFrozenJson() {
        return frozenJson;
    }

    public void setFrozenJson(String frozenJson) {
        this.frozenJson = frozenJson;
    }

    public String getFrozenHash() {
        return frozenHash;
    }

    public void setFrozenHash(String frozenHash) {
        this.frozenHash = frozenHash;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getSignedDocxPath() {
        return signedDocxPath;
    }

    public void setSignedDocxPath(String signedDocxPath) {
        this.signedDocxPath = signedDocxPath;
    }

    public String getSignedPdfPath() {
        return signedPdfPath;
    }

    public void setSignedPdfPath(String signedPdfPath) {
        this.signedPdfPath = signedPdfPath;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public LocalDateTime getCirculationStartedAt() {
        return circulationStartedAt;
    }

    public void setCirculationStartedAt(LocalDateTime circulationStartedAt) {
        this.circulationStartedAt = circulationStartedAt;
    }

    /** Whether staff have released this document to the signers after them. */
    public boolean isCirculationStarted() {
        return circulationStartedAt != null;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<SignatureStep> getSteps() {
        return steps;
    }

    public void setSteps(List<SignatureStep> steps) {
        this.steps = steps;
    }

    public void addStep(SignatureStep step) {
        step.setSignatureRequest(this);
        steps.add(step);
    }
}
