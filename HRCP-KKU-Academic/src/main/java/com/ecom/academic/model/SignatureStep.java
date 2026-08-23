package com.ecom.academic.model;

import java.time.LocalDateTime;

import com.ecom.model.UserDtls;

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
import jakarta.persistence.Table;

/**
 * One person's turn to sign, and the evidence of what they did.
 *
 * <p>Much of this is snapshot data — the signer's name, their signature image
 * path, the hash of what they saw. That duplication is the point: a signing
 * record has to stay true even after the account is renamed, the signature is
 * deleted from its owner's library, or the document is superseded.
 */
@Entity
@Table(name = "signature_step", indexes = {
        @Index(name = "idx_sig_step_signer", columnList = "signer_user_id, status"),
        @Index(name = "idx_sig_step_order", columnList = "signature_request_id, step_order")
})
public class SignatureStep {

    /**
     * Wording the signer must accept before signing.
     *
     * <p>Stored by version on each step rather than only rendered: if this text
     * is ever reworded, past evidence must still point at what that person
     * actually agreed to, not at today's phrasing.
     */
    public static final String CONSENT_TEXT_VERSION = "v1";

    public static final String CONSENT_TEXT = "ข้าพเจ้าได้ตรวจสอบเอกสารฉบับนี้แล้ว "
            + "และยินยอมลงลายมือชื่ออิเล็กทรอนิกส์ในเอกสารดังกล่าวด้วยความสมัครใจ "
            + "โดยรับทราบว่าการลงลายมือชื่ออิเล็กทรอนิกส์นี้มีผลผูกพันตามกฎหมายเช่นเดียวกับการลงลายมือชื่อด้วยตนเอง "
            + "ตามพระราชบัญญัติว่าด้วยธุรกรรมทางอิเล็กทรอนิกส์ พ.ศ. 2544 และที่แก้ไขเพิ่มเติม";

    /** How the signer was identified at the moment of signing. */
    public static final String AUTH_METHOD_SESSION = "SESSION";
    public static final String AUTH_METHOD_OTP = "EMAIL_OTP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matches the {@code ON DELETE CASCADE} in V6 — see {@link SignatureAuditEvent}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signature_request_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private SignatureRequest signatureRequest;

    /** Position in the chain. Equal values may sign in any order. */
    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    /** Which slot of the template this fills, from {@code SignatureAnchorRegistry}. */
    @Column(name = "slot_key", length = 50, nullable = false)
    private String slotKey;

    @Column(name = "role_label", length = 150)
    private String roleLabel;

    @Column(name = "anchor_placeholder", length = 100, nullable = false)
    private String anchorPlaceholder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_user_id")
    private UserDtls signer;

    @Column(name = "signer_name_snapshot", length = 255)
    private String signerNameSnapshot;

