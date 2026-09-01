package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;
import com.ecom.academic.repository.AcademicAttachmentRepository;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.RequestStatusHistoryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.repository.AcademicDocumentEditLogRepository;
import com.ecom.model.UserDtls;

@Service
public class AcademicRequestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AcademicRequestService.class);

    private static final Map<Integer, String> DOC_LABELS = new LinkedHashMap<>();
    static {
        DOC_LABELS.put(0, "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน");
        DOC_LABELS.put(1, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน");
        DOC_LABELS.put(2, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ");
        DOC_LABELS.put(3, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน");
        DOC_LABELS.put(4, "บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ");
        DOC_LABELS.put(5, "ข้อเสนอแนะจากคณะอนุกรรมการ");
        DOC_LABELS.put(6, "แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66");
        DOC_LABELS.put(7, "ส่วนที่ 3 แบบประเมินผลการสอน");
        DOC_LABELS.put(8, "บันทึกข้อความ แจ้งผลการประเมินผลการสอน");
    }

    private final AcademicRequestRepository requestRepository;

    private final AcademicDocumentRepository documentRepository;

    private final AcademicAttachmentRepository attachmentRepository;

    private final RequestStatusHistoryRepository historyRepository;

    private final AcademicDocumentEditLogRepository editLogRepository;

    private final AcademicEmailService emailService;
    private final com.ecom.service.AfterCommitRunner afterCommit;

    private final com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository;

    public AcademicRequestService(
            AcademicRequestRepository requestRepository,
            AcademicDocumentRepository documentRepository,
            AcademicAttachmentRepository attachmentRepository,
            RequestStatusHistoryRepository historyRepository,
            AcademicDocumentEditLogRepository editLogRepository,
            AcademicEmailService emailService,
            com.ecom.service.AfterCommitRunner afterCommit,
            com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository) {
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.editLogRepository = editLogRepository;
        this.emailService = emailService;
        this.afterCommit = afterCommit;
        this.signatureRequestRepository = signatureRequestRepository;
    }

    public AcademicRequest createRequest(UserDtls applicant) {
        AcademicRequest request = new AcademicRequest();
        request.setApplicant(applicant);
        request.setCurrentStatus(RequestStatus.RECEIVED);
        request.setSubmissionDate(LocalDateTime.now());
        request = requestRepository.save(request);
        request.generateRequestCode();
        return requestRepository.save(request);
    }

    public Optional<AcademicRequest> findById(Long id) {
        return requestRepository.findById(id);
    }

    public List<AcademicRequest> findByApplicant(Integer applicantId) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
    }

    public List<AcademicRequest> findAll() {
        List<AcademicRequest> all = requestRepository.findAllByOrderByCreatedAtDesc();
        // กรอง draft ออก - แอดมินไม่ต้องเห็น
        return all.stream()
                .filter(r -> r.getCurrentStatus() != RequestStatus.DRAFT)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Refuses a status change the process does not allow.
     *
     * <p>{@code updateStatus} used to be a setter with an audit trail: it took
     * whatever it was handed. A mistyped dropdown could send a request straight
     * from "รับคำร้อง" to "เสร็จสิ้น", skipping the subcommittee, the meeting and
     * the college board — steps 3 to 10 — or pull a closed request back open
     * (GAP-30). The order lives in {@link RequestStatus#allowedNext()}.
     *
     * <p>Moving to the status it is already in is allowed and does nothing
     * surprising: several callers re-assert the current status, and treating
     * that as an error would turn a harmless no-op into a failure.
     */
    private void requireLegalTransition(RequestStatus from, RequestStatus to, Long requestId) {
        if (from == null || from == to) {
            return;
        }
        if (!from.canMoveTo(to)) {
            throw new IllegalStateException(
                    "เปลี่ยนสถานะคำร้อง #%d จาก \"%s\" ไปเป็น \"%s\" ไม่ได้ — ไม่ตรงกับลำดับใน flow (ขั้นที่ทำได้ต่อไป: %s)"
                            .formatted(requestId, from.getThaiLabel(), to.getThaiLabel(),
                                    from.allowedNext().stream().map(RequestStatus::getThaiLabel)
                                            .reduce((a, b) -> a + ", " + b).orElse("ไม่มี — สถานะนี้ปิดแล้ว")));
        }
    }

    @Transactional
    public AcademicRequest updateStatus(Long requestId, RequestStatus newStatus, UserDtls changedBy, String note) {
        return updateStatus(requestId, newStatus, changedBy, note, true);
    }

    @Transactional
    public AcademicRequest updateStatus(Long requestId, RequestStatus newStatus, UserDtls changedBy, String note,
            boolean sendNotification) {
        AcademicRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        RequestStatus oldStatus = request.getCurrentStatus();
        requireLegalTransition(oldStatus, newStatus, requestId);
        request.setCurrentStatus(newStatus);
        requestRepository.save(request);

        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        historyRepository.save(history);

        if (newStatus == RequestStatus.REJECTED) {
            List<com.ecom.academic.model.SignatureRequest> rejectedEnvelopes = signatureRequestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(com.ecom.academic.model.SignatureModule.ACADEMIC, requestId);
            for (com.ecom.academic.model.SignatureRequest env : rejectedEnvelopes) {
                if (env.getStatus().isOpen()) {
                    env.setStatus(com.ecom.academic.model.SignatureRequestStatus.CANCELLED);
                    env.setCancelledAt(LocalDateTime.now());
                    env.setCancelReason("คำร้องถูกปฏิเสธ: " + (note != null ? note : "-"));
                    if (env.getSteps() != null) {
                        env.getSteps().forEach(s -> {
                            if (s.getStatus() == com.ecom.academic.model.SignatureStepStatus.WAITING || s.getStatus() == com.ecom.academic.model.SignatureStepStatus.ACTIVE) {
                                s.setStatus(com.ecom.academic.model.SignatureStepStatus.SKIPPED);
                            }
                        });
                    }
                    signatureRequestRepository.save(env);
                }
            }
        }

        if (sendNotification) {
            // Announced only once the new status is actually committed, and by
            // id so the background thread reads its own copy of the row.
            Long notifyId = request.getId();
            afterCommit.run(() -> emailService.sendStatusChangeEmail(notifyId, oldStatus, newStatus));
        }

        return request;
    }

    @Transactional
    public AcademicRequest setMeetingDate(Long requestId, LocalDateTime meetingDate, String location,
            UserDtls changedBy) {
        AcademicRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        request.setMeetingDate(meetingDate);
        request.setMeetingLocation(location);
        requestRepository.save(request);

        updateStatus(requestId, RequestStatus.MEETING_SCHEDULED, changedBy,
                "นัดหมายวันประชุม: " + meetingDate.toString());

        return request;
    }

    public AcademicRequest setResultFile(Long requestId, String filePath) {
        AcademicRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
        request.setResultFilePath(filePath);
        return requestRepository.save(request);
    }

    public AcademicRequest setRevisionFile(Long requestId, String filePath) {
        AcademicRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
        request.setRevisionFilePath(filePath);
        return requestRepository.save(request);
    }

    public AcademicDocument saveDocument(AcademicRequest request, int documentType, String jsonData,
            String filePath, String label, Integer copyNumber) {
        Optional<AcademicDocument> existing = documentRepository
                .findByRequestIdAndDocumentTypeAndCopyNumber(request.getId(), documentType,
                        copyNumber != null ? copyNumber : 0);

        AcademicDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new AcademicDocument();
            doc.setRequest(request);
            doc.setDocumentType(documentType);
            doc.setCopyNumber(copyNumber != null ? copyNumber : 0);
        }
        doc.setJsonData(jsonData);
        doc.setGeneratedFilePath(filePath);
        
        if (label == null || label.isBlank() || label.matches("^(?:เอกสาร|Document)\\s*ที่?\\s*\\d+$")) {
            label = getDocLabel(documentType);
        }
        doc.setDocumentLabel(label);
        doc.setIsDraft(false);
        return documentRepository.save(doc);
    }

    /**
     * บันทึกแบบร่าง - เก็บเฉพาะ jsonData ไม่สร้างไฟล์ DOCX
     */
    public AcademicDocument saveDraft(AcademicRequest request, int documentType, String jsonData,
            String label, Integer copyNumber) {
        Optional<AcademicDocument> existing = documentRepository
                .findByRequestIdAndDocumentTypeAndCopyNumber(request.getId(), documentType,
                        copyNumber != null ? copyNumber : 0);

        AcademicDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            doc = new AcademicDocument();
            doc.setRequest(request);
            doc.setDocumentType(documentType);
            doc.setCopyNumber(copyNumber != null ? copyNumber : 0);
        }
        doc.setJsonData(jsonData);
        doc.setDocumentLabel(label);
        doc.setIsDraft(true);
        return documentRepository.save(doc);
    }

    public List<AcademicDocument> getDocuments(Long requestId) {
        return documentRepository.findByRequestId(requestId);
    }

    public List<AcademicDocument> getDocumentsByType(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocumentType(requestId, documentType);
    }

    // ================== ประตูแก้ไขเอกสารของผู้ยื่น ==================

    /**
     * สถานะที่ปิดกระบวนการไปแล้ว — ไม่เปิดให้แก้เอกสารอีกไม่ว่ากรณีใด
     */
    private static final java.util.EnumSet<RequestStatus> CLOSED_STATUSES = java.util.EnumSet.of(
            RequestStatus.COMPLETED, RequestStatus.COMPLETED_PASS, RequestStatus.COMPLETED_REVISE,
            RequestStatus.COMPLETED_FAIL, RequestStatus.REJECTED);

    /**
     * ผู้ยื่นแก้ไขเอกสารฉบับนี้ได้หรือไม่
     *
     * <p>ก่อนส่งคำร้อง (DRAFT) แก้ได้ตามปกติ หลังส่งแล้วเอกสารถือว่าอยู่ในมือแอดมิน
     * และจะกลับมาแก้ได้ก็ต่อเมื่อแอดมินกด "ขอให้แก้ไขและลงนามใหม่" ส่งกลับมาเท่านั้น
     *
     * <p>ต้องไม่มีซองลงนามค้างอยู่ด้วย เพราะการแก้เอกสารที่ส่งเวียนหรือลงนามไปแล้ว
     * เท่ากับเปลี่ยนเนื้อหาใต้ลายเซ็นที่ผู้ลงนามไม่เคยเห็น — เป็นกติกาเดียวกับที่
     * ฝั่งแอดมินใช้อยู่ใน {@code AcademicAdminController.generateDocument}
     */
    public boolean canApplicantEditDocument(AcademicRequest request, int documentType) {
        if (request == null || request.getCurrentStatus() == null) {
            return false;
        }
        if (request.getCurrentStatus() == RequestStatus.DRAFT) {
            return true;
        }
        if (CLOSED_STATUSES.contains(request.getCurrentStatus())) {
            return false;
        }
        if (isDocumentSignatureLocked(request.getId(), documentType)) {
            return false;
        }
        return isRevisionRequested(request.getId(), documentType);
    }

    /** เอกสารกำลังเวียนลงนาม หรือลงนามครบแล้ว */
    private boolean isDocumentSignatureLocked(Long requestId, int documentType) {
        return !signatureRequestRepository.findBlockingEnvelopes(
                com.ecom.academic.model.SignatureModule.ACADEMIC, requestId, documentType).isEmpty();
    }

    /** แอดมินส่งเอกสารฉบับนี้กลับมาให้ผู้ยื่นแก้ไขแล้วหรือยัง */
    public boolean isRevisionRequested(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocumentType(requestId, documentType).stream()
                .anyMatch(AcademicDocument::isRevisionRequested);
    }

    /** เหตุผลที่แอดมินส่งเอกสารฉบับนี้กลับมาให้แก้ไข (ถ้ามี) */
    public String getRevisionNote(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocumentType(requestId, documentType).stream()
                .filter(AcademicDocument::isRevisionRequested)
                .map(AcademicDocument::getRevisionNote)
                .filter(note -> note != null && !note.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * แอดมินส่งเอกสารกลับให้ผู้ยื่นแก้ไข — ปลดล็อกเฉพาะเอกสารฉบับที่ระบุ
     *
     * <p>ไม่ต้องล้างค่านี้ตอนผู้ยื่นบันทึกเอกสารเสร็จ เพราะการลงนามใหม่จะสร้างซอง
     * ลงนามใบใหม่ซึ่งล็อกเอกสารกลับเองอยู่แล้ว
     */
    @Transactional
    public void openDocumentForRevision(Long requestId, int documentType, String note) {
        List<AcademicDocument> docs = documentRepository.findByRequestIdAndDocumentType(requestId, documentType);
        String trimmed = (note != null && !note.isBlank()) ? note.trim() : null;
        if (trimmed != null && trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }
        for (AcademicDocument doc : docs) {
            doc.setRevisionRequestedAt(LocalDateTime.now());
            doc.setRevisionNote(trimmed);
        }
        documentRepository.saveAll(docs);
    }

    /**
     * Whether the appointment order names all three subcommittee members.
     *
     * <p>ข้อ 3 is specific — "รายชื่ออนุกรรมการประเมินการสอน จำนวน 3 คน" — and the
     * order that goes out for the dean's signature has a line for each of them.
     * Saving it with a name missing left the request marked "แต่งตั้งอนุกรรมการ"
     * when no such committee had been appointed, and produced an order with a
     * blank line in it (GAP-35).
     *
     * <p>The document is still saved either way; only the status waits.
     */
    boolean namesThreeSubCommitteeMembers(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(jsonData,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
            for (int i = 1; i <= 3; i++) {
                Object name = data.get("committee_" + i + "_name");
                if (name == null || name.toString().isBlank()) {
                    log.info("เอกสารที่ 3 ยังไม่ครบ: ขาดชื่ออนุกรรมการคนที่ {} — สถานะยังไม่เปลี่ยน", i);
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("อ่านรายชื่ออนุกรรมการจากเอกสารที่ 3 ไม่ได้: {}", e.getMessage());
            return false;
        }
    }

    public List<RequestStatusHistory> getStatusHistory(Long requestId) {
        return historyRepository.findByRequestIdOrderByChangedAtDesc(requestId);
    }

    public static Map<Integer, String> getDocLabels() {
        return DOC_LABELS;
    }

    public static String getDocLabel(int type) {
        return DOC_LABELS.getOrDefault(type, "เอกสารที่ " + type);
    }

    // ================== Document Edit Logging ==================

    public void logDocumentEdit(AcademicRequest request, int documentType, String label,
            UserDtls user, AcademicDocumentEditLog.EditAction action) {
        AcademicDocumentEditLog logEntry = new AcademicDocumentEditLog();
        logEntry.setRequest(request);
        logEntry.setDocumentType(documentType);
        logEntry.setDocumentLabel(label != null ? label : getDocLabel(documentType));
        logEntry.setEditedBy(user);
        logEntry.setAction(action);
        editLogRepository.save(logEntry);
    }

    public List<AcademicDocumentEditLog> getEditHistory(Long requestId) {
        AcademicRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) return List.of();
        return editLogRepository.findByRequestOrderByEditedAtDesc(request);
    }

    public AcademicRequest save(AcademicRequest request) {
        return requestRepository.save(request);
    }

    /**
     * Whether this applicant still has a request in flight, which is what stops
     * them opening a second one.
     *
     * <p>Which statuses count as finished is asked of {@link
     * RequestStatus#isTerminal()} rather than listed here. Listing them is how
     * this went wrong before: the list named REJECTED and COMPLETED but not
     * COMPLETED_FAIL, so an applicant told their teaching evaluation did not
     * pass could never ask to be evaluated again — the one situation where
     * asking again is the whole point.
     *
     * <p>{@code DRAFT} is excluded on top of that, and has to be: {@code
     * newRequestForm} finds or creates the draft only after this check, so a
     * draft counting as active would lock people out of their own unfinished
     * form. (The position flow does the opposite for its own reasons — see
     * {@code PositionRequestService.hasActiveRequest}.)
     *
     * <p>COMPLETED_PASS and COMPLETED_REVISE stay active on purpose: a passed
     * result still feeds a position request, and a revise still needs work.
     */
    public boolean hasActiveRequest(Integer applicantId) {
        List<RequestStatus> excludedStatuses = Stream.concat(
                Arrays.stream(RequestStatus.values()).filter(RequestStatus::isTerminal),
                Stream.of(RequestStatus.DRAFT))
                .toList();

        List<AcademicRequest> activeRequests = requestRepository
                .findByApplicantIdAndCurrentStatusNotIn(applicantId, excludedStatuses);
        return !activeRequests.isEmpty();
    }

    /**
     * ค้นหา draft request ของ applicant (ยังไม่ส่งคำร้อง)
     */
    public AcademicRequest findDraftByApplicant(Integer applicantId) {
        List<AcademicRequest> drafts = requestRepository
                .findByApplicantIdAndCurrentStatus(applicantId, RequestStatus.DRAFT);
        return drafts.isEmpty() ? null : drafts.get(0);
    }

    /**
     * สร้าง draft request ใหม่ (ยังไม่ submit)
     */
    public AcademicRequest createDraftRequest(UserDtls applicant) {
        AcademicRequest request = new AcademicRequest();
        request.setApplicant(applicant);
        request.setCurrentStatus(RequestStatus.DRAFT);
        request = requestRepository.save(request);
        request.generateRequestCode();
        return requestRepository.save(request);
    }

    /**
     * แปลง draft request เป็น request จริง (submit)
     */
    @Transactional
    public AcademicRequest submitDraftRequest(AcademicRequest draftRequest) {
        draftRequest.setCurrentStatus(RequestStatus.RECEIVED);
        draftRequest.setSubmissionDate(LocalDateTime.now());
        return requestRepository.save(draftRequest);
    }

    /**
     * ยกเลิก/ลบ draft request พร้อมลบไฟล์และโฟลเดอร์บนดิสก์ทั้งหมด
     */
    @Transactional
    public boolean deleteDraftRequest(Long requestId, Integer applicantId) {
        Optional<AcademicRequest> opt = requestRepository.findById(requestId);
        if (opt.isPresent()) {
            AcademicRequest req = opt.get();
            if (req.getApplicant().getId().equals(applicantId) && req.getCurrentStatus() == RequestStatus.DRAFT) {
                List<AcademicAttachment> attachments = attachmentRepository.findByRequestIdOrderByUploadedAtDesc(requestId);
                for (AcademicAttachment att : attachments) {
                    deletePhysicalFile(att.getStoredFilePath());
                }
                if (!attachments.isEmpty()) {
                    attachmentRepository.deleteAll(attachments);
                }

                // Delete revision file if present
                if (req.getRevisionFilePath() != null) {
                    deletePhysicalFile(req.getRevisionFilePath());
                }

                // Delete / clean up any signature requests associated with this draft request
                List<com.ecom.academic.model.SignatureRequest> draftEnvelopes = signatureRequestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(com.ecom.academic.model.SignatureModule.ACADEMIC, requestId);
                if (!draftEnvelopes.isEmpty()) {
                    signatureRequestRepository.deleteAll(draftEnvelopes);
                }

                // Delete entire request folder from disk
                try {
                    java.nio.file.Path requestDir = java.nio.file.Path.of("uploads/academic/" + requestId);
                    if (java.nio.file.Files.exists(requestDir)) {
                        org.springframework.util.FileSystemUtils.deleteRecursively(requestDir);
                    }
                } catch (Exception e) {
                    log.warn("Could not delete request folder for #{}: {}", requestId, e.getMessage());
                }

                requestRepository.delete(req);
                return true;
            }
        }
        return false;
    }

    // ==================== Attachment Methods ====================

    public AcademicAttachment saveAttachment(AcademicAttachment attachment) {
        return attachmentRepository.save(attachment);
    }

    public List<AcademicAttachment> getAttachments(Long requestId) {
        return attachmentRepository.findByRequestIdOrderByUploadedAtDesc(requestId);
    }

    public long countAttachments(Long requestId) {
        return attachmentRepository.countByRequestId(requestId);
    }

    public long getTotalAttachmentSize(Long requestId) {
        return getAttachments(requestId).stream()
                .mapToLong(att -> att.getFileSize() != null ? att.getFileSize() : 0L)
                .sum();
    }

    public Optional<AcademicAttachment> findAttachmentById(Long attachmentId) {
        return attachmentRepository.findById(attachmentId);
    }

    public void deleteAttachment(Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(att -> {
            deletePhysicalFile(att.getStoredFilePath());
            attachmentRepository.delete(att);
        });
    }

    private void deletePhysicalFile(String filePath) {
        if (filePath != null && !filePath.isBlank()) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(filePath));
            } catch (Exception e) {
                log.warn("Failed to delete physical file {}: {}", filePath, e.getMessage());
            }
        }
    }

    /**
     * อัพเดตสถานะอัตโนมัติตามเอกสารที่กรอกเสร็จ
     * - Doc 3 saved → SUB_COMMITTEE_APPOINTED
     * - Doc 4 saved → MEETING_SCHEDULED
     * - Doc 6 saved → COMPLETED_PASS or COMPLETED_REVISE (based on score)
     * - Doc 8 saved → COMPLETED
     */
    @Transactional
    public void autoUpdateStatusByDocument(Long requestId, int documentType, UserDtls changedBy, String jsonData) {
        autoUpdateStatusByDocument(requestId, documentType, changedBy, jsonData, true);
    }

    @Transactional
    public void autoUpdateStatusByDocument(Long requestId, int documentType, UserDtls changedBy, String jsonData,
            boolean sendNotify) {
        AcademicRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

        switch (documentType) {
            case 3 -> {
                if (request.getCurrentStatus().canMoveTo(RequestStatus.SUB_COMMITTEE_APPOINTED)
                        && namesThreeSubCommitteeMembers(jsonData)) {
                    updateStatus(requestId, RequestStatus.SUB_COMMITTEE_APPOINTED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารคำสั่งแต่งตั้งอนุกรรมการ", sendNotify);
                }
            }
            case 5 -> {
                // Doc 5 auto-status is handled separately via /send-suggestion endpoint
            }
            case 4 -> {
                if (request.getCurrentStatus().canMoveTo(RequestStatus.MEETING_SCHEDULED)) {
                    updateStatus(requestId, RequestStatus.MEETING_SCHEDULED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ", sendNotify);
                }
            }
            case 6 -> {
                try {
                    var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<String, Object> data = objectMapper.readValue(jsonData,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {
                            });
                    String evalLevel = data.getOrDefault("eval_result_level", "").toString();
                    RequestStatus outcome = "ไม่ผ่าน".equals(evalLevel)
                            ? RequestStatus.COMPLETED_FAIL
                            : RequestStatus.COMPLETED_PASS;
                    if (!evalLevel.isEmpty()
                            && request.getCurrentStatus().canMoveTo(outcome)) {
                        updateStatus(requestId, outcome, changedBy,
                                "อัพเดตอัตโนมัติ: ผลการประเมิน - " + evalLevel, sendNotify);
                    }
                } catch (Exception e) {
                    if (request.getCurrentStatus().canMoveTo(RequestStatus.COMPLETED_PASS)) {
                        updateStatus(requestId, RequestStatus.COMPLETED_PASS, changedBy,
                                "อัพเดตอัตโนมัติ: บันทึกแบบฟอร์มประเมิน", sendNotify);
                    }
                }
            }
            case 8 -> {
                // Unconditional until now, so saving this document on a request
                // that had been refused turned that refusal into "เสร็จสิ้น" and
                // mailed the applicant to say so (GAP-31). It also has to wait
                // for the college board's endorsement (ข้อ 9-10) — the document
                // is still saved either way, only the status holds back.
                if (request.getCurrentStatus().canMoveTo(RequestStatus.COMPLETED)) {
                    updateStatus(requestId, RequestStatus.COMPLETED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารแจ้งผลการประเมิน", sendNotify);
                }
            }
        }
    }

    /**
     * ค้นหาคำร้องตามชื่อผู้ยื่น
     */
    public List<AcademicRequest> searchByApplicantName(String name) {
        return requestRepository.searchByNameOrEmail(name);
    }

    /**
     * ดึงเอกสารเรียงตามลำดับ documentType
     */
    public List<AcademicDocument> getDocumentsSorted(Long requestId) {
        return documentRepository.findByRequestIdOrderByDocumentTypeAscCopyNumberAsc(requestId);
    }

    /**
     * ค้นหาวันหมดอายุผลประเมินล่าสุดของผู้ใช้
     * คำนวณ: วันอนุมัติใน Doc 8 + 5 ปี
     */
    public LocalDateTime getLatestEvaluationExpiry(Integer applicantId) {
        // Find completed requests (COMPLETED_PASS or COMPLETED)
        List<AcademicRequest> requests = requestRepository
                .findByApplicantIdOrderByCreatedAtDesc(applicantId);

        for (AcademicRequest req : requests) {
            if (req.getCurrentStatus().carriesAPassedResult()) {

                // If expiry is already computed, return it
                if (req.getEvaluationExpiryDate() != null) {
                    return req.getEvaluationExpiryDate();
                }

                // Try to extract from Doc 8 JSON
                try {
                    Optional<AcademicDocument> doc8 = documentRepository
                            .findByRequestIdAndDocumentTypeAndCopyNumber(req.getId(), 8, 0);
                    if (doc8.isPresent() && doc8.get().getJsonData() != null) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, String> data = mapper.readValue(
                                doc8.get().getJsonData(), java.util.Map.class);

                        // Try evaluation_date or faculty_board_meeting_date
                        String dateStr = data.getOrDefault("evaluation_date",
                                data.getOrDefault("faculty_board_meeting_date", null));

                        if (dateStr != null && !dateStr.isEmpty()) {
                            LocalDateTime approvalDate = parseThaiDate(dateStr);
                            if (approvalDate != null) {
                                LocalDateTime expiry = approvalDate.plusYears(3);
                                req.setEvaluationExpiryDate(expiry);
                                requestRepository.save(req);
                                return expiry;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fall through
                }

                // Fallback: use submission date + 3 years
                if (req.getSubmissionDate() != null) {
                    LocalDateTime expiry = req.getSubmissionDate().plusYears(3);
                    req.setEvaluationExpiryDate(expiry);
                    requestRepository.save(req);
                    return expiry;
                }
            }
        }
        return null;
    }

    private static final String[] THAI_MONTHS = { "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน",
            "พฤษภาคม", "มิถุนายน", "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม" };

    /**
     * Reads a Thai date in any of the forms the documents actually contain.
     *
     * <p>Accepts a month name ({@code "17 กุมภาพันธ์ 2569"}) and the all-numeric
     * form ({@code "17/2/2569"}, {@code "17-2-2569"}), with Thai or Arabic
     * digits, and converts a Buddhist year. Both forms are needed: staff type the
     * month name into most documents, while the expiry field on document 8 has
     * been filled both ways.
     *
     * <p>This is the single date reader for the whole evaluation-expiry rule.
     * There used to be a second one in {@code PositionRequestService.isExpired},
     * which stripped every non-digit and then expected three numbers — a month
     * name left it with two, so it fell through to "assume not expired" and the
     * expiry was, in practice, never enforced (GAP-21).
     *
     * @return the date, or null when the text is not a date at all
     */
    public static LocalDateTime parseThaiDate(String thaiDate) {
        if (thaiDate == null || thaiDate.isBlank()) {
            return null;
        }
        try {
            String normalized = thaiDate
                    .replace("๐", "0").replace("๑", "1").replace("๒", "2")
                    .replace("๓", "3").replace("๔", "4").replace("๕", "5")
                    .replace("๖", "6").replace("๗", "7").replace("๘", "8")
                    .replace("๙", "9").trim();

            String[] parts = normalized.split("[\\s/.\\-]+");
            if (parts.length < 3) {
                return null;
            }

            int day = Integer.parseInt(parts[0]);
            int month = monthOf(parts[1]);
            if (month < 1 || month > 12) {
                return null;
            }

            int year = Integer.parseInt(parts[2]);
            if (year > 2400) {
                year -= 543; // Buddhist era
            }
            return LocalDateTime.of(year, month, day, 0, 0);
        } catch (Exception e) {
            log.debug("Not a date: '{}'", thaiDate);
            return null;
        }
    }

    /** A Thai month name or a month number. */
    private static int monthOf(String token) {
        for (int i = 0; i < THAI_MONTHS.length; i++) {
            if (THAI_MONTHS[i].equals(token)) {
                return i + 1;
            }
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Evaluations this person may put behind a position request.
     *
     * <p>The single definition of "a usable result", so that the dashboard's
     * expiry countdown and the "start a position request" screen can no longer
     * disagree — they did, and a professor whose request stopped at
     * COMPLETED_PASS was told on one screen that their result was valid and on
     * the next that they had none (GAP-20).
     *
     * <p>Step 11 of the flow is "แจ้งผลการประเมินผลการสอนให้ผู้ขอกำหนดตำแหน่ง
     * ทราบเพื่อดำเนินการยื่นขอกำหนดตำแหน่งทางวิชาการต่อไป" — a result that has
     * been announced is usable, whether or not the paperwork behind it has been
     * closed off as COMPLETED.
     */
    public List<AcademicRequest> findUsableEvaluations(Integer applicantId) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId).stream()
                .filter(this::isUsableEvaluation)
                .toList();
    }

    /** Whether one evaluation still backs a position request. */
    public boolean isUsableEvaluation(AcademicRequest request) {
        if (request == null || request.getCurrentStatus() == null) {
            return false;
        }
        if (!request.getCurrentStatus().carriesAPassedResult()) {
            return false;
        }
        // The result is evidenced by document 8, the notification of the result.
        List<AcademicDocument> doc8s = documentRepository
                .findByRequestIdAndDocumentType(request.getId(), 8);
        if (doc8s.isEmpty() || doc8s.get(0).getJsonData() == null) {
            return false;
        }
        return !isEvaluationExpired(doc8s.get(0).getJsonData());
    }

    /**
     * Whether document 8 names an expiry date that has passed.
     *
     * <p>No date means no expiry, which is the historical behaviour and the
     * right default: an older document that simply never carried the field must
     * not be treated as lapsed.
     */
    private boolean isEvaluationExpired(String doc8Json) {
        try {
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(doc8Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
            Object raw = data.get("expiration_date");
            if (raw == null || raw.toString().isBlank()) {
                return false;
            }
            LocalDateTime expiry = parseThaiDate(raw.toString());
            return expiry != null && expiry.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Could not read the expiry date on document 8: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Evaluations whose result lapses before the given moment.
     *
     * <p>Previously ignored its argument entirely and asked for the requests of
     * applicant {@code null}, which is nobody (GAP-22).
     */
    public List<AcademicRequest> findRequestsExpiringSoon(LocalDateTime before) {
        if (before == null) {
            return List.of();
        }
        return requestRepository.findExpiringBefore(before,
                Arrays.stream(RequestStatus.values())
                        .filter(RequestStatus::carriesAPassedResult)
                        .toList());
    }

    public void sendSuggestionEmail(AcademicRequest request, String suggestionsText) {
        Long notifyId = request.getId();
        afterCommit.run(() -> emailService.sendSuggestionEmail(notifyId, suggestionsText));
    }
}
