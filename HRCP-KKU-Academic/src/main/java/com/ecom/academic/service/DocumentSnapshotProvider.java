package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.SignatureModule;
import com.ecom.model.UserDtls;

/**
 * Reads a document's saved form data and label, whichever module it belongs to.
 *
 * <p>The two request flows store documents in separate tables with separate
 * services. The signing feature does not care which — it needs the JSON to
 * freeze and a name to show — so that difference is resolved here instead of
 * being repeated at every call site.
 */
@Service
public class DocumentSnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(DocumentSnapshotProvider.class);

    /**
     * สิ่งที่ผู้ลงนามต้องรู้เกี่ยวกับคำร้องก่อนตัดสินใจ — ใครยื่น ยื่นเมื่อไหร่ ขออะไร
     *
     * @param targetPosition ตำแหน่งที่เสนอขอ มีเฉพาะเฟส 2
     * @param major          สาขาวิชา มีเฉพาะเฟส 2
     */
    public record RequestSummary(
            String requestCode,
            String applicantName,
            String statusLabel,
            LocalDateTime submissionDate,
            String targetPosition,
            String major) {
    }

    /**
     * ไฟล์แนบของคำร้อง ในรูปเดียวกันไม่ว่าจะมาจากเฟสไหน
     *
     * @param storedPath ที่อยู่ไฟล์บนดิสก์ หรือ URL เมื่อเป็นไฟล์แนบแบบลิงก์
     */
    public record SignerAttachment(
            Long id,
            String filename,
            String extension,
            Long fileSize,
            LocalDateTime uploadedAt,
            boolean link,
            String storedPath) {

        static SignerAttachment of(AcademicAttachment a) {
            return new SignerAttachment(a.getId(), a.getOriginalFilename(), a.getFileExtension(),
                    a.getFileSize(), a.getUploadedAt(), a.isLink(), a.getStoredFilePath());
        }

        static SignerAttachment of(PositionAttachment a) {
            return new SignerAttachment(a.getId(), a.getOriginalFilename(), a.getFileExtension(),
                    a.getFileSize(), a.getUploadedAt(), "LINK".equalsIgnoreCase(a.getFileType()),
                    a.getStoredFilePath());
        }
    }

    private final AcademicRequestService academicRequestService;
    private final PositionRequestService positionRequestService;

    public DocumentSnapshotProvider(AcademicRequestService academicRequestService,
            PositionRequestService positionRequestService) {
        this.academicRequestService = academicRequestService;
        this.positionRequestService = positionRequestService;
    }

    public boolean requestExists(SignatureModule module, Long requestId) {
        if (requestId == null) {
            return false;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId).isPresent();
            }
            return positionRequestService.findById(requestId).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Retrieves the applicant user account behind a request.
     */
    public UserDtls applicantOf(SignatureModule module, Long requestId) {
        if (requestId == null) {
            return null;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId)
                        .map(com.ecom.academic.model.AcademicRequest::getApplicant)
                        .orElse(null);
            }
            return positionRequestService.findById(requestId)
                    .map(com.ecom.academic.model.PositionRequest::getApplicant)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not find applicant of {} request {}: {}", module, requestId, e.toString());
            return null;
        }
    }

    /**
     * Whether this account is the applicant behind a request.
     *
     * <p>Applicants send their own documents for signature — several forms are
     * signed by the applicant alone — so the signing endpoints need a way to
     * authorise that without granting them anything on other people's requests.
     *
     * @return false when the request does not exist or belongs to someone else
     */
    public boolean isApplicantOf(SignatureModule module, Long requestId, Integer userId) {
        if (requestId == null || userId == null) {
            return false;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId)
                        .map(r -> r.getApplicant() != null && userId.equals(r.getApplicant().getId()))
                        .orElse(false);
            }
            return positionRequestService.findById(requestId)
                    .map(r -> r.getApplicant() != null && userId.equals(r.getApplicant().getId()))
                    .orElse(false);
        } catch (Exception e) {
            // Deny on error: an ownership check that fails open is worse than
            // one that occasionally refuses a legitimate request.
            log.warn("Could not resolve ownership of {} request {}: {}", module, requestId, e.toString());
            return false;
        }
    }

    /** The document's Thai title, or a generic fallback. */
    public String labelFor(SignatureModule module, int documentType) {
        try {
            String label = module == SignatureModule.ACADEMIC
                    ? AcademicRequestService.getDocLabel(documentType)
                    : positionRequestService.getDocLabel(documentType);
            if (label != null && !label.isBlank()) {
                return label;
            }
        } catch (Exception e) {
            log.warn("Could not resolve label for {} doc {}: {}", module, documentType, e.toString());
        }
        return "เอกสารที่ " + documentType;
    }

    /**
     * The most recently saved form data for a document.
     *
     * <p>เฟส 2 เก็บได้ทั้งฉบับที่กดบันทึกแล้วและฉบับร่างที่แก้ทีหลัง โดย
     * {@code findByRequestIdAndDocType} เรียง {@code isDraft ASC} ฉบับร่างจึงอยู่ท้ายแถว
     * การหยิบ {@code get(0)} ตรง ๆ จะได้ของเก่าไปแช่ในซองทั้งที่ผู้ใช้แก้ไปแล้ว —
     * ฉบับร่างคือฉบับที่ถูกเขียนทีหลังเสมอ เพราะ {@code saveDocument} ล้างธงร่างทิ้งทุกครั้ง
     *
     * @return the stored JSON, or null when the document has never been saved —
     *         which is what stops an empty document being sent for signature
     */
    public String currentJsonFor(SignatureModule module, Long requestId, int documentType) {
        try {
            if (module == SignatureModule.ACADEMIC) {
                List<AcademicDocument> documents =
                        academicRequestService.getDocumentsByType(requestId, documentType);
                return documents.isEmpty() ? null : documents.get(0).getJsonData();
            }
            List<PositionDocument> documents =
                    positionRequestService.getDocumentsByType(requestId, documentType);
            if (documents.isEmpty()) {
                return null;
            }
            return documents.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getIsDraft()))
                    .findFirst()
                    .orElse(documents.get(0))
                    .getJsonData();
        } catch (Exception e) {
            log.warn("Could not read saved data for {} request {} doc {}: {}",
                    module, requestId, documentType, e.toString());
            return null;
        }
    }

    /**
     * ฉบับร่างที่เพิ่งถูกแช่แข็งลงซองลายเซ็น กลายเป็นฉบับที่บันทึกแล้ว
     *
     * <p>ปุ่ม "บันทึกและส่งลงนาม" บันทึกผ่าน auto-draft เท่านั้น เอกสารเฟส 2 จึงค้างเป็นแถวร่าง
     * ทั้งที่ลงนามไปแล้ว — หน้าคำร้องนับแต่แถวที่ไม่ใช่ร่าง ({@code findCompletedDocTypes})
     * ปุ่มส่งคำร้องจึงไม่เปิด และเอกสารก็ถูกซองล็อกจนกดบันทึกซ้ำไม่ได้ ผู้ยื่นติดตายตรงนั้น
     *
     * <p>เฟส 1 ไม่ต้อง: ตัดสินความครบของเอกสารจากเนื้อข้อมูล ไม่ใช่จากธงร่าง
     */
    public void markSavedForSigning(SignatureModule module, Long requestId, int documentType,
            String frozenJson) {
        if (module != SignatureModule.POSITION || requestId == null || frozenJson == null) {
            return;
        }
        positionRequestService.findById(requestId).ifPresent(request -> {
            List<PositionDocument> documents =
                    positionRequestService.getDocumentsByType(requestId, documentType);
            PositionDocument draft = documents.stream()
                    .filter(d -> Boolean.TRUE.equals(d.getIsDraft()))
                    .findFirst()
                    .orElse(null);
            if (draft == null) {
                return;
            }
            positionRequestService.saveDocument(request, documentType, frozenJson,
                    draft.getGeneratedFilePath(), draft.getDocumentLabel(), null, draft.getFilledBy());
        });
    }

    /**
     * ตำแหน่งที่คำร้องนี้เสนอขอ — ใช้ตัดส่วนของระดับตำแหน่งอื่นออกจากการตรวจความครบถ้วน
     *
     * @return ชื่อตำแหน่งภาษาไทย หรือ null เมื่อไม่เกี่ยวข้อง (เฟส 1) หรืออ่านไม่ได้
     */
    public String targetPositionFor(SignatureModule module, Long requestId) {
        if (module != SignatureModule.POSITION || requestId == null) {
            return null;
        }
        try {
            return positionRequestService.findById(requestId)
                    .map(com.ecom.academic.model.PositionRequest::getTargetPosition)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not read target position for request {}: {}", requestId, e.toString());
            return null;
        }
    }

    /**
     * Checks if the parent request is currently an unsubmitted DRAFT.
     * Returns false if the request is already submitted or if request does not exist (e.g. unit tests).
     */
    public boolean isDraftRequest(SignatureModule module, Long requestId) {
        if (requestId == null) {
            return false;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId)
                        .map(r -> r.getCurrentStatus() == com.ecom.academic.model.RequestStatus.DRAFT)
                        .orElse(false);
            }
            return positionRequestService.findById(requestId)
                    .map(r -> r.getCurrentStatus() == com.ecom.academic.model.PositionRequestStatus.DRAFT)
                    .orElse(false);
        } catch (Exception e) {
            log.warn("Could not check draft status for {} request {}: {}", module, requestId, e.toString());
            return false;
        }
    }

    /**
     * เอกสารฉบับนี้ยังอยู่ในมือผู้ยื่นหรือไม่ — คำร้องยังไม่ส่ง หรือเจ้าหน้าที่ส่งเอกสาร
     * ฉบับนี้กลับมาให้แก้แล้วแต่ยังไม่ได้ยื่นกลับ
     *
     * <p>ทั้งสองกรณีคือช่วงที่ผู้ยื่นยังแก้เอกสารของตัวเองได้ ถ้าไม่ติดลายเซ็น
     */
    public boolean isOpenForApplicant(SignatureModule module, Long requestId, int documentType) {
        if (isDraftRequest(module, requestId)) {
            return true;
        }
        try {
            return module == SignatureModule.ACADEMIC
                    ? academicRequestService.isRevisionRequested(requestId, documentType)
                    : positionRequestService.isRevisionRequested(requestId, documentType);
        } catch (Exception e) {
            log.warn("Could not check revision state for {} request {} doc {}: {}",
                    module, requestId, documentType, e.toString());
            return false;
        }
    }

    /** เวลาที่เจ้าหน้าที่ส่งเอกสารฉบับนี้กลับให้แก้รอบล่าสุด หรือ null */
    public java.time.LocalDateTime revisionRequestedAt(SignatureModule module, Long requestId, int documentType) {
        try {
            return module == SignatureModule.ACADEMIC
                    ? academicRequestService.latestRevisionRequestedAt(requestId, documentType)
                    : positionRequestService.latestRevisionRequestedAt(requestId, documentType);
        } catch (Exception e) {
            log.warn("Could not read revision time for {} request {} doc {}: {}",
                    module, requestId, documentType, e.toString());
            return null;
        }
    }

    /**
     * ข้อมูลหัวคำร้องสำหรับแสดงบนหน้าลงนาม
     *
     * @return null เมื่อหาคำร้องไม่เจอหรืออ่านไม่ได้ — หน้าลงนามแค่ไม่แสดงการ์ดนั้น
     */
    @Transactional(readOnly = true)
    public RequestSummary summaryOf(SignatureModule module, Long requestId) {
        if (requestId == null) {
            return null;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId)
                        .map(r -> new RequestSummary(r.getRequestCode(), nameOf(r.getApplicant()),
                                r.getCurrentStatus() != null ? r.getCurrentStatus().getThaiLabel() : null,
                                r.getSubmissionDate(), null, null))
                        .orElse(null);
            }
            return positionRequestService.findById(requestId)
                    .map(r -> new RequestSummary(r.getRequestCode(), nameOf(r.getApplicant()),
                            r.getCurrentStatus() != null ? r.getCurrentStatus().getThaiLabel() : null,
                            r.getSubmissionDate(), r.getTargetPosition(), r.getMajor()))
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Could not read summary of {} request {}: {}", module, requestId, e.toString());
            return null;
        }
    }

    /**
     * ไฟล์แนบที่ยังไม่ถูกลบของคำร้อง
     *
     * <p>ไฟล์แนบไม่ได้ถูก freeze ไว้ในซองเหมือนเนื้อเอกสาร สิ่งที่ได้จึงเป็นฉบับปัจจุบันของคำร้อง
     */
    @Transactional(readOnly = true)
    public List<SignerAttachment> attachmentsOf(SignatureModule module, Long requestId) {
        if (requestId == null) {
            return List.of();
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                // getAttachments ของเฟส 1 ไม่กรองไฟล์ที่ลบแล้วให้ ต่างจากเฟส 2
                return academicRequestService.getAttachments(requestId).stream()
                        .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                        .map(SignerAttachment::of)
                        .toList();
            }
            return positionRequestService.getAttachments(requestId).stream()
                    .map(SignerAttachment::of)
                    .toList();
        } catch (Exception e) {
            log.warn("Could not list attachments of {} request {}: {}", module, requestId, e.toString());
            return List.of();
        }
    }

    /**
     * ไฟล์แนบหนึ่งไฟล์ เฉพาะเมื่อเป็นของคำร้องนี้และยังไม่ถูกลบ
     *
     * <p>ตรวจเจ้าของทุกครั้ง เพราะผู้เรียกได้สิทธิ์มาจากขั้นลงนามของคำร้องหนึ่ง ถ้าไม่ตรวจ
     * เลข id ที่เดาเอาก็เปิดไฟล์ของคำร้องใดก็ได้
     */
    @Transactional(readOnly = true)
    public Optional<SignerAttachment> attachmentOf(SignatureModule module, Long requestId, Long attachmentId) {
        if (requestId == null || attachmentId == null) {
            return Optional.empty();
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findAttachmentById(attachmentId)
                        .filter(a -> a.getRequest() != null && requestId.equals(a.getRequest().getId()))
                        .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                        .map(SignerAttachment::of);
            }
            return positionRequestService.findAttachmentById(attachmentId)
                    .filter(a -> a.getRequest() != null && requestId.equals(a.getRequest().getId()))
                    .filter(a -> !Boolean.TRUE.equals(a.getIsDeleted()))
                    .map(SignerAttachment::of);
        } catch (Exception e) {
            log.warn("Could not read attachment {} of {} request {}: {}",
                    attachmentId, module, requestId, e.toString());
            return Optional.empty();
        }
    }

    private static String nameOf(UserDtls user) {
        return user != null ? user.getName() : null;
    }
}
