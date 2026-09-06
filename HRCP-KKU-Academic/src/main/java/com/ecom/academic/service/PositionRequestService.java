package com.ecom.academic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.dto.EvaluationChoice;
import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionAttachmentRepository;
import com.ecom.academic.repository.PositionDocumentEditLogRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.PositionStatusHistoryRepository;
import com.ecom.model.UserDtls;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PositionRequestService {

    private static final Logger log = LoggerFactory.getLogger(PositionRequestService.class);

    private final PositionRequestRepository requestRepository;

    private final PositionDocumentRepository documentRepository;

    private final PositionStatusHistoryRepository statusHistoryRepository;

    private final PositionDocumentEditLogRepository editLogRepository;

    private final AcademicRequestRepository academicRequestRepository;

    /**
     * Owns the "is this evaluation still usable" rule, which both phases need.
     * No cycle: the academic service knows nothing about position requests.
     */
    private final AcademicRequestService academicRequestService;

    private final PositionEmailService emailService;
    private final com.ecom.service.AfterCommitRunner afterCommit;

    private final PositionAttachmentRepository attachmentRepository;

    private final com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository;

    /** Which publications each request puts forward — see {@link PositionRequestPublication}. */
    private final PositionRequestPublicationRepository publicationLinkRepository;

    public PositionRequestService(
            PositionRequestRepository requestRepository,
            PositionDocumentRepository documentRepository,
            PositionStatusHistoryRepository statusHistoryRepository,
            PositionDocumentEditLogRepository editLogRepository,
            AcademicRequestRepository academicRequestRepository,
            AcademicRequestService academicRequestService,
            PositionEmailService emailService,
            com.ecom.service.AfterCommitRunner afterCommit,
            PositionAttachmentRepository attachmentRepository,
            com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository,
            PositionRequestPublicationRepository publicationLinkRepository) {
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.editLogRepository = editLogRepository;
        this.academicRequestRepository = academicRequestRepository;
        this.academicRequestService = academicRequestService;
        this.emailService = emailService;
        this.afterCommit = afterCommit;
        this.attachmentRepository = attachmentRepository;
        this.signatureRequestRepository = signatureRequestRepository;
        this.publicationLinkRepository = publicationLinkRepository;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================== Document labels ==================

    private static final Map<Integer, String> DOC_LABELS = new LinkedHashMap<>();
    static {
        DOC_LABELS.put(1, "แบบ ก.พ.ว. มข. 03 (ประวัติและผลงาน)");
        DOC_LABELS.put(2, "หนังสือแจ้งความประสงค์เรื่องการรับรู้ข้อมูล");
        DOC_LABELS.put(3, "แบบรับรองจริยธรรมและจรรยาบรรณ");
        DOC_LABELS.put(4, "บันทึกรับรองผลงานทางวิชาการ (วิทยานิพนธ์)");
        DOC_LABELS.put(5, "แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา");
        DOC_LABELS.put(6, "บันทึกข้อความจริยธรรมการวิจัย (Exemption)");
        DOC_LABELS.put(7, "แบบฟอร์มตรวจสอบคุณสมบัติ (Checklist)");
        DOC_LABELS.put(8, "แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ");
        DOC_LABELS.put(9, "ลักษณะการมีส่วนร่วมในผลงาน");
    }

    // Applicant fills these (visible to applicant)
    public static final List<Integer> APPLICANT_DOCS = Arrays.asList(1, 2, 3, 4, 6, 9);

    // Admin fills these (hidden from applicant)
    public static final List<Integer> ADMIN_DOCS = Arrays.asList(5, 7, 8);

    // Fields inside an applicant-facing document that only staff/admin may fill.
    // Applicant submissions must never create or overwrite these — see
    // preserveStaffOnlyFields(). Keyed by document type.
    private static final Map<Integer, Set<String>> STAFF_ONLY_FIELDS = Map.of(
            7, Set.of("hr_officer_name", "hr_officer_position",
                    "dean_name", "dean_position", "reason_if_none"));

    /**
     * Strips staff-only fields from an applicant-submitted payload and restores
     * whatever staff previously recorded, so an applicant can neither forge the
     * verification result nor wipe it by re-saving the document.
     *
     * @param submitted parsed form data posted by the applicant (mutated in place)
     * @param existing  the currently stored data for this document, may be null
     */
    public void preserveStaffOnlyFields(int documentType, Map<String, String> submitted,
            Map<String, String> existing) {
        Set<String> protectedKeys = STAFF_ONLY_FIELDS.get(documentType);
        if (protectedKeys == null)
            return;
        for (String key : protectedKeys) {
            submitted.remove(key);
            if (existing != null) {
                String previous = existing.get(key);
                if (previous != null)
                    submitted.put(key, previous);
            }
        }
    }

    /**
     * JSON-in/JSON-out variant of {@link #preserveStaffOnlyFields} for the
     * auto-draft endpoint, which receives a raw request body. Returns the
     * original JSON unchanged when the document has no staff-only fields or the
     * payload cannot be parsed.
     */
    public String preserveStaffOnlyFieldsInJson(Long requestId, int documentType, String jsonData) {
        if (!STAFF_ONLY_FIELDS.containsKey(documentType) || jsonData == null)
            return jsonData;
        try {
            Map<String, String> submitted = objectMapper.readValue(jsonData,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
            preserveStaffOnlyFields(documentType, submitted, getLatestDocumentData(requestId, documentType));
            return objectMapper.writeValueAsString(submitted);
        } catch (Exception e) {
            return jsonData;
        }
    }

    /**
     * Latest stored form data for a document type, or null when nothing saved yet.
     */
    public Map<String, String> getLatestDocumentData(Long requestId, int documentType) {
        List<PositionDocument> docs = getDocumentsByType(requestId, documentType);
        if (docs.isEmpty())
            return null;
        String json = docs.get(docs.size() - 1).getJsonData();
        if (json == null || json.isBlank())
            return null;
        try {
            return objectMapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    public Map<Integer, String> getDocLabels() {
        return DOC_LABELS;
    }

    public Map<Integer, String> getApplicantDocLabels() {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int d : APPLICANT_DOCS)
            m.put(d, DOC_LABELS.get(d));
        return m;
    }

    public Map<Integer, String> getAdminDocLabels() {
        return DOC_LABELS; // admin sees all
    }

    public String getDocLabel(int type) {
        return DOC_LABELS.getOrDefault(type, "เอกสารที่ " + type);
    }

    // ================== Eligibility ==================

    /**
     * ผลประเมินการสอนที่ผู้ยื่นใช้ขอกำหนดตำแหน่งได้
     *
     * <p>Delegates to {@link AcademicRequestService#findUsableEvaluations} rather
     * than deciding for itself. There used to be a second copy of the rule here,
     * and the two disagreed: this one accepted only {@code COMPLETED} while the
     * dashboard's expiry countdown also accepted {@code COMPLETED_PASS}, so a
     * professor could be shown a valid result on one screen and none on the next
     * (GAP-20). Its expiry check could not read a Thai month name either, which
     * meant expiry was effectively never enforced (GAP-21).
     */
    public List<AcademicRequest> getEligibleEvaluations(Integer userId) {
        Set<String> spent = getSpentCourses(userId).keySet();
        return academicRequestService.findUsableEvaluations(userId).stream()
                .filter(evaluation -> !spent.contains(
                        academicRequestService.summarize(evaluation).courseKey()))
                .toList();
    }

    /**
     * Every teaching evaluation this applicant could put forward, each marked
     * with whether its course has already been spent.
     *
     * <p>The unmarked ones are exactly {@link #getEligibleEvaluations}; this adds
     * the ones the rule excludes, and why, so the screen can say so. Anything
     * that failed, was refused, or has lapsed is not here at all — that is not a
     * choice being withheld, it is not a choice.
     */
    public List<EvaluationChoice> getEvaluationChoices(Integer applicantId) {
        Map<String, String> spent = getSpentCourses(applicantId);
        return academicRequestService.findUsableEvaluations(applicantId).stream()
                .map(academicRequestService::summarize)
                .map(summary -> new EvaluationChoice(summary,
                        spent.containsKey(summary.courseKey()),
                        spent.get(summary.courseKey())))
                .toList();
    }

    /**
     * Statuses that consume nothing. A draft has not been submitted, and a
     * rejected request never got anywhere — its author has to be able to put the
     * same teaching result forward again, or one refusal would burn an evaluation
     * that is still perfectly valid. Same rule, and the same reasoning, as
     * {@code ScopusQueryService.STATUSES_THAT_FREE_A_PUBLICATION}.
     */
    private static final List<PositionRequestStatus> STATUSES_THAT_FREE_A_COURSE =
            List.of(PositionRequestStatus.DRAFT, PositionRequestStatus.REJECTED);

    /**
     * The courses this applicant has already spent, each mapped to the request
     * that spent it.
     *
     * <p>Keyed by {@link EvaluationSummary#courseKey()} — course code plus
     * academic year — so that a second evaluation of the same course in the same
     * year is caught too, and so that the same course in a <em>later</em> year is
     * not. The request code rides along because a screen that merely hides a
     * choice leaves the applicant guessing; it can now say which request took it.
     *
     * <p>Derived from {@code linked_evaluation_id} rather than stored in a table
     * of its own: the link is already in the database, the course and the year are
     * already in the evaluation's documents, and a stored copy could only drift
     * from them.
     */
    public Map<String, String> getSpentCourses(Integer applicantId) {
        if (applicantId == null) {
            return Map.of();
        }
        Map<String, String> spent = new LinkedHashMap<>();
        for (PositionRequest request : requestRepository.findConsumingEvaluations(
                applicantId, STATUSES_THAT_FREE_A_COURSE)) {
            EvaluationSummary summary = academicRequestService.summarize(request.getLinkedEvaluation());
            if (summary != null) {
                spent.putIfAbsent(summary.courseKey(),
                        request.getRequestCode() != null ? request.getRequestCode()
                                : String.valueOf(request.getId()));
            }
        }
        return spent;
    }

    /**
     * Whether the evaluation behind this request is still free to be spent.
     *
     * <p>Asked again at submission, not only when the draft was created. A draft
     * can sit for weeks, and in the meantime another request of the same
     * applicant's may have gone in on the same course — the draft was legal when
     * it was made and is not legal now.
     *
     * <p>A request carrying no evaluation is left alone: whether that is
     * allowed is a different rule, and answering "no" here would report the
     * wrong reason.
     */
    public boolean isEvaluationStillAvailable(PositionRequest request) {
        if (request == null || request.getLinkedEvaluation() == null) {
            return true;
        }
        EvaluationSummary summary = academicRequestService.summarize(request.getLinkedEvaluation());
        if (summary == null) {
            return true;
        }
        // getSpentCourses skips DRAFT, so a draft never reads as blocking itself.
        return !getSpentCourses(request.getApplicant().getId()).containsKey(summary.courseKey());
    }

    /** Whether this applicant may still start a request on this evaluation. */
    public boolean canUseEvaluation(Integer applicantId, Long evaluationId) {
        if (evaluationId == null) {
            return false;
        }
        return getEligibleEvaluations(applicantId).stream()
                .anyMatch(evaluation -> evaluationId.equals(evaluation.getId()));
    }

    // ================== Request CRUD ==================

    @Transactional
    public PositionRequest createDraftRequest(UserDtls applicant, Long linkedEvaluationId) {
        PositionRequest request = new PositionRequest();
        request.setApplicant(applicant);
        request.setCurrentStatus(PositionRequestStatus.DRAFT);

        if (linkedEvaluationId != null) {
            AcademicRequest eval = academicRequestRepository.findById(linkedEvaluationId).orElse(null);
            request.setLinkedEvaluation(eval);
        }

        request = requestRepository.save(request);

        // Generate request code: KKU-POS-YYYY-NNNN
        String year = String.valueOf(LocalDateTime.now().getYear() + 543);
        String seq = String.format("%04d", request.getId());
        request.setRequestCode("KKU-POS-" + year + "-" + seq);
        return requestRepository.save(request);
    }

    public Optional<PositionRequest> findById(Long id) {
        return requestRepository.findById(id);
    }

    public List<PositionRequest> findByApplicant(Integer userId) {
        return requestRepository.findByApplicantId(userId);
    }

    public List<PositionRequest> findAll() {
        return requestRepository.findAllOrderByCreatedAtDesc();
    }

    public List<PositionRequest> searchByNameOrEmail(String keyword) {
        return requestRepository.searchByNameOrEmail(keyword);
    }

    /** แบบร่างล่าสุดของผู้ยื่น — ปกติมีได้ฉบับเดียว ถ้าเผลอมีมากกว่านั้นให้เอาฉบับใหม่สุด */
    public Optional<PositionRequest> findDraftByApplicant(Integer userId) {
        return requestRepository.findDraftByApplicantId(userId).stream().findFirst();
    }

    /**
     * ยกเลิก/ลบ draft position request
     */
    @Transactional
    public boolean deleteDraftRequest(Long requestId, Integer applicantId) {
        Optional<PositionRequest> opt = requestRepository.findById(requestId);
        if (opt.isPresent()) {
            PositionRequest req = opt.get();
            if (req.getApplicant().getId().equals(applicantId)
                    && req.getCurrentStatus() == PositionRequestStatus.DRAFT) {
                // Delete attachments and physical files
                List<PositionAttachment> attachments = attachmentRepository.findActiveByRequestId(requestId);
                for (PositionAttachment att : attachments) {
                    deletePhysicalFile(att.getStoredFilePath());
                }
                if (!attachments.isEmpty()) {
                    attachmentRepository.deleteAll(attachments);
                }

                // Delete entire position request folder from disk
                try {
                    java.nio.file.Path requestDir = java.nio.file.Path.of("uploads/position/" + requestId);
                    if (java.nio.file.Files.exists(requestDir)) {
                        org.springframework.util.FileSystemUtils.deleteRecursively(requestDir);
                    }
                } catch (Exception e) {
                    log.warn("Could not delete position request folder for #{}: {}", requestId, e.getMessage());
                }

                // Delete document edit logs
                List<PositionDocumentEditLog> editLogs = editLogRepository.findByRequestOrderByEditedAtDesc(req);
                if (!editLogs.isEmpty()) {
                    editLogRepository.deleteAll(editLogs);
                }
                // Delete child collections
                if (req.getStatusHistory() != null && !req.getStatusHistory().isEmpty()) {
                    statusHistoryRepository.deleteAll(req.getStatusHistory());
                }
                if (req.getDocuments() != null && !req.getDocuments().isEmpty()) {
                    documentRepository.deleteAll(req.getDocuments());
                }

                // Delete / clean up any signature requests associated with this draft request
                List<com.ecom.academic.model.SignatureRequest> draftEnvelopes = signatureRequestRepository
                        .findByModuleAndRequestIdOrderByDocumentTypeAsc(
                                com.ecom.academic.model.SignatureModule.POSITION, requestId);
                if (!draftEnvelopes.isEmpty()) {
                    signatureRequestRepository.deleteAll(draftEnvelopes);
                }

                requestRepository.delete(req);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this person still has a request in flight, which is what stops
     * them opening a second one.
     *
     * <p>Which statuses count as finished is asked of {@link
     * PositionRequestStatus#isTerminal()} rather than listed here. Listing them
     * is how this went wrong before: the list held only {@code SENT_TO_HR}, so a
     * rejected request counted as in flight for ever and its owner could never
     * submit another one — with no way out, since only drafts can be cancelled.
     *
     * <p>{@code DRAFT} deliberately counts as active. An unfinished draft is
     * something to go back to, not a reason to start again, and this is what
     * keeps {@code createRequest} from leaving a second draft behind. The
     * applicant reaches theirs from the "คำร้องฉบับร่าง" card on the dashboard.
     */
    public boolean hasActiveRequest(Integer userId) {
        List<PositionRequestStatus> terminal = Arrays.stream(PositionRequestStatus.values())
                .filter(PositionRequestStatus::isTerminal)
                .toList();
        return !requestRepository.findActiveByApplicantId(userId, terminal).isEmpty();
    }

    @Transactional
    public PositionRequest submitRequest(PositionRequest request) {
        PositionRequestStatus oldStatus = request.getCurrentStatus();
        request.setCurrentStatus(PositionRequestStatus.DOCUMENT_RECEIVED);
        request.setSubmissionDate(LocalDateTime.now());
        request = requestRepository.save(request);

        addStatusHistory(request, oldStatus, PositionRequestStatus.DOCUMENT_RECEIVED, null, "ส่งคำร้องเข้าระบบ");

        // Announced only once the submission is actually committed, and by id
        // so the background thread reads its own copy of the row.
        Long notifyId = request.getId();
        afterCommit.run(() -> emailService.sendNewRequestNotificationToAdmins(notifyId));

        return request;
    }

    /**
     * Refuses a status change the process does not allow — same reasoning as the
     * Phase 1 service. The order lives in
     * {@link PositionRequestStatus#allowedNext()}.
     */
    private void requireLegalTransition(PositionRequestStatus from, PositionRequestStatus to,
            Long requestId) {
        if (from == null || from == to) {
            return;
        }
        if (!from.canMoveTo(to)) {
            throw new IllegalStateException(
                    "เปลี่ยนสถานะคำร้องขอตำแหน่ง #%d จาก \"%s\" ไปเป็น \"%s\" ไม่ได้ — ไม่ตรงกับลำดับใน flow (ขั้นที่ทำได้ต่อไป: %s)"
                            .formatted(requestId, from.getThaiLabel(), to.getThaiLabel(),
                                    from.allowedNext().stream()
                                            .map(PositionRequestStatus::getThaiLabel)
                                            .reduce((a, b) -> a + ", " + b)
                                            .orElse("ไม่มี — สถานะนี้ปิดแล้ว")));
        }
    }

    @Transactional
    public PositionRequest updateStatus(Long requestId, PositionRequestStatus newStatus,
            UserDtls changedBy, String note) {
        return updateStatus(requestId, newStatus, changedBy, note, true);
    }

    @Transactional
    public PositionRequest updateStatus(Long requestId, PositionRequestStatus newStatus,
            UserDtls changedBy, String note, boolean sendNotify) {
        PositionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + requestId));

        PositionRequestStatus oldStatus = request.getCurrentStatus();
        requireLegalTransition(oldStatus, newStatus, requestId);
        request.setCurrentStatus(newStatus);
        request = requestRepository.save(request);

        addStatusHistory(request, oldStatus, newStatus, changedBy, note);

        if (newStatus == PositionRequestStatus.REJECTED) {
            List<com.ecom.academic.model.SignatureRequest> rejectedEnvelopes = signatureRequestRepository.findByModuleAndRequestIdOrderByDocumentTypeAsc(com.ecom.academic.model.SignatureModule.POSITION, requestId);
            for (com.ecom.academic.model.SignatureRequest env : rejectedEnvelopes) {
                if (env.getStatus().isOpen()) {
                    env.setStatus(com.ecom.academic.model.SignatureRequestStatus.CANCELLED);
                    env.setCancelledAt(LocalDateTime.now());
                    env.setCancelReason("คำร้องขอตำแหน่งถูกปฏิเสธ: " + (note != null ? note : "-"));
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

        // Send email notification to applicant if requested
        if (sendNotify) {
            Long notifyId = request.getId();
            afterCommit.run(() -> emailService.sendStatusChangeEmail(notifyId, oldStatus, newStatus));
        }

        return request;
    }

    @Transactional
    public void autoUpdateStatusByDocument(Long requestId, int documentType, UserDtls changedBy, String jsonData,
            boolean sendNotify) {
        PositionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + requestId));

        switch (documentType) {
            case 5, 7, 0 -> {
                if (request.getCurrentStatus().canMoveTo(PositionRequestStatus.DOCUMENT_VERIFICATION)) {
                    updateStatus(requestId, PositionRequestStatus.DOCUMENT_VERIFICATION, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึก" + getDocLabel(documentType), sendNotify);
                }
            }
            case 8 -> {
                if (request.getCurrentStatus().canMoveTo(PositionRequestStatus.SCREENING_COMMITTEE)) {
                    updateStatus(requestId, PositionRequestStatus.SCREENING_COMMITTEE, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ", sendNotify);
                }
            }
        }
    }

    private void addStatusHistory(PositionRequest request, PositionRequestStatus oldStatus,
            PositionRequestStatus newStatus, UserDtls changedBy, String note) {
        PositionStatusHistory h = new PositionStatusHistory();
        h.setRequest(request);
        h.setOldStatus(oldStatus);
        h.setNewStatus(newStatus);
        h.setChangedBy(changedBy);
        h.setNote(note);
        statusHistoryRepository.save(h);
    }

    /** @return status changes, most recent first */
    public List<PositionStatusHistory> getStatusHistory(Long requestId) {
        return statusHistoryRepository.findByRequestIdOrderByChangedAtDesc(requestId);
    }

    // ================== Document Edit Logging ==================

    public void logDocumentEdit(PositionRequest request, int documentType, String label,
            UserDtls user, PositionDocumentEditLog.EditAction action) {
        PositionDocumentEditLog log = new PositionDocumentEditLog();
        log.setRequest(request);
        log.setDocumentType(documentType);
        log.setDocumentLabel(label != null ? label : getDocLabel(documentType));
        log.setEditedBy(user);
        log.setAction(action);
        editLogRepository.save(log);
    }

    public List<PositionDocumentEditLog> getEditHistory(Long requestId) {
        PositionRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) return List.of();
        return editLogRepository.findByRequestOrderByEditedAtDesc(request);
    }

    // ================== Document CRUD ==================

    public PositionDocument saveDocument(PositionRequest request, int documentType, String jsonData,
            String filePath,
            String label, Integer copyNumber, String filledBy) {
        Optional<PositionDocument> existing = documentRepository.findDraftByRequestIdAndDocType(
                request.getId(), documentType);

        PositionDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            // Check if a non-draft exists to update
            List<PositionDocument> docs = documentRepository.findByRequestIdAndDocType(
                    request.getId(), documentType);
            if (!docs.isEmpty() && (copyNumber == null || copyNumber == 0)) {
                doc = docs.get(0);
            } else {
                doc = new PositionDocument();
                doc.setRequest(request);
                doc.setDocumentType(documentType);
                doc.setCopyNumber(copyNumber != null ? copyNumber : 0);
            }
        }
        doc.setJsonData(jsonData);
        doc.setGeneratedFilePath(filePath);
        if (label == null || label.isBlank() || label.matches("^(?:เอกสาร|Document)\\s*ที่?\\s*\\d+$")) {
            label = getDocLabel(documentType);
        }
        doc.setDocumentLabel(label);
        doc.setIsDraft(false);
        doc.setFilledBy(filledBy);
        PositionDocument saved = documentRepository.save(doc);

        // Sync doc 2 fields back to request
        if (documentType == 2 && jsonData != null) {
            syncDoc2ToRequest(request, jsonData);
        }
        syncPublicationLinks(request, documentType, jsonData);

        return saved;
    }

    public PositionDocument saveDraft(PositionRequest request, int documentType, String jsonData,
            String label, String filledBy) {
        Optional<PositionDocument> existing = documentRepository.findDraftByRequestIdAndDocType(
                request.getId(), documentType);

        PositionDocument doc;
        if (existing.isPresent()) {
            doc = existing.get();
        } else {
            List<PositionDocument> docs = documentRepository.findByRequestIdAndDocType(
                    request.getId(), documentType);
            if (!docs.isEmpty() && !docs.get(0).getIsDraft()) {
                // Submitted doc exists — create a new draft, never touch the submitted one
                doc = new PositionDocument();
                doc.setRequest(request);
                doc.setDocumentType(documentType);
                doc.setCopyNumber(0);
            } else if (!docs.isEmpty()) {
                doc = docs.get(0);
            } else {
                doc = new PositionDocument();
                doc.setRequest(request);
                doc.setDocumentType(documentType);
                doc.setCopyNumber(0);
            }
        }
        doc.setJsonData(jsonData);
        doc.setDocumentLabel(label);
        doc.setIsDraft(true);
        doc.setFilledBy(filledBy);
        PositionDocument saved = documentRepository.save(doc);

        // Sync doc 2 fields back to request
        if (documentType == 2 && jsonData != null) {
            syncDoc2ToRequest(request, jsonData);
        }
        // A draft records its publications too. "Still a draft" means the work is
        // not spent yet, not that nobody knows which work it is — the applicant
        // may come back to this form a dozen times before submitting.
        syncPublicationLinks(request, documentType, jsonData);

        return saved;
    }

    public List<PositionDocument> getDocuments(Long requestId) {
        return documentRepository.findByRequestId(requestId);
    }

    public List<PositionDocument> getDocumentsByType(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocType(requestId, documentType);
    }

    public List<Integer> getCompletedDocTypes(Long requestId) {
        return documentRepository.findCompletedDocTypes(requestId);
    }

    public PositionRequest save(PositionRequest request) {
        return requestRepository.save(request);
    }

    @SuppressWarnings("unchecked")
    private void syncDoc2ToRequest(PositionRequest request, String jsonData) {
        try {
            Map<String, String> data = objectMapper.readValue(jsonData,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            String pos = data.get("request_position");
            if (pos != null && !pos.isBlank()) {
                request.setTargetPosition(pos);
            }
            String major = data.get("major");
            if (major != null && !major.isBlank()) {
                request.setMajor(major);
                    
            }
            String method = data.get("evaluation_method");
            if (method != null && !method.isBlank()) {
                request.setEvaluationMethod(method);
            }
            requestRepository.save(request);
        } catch (Exception e) {
            System.err.println("Sync doc2 to request failed: " + e.getMessage());
        }
    }

    // ================== Publication linkage (GAP-11) ==================

    /**
     * Field names the Scopus picker writes ids into: {@code
     * asst_research_working_scopus_id_2}, and one per row of every publication
     * group on the form.
     */
    private static final Pattern SCOPUS_ID_FIELD = Pattern.compile("^.+_scopus_id_(\\d+)$");

    /**
     * Records which publications this document puts forward.
     *
     * <p>The citation itself stays a free-text field the applicant may reword —
     * that was always the intent. What is new is that the picker also submits the
     * id behind each line, so the request and the publication are tied together
     * and "this work has already been submitted" becomes a question the system
     * can answer (GAP-11/12).
     *
     * <p>Rewritten wholesale on every save rather than merged: the form is the
     * truth about what it currently contains, so a row the applicant deleted must
     * take its link with it. Scoped to one document type so saving another form
     * cannot clear this one's links.
     *
     * <p>Failure here never fails the save. A lost link means the reuse rule is
     * lenient for that row — a worse outcome than saving, but a far better one
     * than an applicant losing a form they spent an hour filling in.
     */
    private void syncPublicationLinks(PositionRequest request, int documentType, String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return;
        }
        try {
            Map<String, String> data = objectMapper.readValue(jsonData,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});

            // publicationId → the row it sits on, first occurrence winning. The
            // same paper twice on one form is one link, not a constraint violation.
            Map<Long, Integer> byPublication = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : data.entrySet()) {
                Matcher field = SCOPUS_ID_FIELD.matcher(entry.getKey());
                if (!field.matches()) {
                    continue;
                }
                String value = entry.getValue();
                if (value == null || value.isBlank()) {
                    continue;
                }
                try {
                    byPublication.putIfAbsent(Long.valueOf(value.trim()),
                            Integer.valueOf(field.group(1)));
                } catch (NumberFormatException e) {
                    // Someone hand-edited the form or a browser extension mangled
                    // it. One unusable row, not a reason to drop the rest.
                    log.warn("Ignoring unreadable Scopus id '{}' on request {} document {}",
                            value, request.getId(), documentType);
                }
            }

            publicationLinkRepository.deleteByRequestIdAndDocumentType(request.getId(), documentType);
            if (byPublication.isEmpty()) {
                return;
            }
            List<PositionRequestPublication> links = byPublication.entrySet().stream()
                    .map(e -> new PositionRequestPublication(request, e.getKey(), documentType, e.getValue()))
                    .toList();
            publicationLinkRepository.saveAll(links);

        } catch (Exception e) {
            log.warn("Could not record the publications used by request {} document {}: {}",
                    request.getId(), documentType, e.toString());
        }
    }

    // ================== Attachments ==================

    public List<com.ecom.academic.model.PositionAttachment> getAttachments(Long requestId) {
        return attachmentRepository.findActiveByRequestId(requestId);
    }

    public long countAttachments(Long requestId) {
        return attachmentRepository.countActiveByRequestId(requestId);
    }

    public long getTotalAttachmentSize(Long requestId) {
        return getAttachments(requestId).stream()
                .mapToLong(att -> att.getFileSize() != null ? att.getFileSize() : 0L)
                .sum();
    }

    public void saveAttachment(com.ecom.academic.model.PositionAttachment attachment) {
        attachmentRepository.save(attachment);
    }

    public Optional<com.ecom.academic.model.PositionAttachment> findAttachmentById(Long id) {
        return attachmentRepository.findById(id);
    }

    public void deleteAttachment(Long id) {
        attachmentRepository.findById(id).ifPresent(att -> {
            deletePhysicalFile(att.getStoredFilePath());
            att.setIsDeleted(true);
            attachmentRepository.save(att);
        });
    }

    private void deletePhysicalFile(String filePath) {
        if (filePath != null && !filePath.isBlank()) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(filePath));
            } catch (Exception e) {
                log.warn("Failed to delete position physical file {}: {}", filePath, e.getMessage());
            }
        }
    }
}