    @Column(name = "signer_position_snapshot", length = 255)
    private String signerPositionSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private SignatureStepStatus status = SignatureStepStatus.WAITING;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "reminded_at")
    private LocalDateTime remindedAt;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_signature_id")
    private UserSignature userSignature;

    /** The image file used here, copied so a later library deletion cannot alter it. */
    @Column(name = "image_path_snapshot", length = 255)
    private String imagePathSnapshot;

    @Column(name = "consent_accepted", nullable = false)
    private Boolean consentAccepted = false;

    @Column(name = "consent_text_version", length = 30)
    private String consentTextVersion;

    @Column(name = "auth_method", length = 30)
    private String authMethod;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** Hash of the frozen content at the moment this person signed. */
    @Column(name = "doc_hash_signed", length = 64)
    private String docHashSigned;

    /** Tamper-evident seal over the evidence fields. Filled in a later phase. */
    @Column(name = "evidence_hmac", length = 128)
    private String evidenceHmac;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delegated_from_user_id")
    private UserDtls delegatedFrom;

    @Column(name = "delegate_reason", length = 500)
    private String delegateReason;

    /**
     * Whether this person signs on someone else's behalf ("ปฏิบัติราชการแทน").
     *
     * <p>Keyed on the reason rather than {@link #delegatedFrom}, because the
     * usual case is deputising for an <em>office</em> — "แทนคณบดี" — where the
     * particular absent person is neither known nor relevant. The named
     * predecessor is recorded when it happens to be known.
     */
    public boolean isDelegated() {
        return delegateReason != null && !delegateReason.isBlank();
    }

    /** The name to print, marked when signed on another's behalf. */
    public String getPrintedSignerName() {
        if (isDelegated()) {
            return "(แทน) " + (signerNameSnapshot != null ? signerNameSnapshot : "");
        }
        return signerNameSnapshot;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SignatureRequest getSignatureRequest() {
        return signatureRequest;
    }

    public void setSignatureRequest(SignatureRequest signatureRequest) {
        this.signatureRequest = signatureRequest;
    }

    public Integer getStepOrder() {
        return stepOrder;
    }

    public void setStepOrder(Integer stepOrder) {
        this.stepOrder = stepOrder;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public void setRoleLabel(String roleLabel) {
        this.roleLabel = roleLabel;
    }

    public String getAnchorPlaceholder() {
        return anchorPlaceholder;
    }

    public void setAnchorPlaceholder(String anchorPlaceholder) {
        this.anchorPlaceholder = anchorPlaceholder;
    }

    public UserDtls getSigner() {
        return signer;
    }

    public void setSigner(UserDtls signer) {
        this.signer = signer;
    }

    public String getSignerNameSnapshot() {
        return signerNameSnapshot;
    }

    public void setSignerNameSnapshot(String signerNameSnapshot) {
        this.signerNameSnapshot = signerNameSnapshot;
    }

    public String getSignerPositionSnapshot() {
        return signerPositionSnapshot;
    }

    public void setSignerPositionSnapshot(String signerPositionSnapshot) {
        this.signerPositionSnapshot = signerPositionSnapshot;
    }

    public SignatureStepStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureStepStatus status) {
        this.status = status;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public LocalDateTime getRemindedAt() {
        return remindedAt;
    }

    public void setRemindedAt(LocalDateTime remindedAt) {
        this.remindedAt = remindedAt;
    }

    public LocalDateTime getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(LocalDateTime viewedAt) {
        this.viewedAt = viewedAt;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public LocalDateTime getDeclinedAt() {
        return declinedAt;
    }

    public void setDeclinedAt(LocalDateTime declinedAt) {
        this.declinedAt = declinedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public UserSignature getUserSignature() {
        return userSignature;
    }

    public void setUserSignature(UserSignature userSignature) {
        this.userSignature = userSignature;
    }

    public String getImagePathSnapshot() {
        return imagePathSnapshot;
    }

    public void setImagePathSnapshot(String imagePathSnapshot) {
        this.imagePathSnapshot = imagePathSnapshot;
    }

    public Boolean getConsentAccepted() {
        return consentAccepted;
    }

    public void setConsentAccepted(Boolean consentAccepted) {
        this.consentAccepted = consentAccepted;
    }

    public String getConsentTextVersion() {
        return consentTextVersion;
    }

    public void setConsentTextVersion(String consentTextVersion) {
        this.consentTextVersion = consentTextVersion;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDocHashSigned() {
        return docHashSigned;
    }

    public void setDocHashSigned(String docHashSigned) {
        this.docHashSigned = docHashSigned;
    }

    public String getEvidenceHmac() {
        return evidenceHmac;
    }

    public void setEvidenceHmac(String evidenceHmac) {
        this.evidenceHmac = evidenceHmac;
    }

    public UserDtls getDelegatedFrom() {
        return delegatedFrom;
    }

    public void setDelegatedFrom(UserDtls delegatedFrom) {
        this.delegatedFrom = delegatedFrom;
    }

    public String getDelegateReason() {
        return delegateReason;
    }

    public void setDelegateReason(String delegateReason) {
        this.delegateReason = delegateReason;
    }
}
