package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.dto.EvaluationSummary;
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
import com.ecom.search.service.SearchQueryNormalizer;

@Service
public class AcademicRequestService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AcademicRequestService.class);

    private static final Map<Integer, String> DOC_LABELS = new LinkedHashMap<>();
    static {
        DOC_LABELS.put(1, "บันทึกข้อความ ขอรับการประเมินผลการสอน โดยผู้ขอรับการประเมิน");
        DOC_LABELS.put(2, "แบบตรวจสอบเบื้องต้นเอกสารประกอบประเมินผลการสอน");
        DOC_LABELS.put(3, "การขอรายชื่อเพื่อแต่งตั้งคณะกรรมการ");
        DOC_LABELS.put(4, "คำสั่งแต่งตั้งคณะอนุกรรมการประเมินผลการสอน");
        DOC_LABELS.put(5, "บันทึกข้อความ ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ");
        DOC_LABELS.put(6, "ข้อเสนอแนะจากคณะอนุกรรมการ");
        DOC_LABELS.put(7, "แบบฟอร์มประเมินการสอน ตามประกาศ มข.1607-66");
        DOC_LABELS.put(8, "ส่วนที่ 3 แบบประเมินผลการสอน");
        DOC_LABELS.put(9, "บันทึกข้อความ แจ้งผลการประเมินผลการสอน");
    }

    /**
     * เอกสารที่การ<em>ลงนามครบ</em>ทำให้คำร้องเดินไปขั้นถัดไป
     *
     * <p>ต้องตรงกับ {@code switch} ใน {@link #autoUpdateStatusByDocument} เสมอ
     * รายชื่อนี้เคยถูกคัดลอกไว้ใน JavaScript อีกชุดหนึ่งและเพี้ยนกันมาสองรอบตอนทีมเลื่อนเลขเอกสาร
     * จึงย้ายมาไว้ที่เดียว มี {@code StatusAdvancingDocumentsMatchSwitchTest} เฝ้าให้ตรงกัน
     */
    public static final java.util.Set<Integer> STATUS_ADVANCING_DOCUMENTS =
            java.util.Set.of(4, 5, 7, 9);

    /**
     * เอกสารที่ออกเป็นหลายฉบับ ฉบับละกรรมการหนึ่งท่าน
     *
     * <p>อยู่ที่นี่ที่เดียว เพราะตอนทีมเลื่อนเลขเอกสารทั้งชุด (doc0 → doc1) เลขที่ฮาร์ดโค้ด
     * ไว้ตามเทมเพลตไม่ได้เลื่อนตาม ป้าย "3 สำเนา" จึงไปค้างอยู่ที่เอกสารที่ 4 อยู่นาน
     */
    public static final int COMMITTEE_COPIES_DOC_TYPE = 5;

    /** จำนวนฉบับที่ {@link #COMMITTEE_COPIES_DOC_TYPE} ออก — เท่ากับจำนวนอนุกรรมการ */
    public static final int COMMITTEE_COPIES = 3;

    private final AcademicRequestRepository requestRepository;

    private final AcademicDocumentRepository documentRepository;

    private final AcademicAttachmentRepository attachmentRepository;

    private final RequestStatusHistoryRepository historyRepository;

    private final AcademicDocumentEditLogRepository editLogRepository;

    private final AcademicEmailService emailService;
    private final com.ecom.service.AfterCommitRunner afterCommit;

    private final com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository;

    private final org.springframework.context.ApplicationEventPublisher events;

    private final com.ecom.service.UploadPaths uploadPaths;

    public AcademicRequestService(
            AcademicRequestRepository requestRepository,
            AcademicDocumentRepository documentRepository,
            AcademicAttachmentRepository attachmentRepository,
            RequestStatusHistoryRepository historyRepository,
            AcademicDocumentEditLogRepository editLogRepository,
            AcademicEmailService emailService,
            com.ecom.service.AfterCommitRunner afterCommit,
            com.ecom.academic.repository.SignatureRequestRepository signatureRequestRepository,
            org.springframework.context.ApplicationEventPublisher events,
            com.ecom.service.UploadPaths uploadPaths) {
        this.uploadPaths = uploadPaths;
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.editLogRepository = editLogRepository;
        this.emailService = emailService;
        this.afterCommit = afterCommit;
        this.signatureRequestRepository = signatureRequestRepository;
        this.events = events;
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
        // เอกสารที่ส่งกลับให้แก้ต้องลงนามใหม่ครบก่อนเรื่องจะเดินต่อ — ส่วนการตีกลับ
        // (คืนเป็นแบบร่าง / แจ้งผลให้แก้) ยังทำได้เสมอ
        if (oldStatus != newStatus && newStatus != RequestStatus.DRAFT
                && newStatus != RequestStatus.COMPLETED_REVISE
                && newStatus != RequestStatus.REVISION_SUBMITTED) {
            String blocker = resignBlocker(requestId);
            if (blocker != null) {
                throw new IllegalStateException(blocker);
            }
        }
        boolean returnedToApplicant = oldStatus == RequestStatus.RECEIVED && newStatus == RequestStatus.DRAFT;
        if (returnedToApplicant && (note == null || note.isBlank())) {
            // ผู้ยื่นต้องรู้ว่าต้องแก้อะไร การส่งคืนโดยไม่มีเหตุผลเท่ากับให้เดา
            throw new IllegalArgumentException("กรุณาระบุเหตุผลที่ส่งคืนคำร้องให้ผู้ยื่นแก้ไข");
        }
        request.setCurrentStatus(newStatus);
        requestRepository.save(request);

        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        historyRepository.save(history);

        if (returnedToApplicant) {
            // Flow ข้อ 2 → ข้อ 1: เอกสารจะถูกแก้ ลายเซ็นที่ให้ไว้กับเนื้อหาเดิมจึงใช้ไม่ได้อีก
            events.publishEvent(new AcademicRequestReturnedToDraft(requestId, changedBy, note));
        }

        if (sendNotification) {
            // Announced only once the new status is actually committed, and by
            // id so the background thread reads its own copy of the row.
            Long notifyId = request.getId();
            afterCommit.run(() -> emailService.sendStatusChangeEmail(notifyId, oldStatus, newStatus, note));
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
        AcademicDocument savedDoc = documentRepository.save(doc);

        if (documentType == 9 && jsonData != null && !jsonData.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                @SuppressWarnings("unchecked")
                Map<String, String> data = mapper.readValue(jsonData, Map.class);
                String statedExpiry = data.get("expiration_date");
                LocalDateTime expiry = parseThaiDate(statedExpiry);
                if (expiry == null) {
                    String evalDate = firstNonBlank(data.get("evaluation_date"), data.get("faculty_board_meeting_date"));
                    LocalDateTime parsedEvalDate = parseThaiDate(evalDate);
                    if (parsedEvalDate != null) {
                        expiry = parsedEvalDate.plusYears(3);
                    }
                }
                if (expiry != null) {
                    request.setEvaluationExpiryDate(expiry);
                    requestRepository.save(request);
                }
            } catch (Exception e) {
                log.warn("Could not parse expiry date from doc 9 jsonData: {}", e.getMessage());
            }
        }

        return savedDoc;
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
        return documentRepository.findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType);
    }

    /**
     * ข้อมูลล่าสุดที่บันทึกไว้ของเอกสารฉบับนี้ หรือ null เมื่อยังไม่เคยบันทึก
     *
     * <p>{@link DocumentFieldOwnership} ใช้ค่านี้คืนช่องที่ผู้บันทึกไม่ได้เป็นเจ้าของกลับไป
     */
    public Map<String, String> getLatestDocumentData(Long requestId, int documentType) {
        List<AcademicDocument> docs = getDocumentsByType(requestId, documentType);
        if (docs.isEmpty()) {
            return null;
        }
        String json = docs.get(docs.size() - 1).getJsonData();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ลงเลขที่หนังสือและวันที่เอกสารให้ทุกสำเนาของเอกสารฉบับนั้น
     *
     * <p>เอกสารที่ 5 ถูกบันทึกเป็นสามแถว หนึ่งแถวต่อกรรมการหนึ่งท่าน แต่ทั้งสามฉบับเป็น
     * หนังสือฉบับเดียวกัน ใช้เลขที่และวันที่ร่วมกัน เจ้าหน้าที่จึงกรอกครั้งเดียวแล้วต้องลง
     * ครบทุกแถว — ถ้าเขียนแค่แถวเดียว อีกสองแถวจะค้างเลขเก่าไว้ แล้วคำตอบจะต่างกัน
     * ไปตามว่าใครหยิบแถวไหนไปใช้ ({@link #getLatestDocumentData} หยิบแถวท้าย ส่วนฟอร์ม
     * ของเจ้าหน้าที่แสดงแถวแรก)
     *
     * <p>ช่องอื่นของแต่ละสำเนา เช่นชื่อกรรมการ ไม่ถูกแตะ เพราะ
     * {@link DocumentFieldOwnership#mergeOfficeFields} รับเฉพาะช่องสารบรรณเท่านั้น
     *
     * @param submitted ค่าที่ส่งมาจากฟอร์ม (จะถูกกรองเหลือเฉพาะช่องสารบรรณ)
     * @return จำนวนแถวที่เขียนจริง
     */
    @Transactional
    public int saveOfficeFieldsAcrossCopies(AcademicRequest request, int documentType,
            Map<String, String> submitted, String label) {
        return saveOfficeFieldsAcrossCopies(request, documentType, submitted, label, false);
    }

    /**
     * เหมือนข้างบน แต่เมื่อ {@code includeAdminFields} รับช่องของแอดมินทั้งหมดด้วย
     * ({@link DocumentFieldOwnership#lateFields}) — ใช้ตอนผู้ยื่นลงนามแล้วแต่ยังไม่ได้ส่งต่อ
     * ให้ผู้ลงนามคนถัดไป ดู {@link SignatureWorkflowService#awaitsMoreSigners}
     */
    @Transactional
    public int saveOfficeFieldsAcrossCopies(AcademicRequest request, int documentType,
            Map<String, String> submitted, String label, boolean includeAdminFields) {
        List<AcademicDocument> docs = getDocumentsByType(request.getId(), documentType);
        if (docs.isEmpty()) {
            // ยังไม่มีแถวเลย — เปิดแถวร่างให้ เพื่อไม่ให้เลขที่กรอกไว้หายไปเฉย ๆ
            Map<String, String> merged = DocumentFieldOwnership.mergeOfficeFields(
                    com.ecom.academic.model.SignatureModule.ACADEMIC, documentType, submitted, null, includeAdminFields);
            saveDraft(request, documentType, writeJson(merged), label, null);
            return 1;
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        int written = 0;
        for (AcademicDocument doc : docs) {
            Map<String, String> existing = null;
            String json = doc.getJsonData();
            if (json != null && !json.isBlank()) {
                try {
                    existing = mapper.readValue(json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                } catch (Exception e) {
                    // แถวที่อ่านไม่ออกก็ยังลงเลขให้ได้ ดีกว่าปล่อยให้ทั้งชุดล้มเพราะแถวเดียว
                    log.warn("Document {} copy {} holds unreadable JSON; writing office fields onto a fresh map",
                            doc.getId(), doc.getCopyNumber());
                }
            }
            Map<String, String> merged = DocumentFieldOwnership.mergeOfficeFields(
                    com.ecom.academic.model.SignatureModule.ACADEMIC, documentType, submitted, existing, includeAdminFields);
            doc.setJsonData(writeJson(merged));
            // ไฟล์ที่สร้างไว้จากข้อมูลชุดก่อนไม่มีค่าที่เพิ่งกรอก — ทิ้งไป ทางสำรองจะได้สร้างใหม่
            doc.setGeneratedFilePath(null);
            documentRepository.save(doc);
            written++;
        }
        return written;
    }

    private String writeJson(Map<String, String> data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("แปลงข้อมูลเอกสารเป็น JSON ไม่ได้", e);
        }
    }

    // ================== ประตูแก้ไขเอกสารของผู้ยื่น ==================

    /**
     * สถานะที่ปิดกระบวนการไปแล้ว — ไม่เปิดให้แก้เอกสารอีกไม่ว่ากรณีใด
     */
    private static final java.util.EnumSet<RequestStatus> CLOSED_STATUSES = java.util.EnumSet.of(
            RequestStatus.COMPLETED, RequestStatus.COMPLETED_PASS, RequestStatus.COMPLETED_REVISE,
            RequestStatus.COMPLETED_FAIL);

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
        // ก่อนดูว่าเป็นแบบร่าง — ผู้ยื่นลงนามเอกสารที่ 1-2 ได้ตั้งแต่ยังไม่ส่งคำร้อง ถ้าเช็กทีหลัง
        // เอกสารที่ลงนามแล้วถูกเขียนทับได้ (บันทึกร่างอัตโนมัติที่ยิงช้า หรือ POST ตรง)
        if (isDocumentLockedForSigning(request.getId(), documentType)) {
            return false;
        }
        if (request.getCurrentStatus() == RequestStatus.DRAFT) {
            return true;
        }
        if (CLOSED_STATUSES.contains(request.getCurrentStatus())) {
            return false;
        }
        return isRevisionRequested(request.getId(), documentType);
    }

    /** เอกสารกำลังเวียนลงนาม หรือลงนามครบแล้ว */
    public boolean isDocumentLockedForSigning(Long requestId, int documentType) {
        var blocking = signatureRequestRepository.findBlockingEnvelopes(
                com.ecom.academic.model.SignatureModule.ACADEMIC, requestId, documentType);
        return !blocking.isEmpty();
    }

    /**
     * ลงนามครบแล้ว รอบเวียนลงนามปิดไปแล้ว
     *
     * <p>แคบกว่า {@link #isDocumentLockedForSigning} หนึ่งขั้น: ระหว่างเวียนลงนามห้ามขยับ
     * อะไรทั้งสิ้นเพราะคนถัดไปต้องเห็นของเดิม แต่เมื่อปิดรอบแล้วไม่มีคนถัดไปให้เข้าใจผิด
     * จึงเหลือช่องให้สารบรรณลงเลขที่หนังสือและวันที่ซึ่งออกให้หลังเอกสารเสร็จ
     */
    public boolean isSigningComplete(Long requestId, int documentType) {
        return signatureRequestRepository.findBlockingEnvelopes(
                com.ecom.academic.model.SignatureModule.ACADEMIC, requestId, documentType)
                .stream()
                .anyMatch(envelope -> envelope
                        .getStatus() == com.ecom.academic.model.SignatureRequestStatus.COMPLETED);
    }

    /**
     * เอกสารที่เจ้าหน้าที่ส่งกลับให้ผู้ยื่นแก้ และผู้ยื่นยังแก้/ลงนามใหม่ไม่เสร็จ → เหตุผล ("" ถ้าไม่ได้ระบุ)
     *
     * <p>สถานะคำร้องไม่เปลี่ยนตอนส่งกลับ (ยังเป็น "รับคำร้อง" ฯลฯ) ผู้ยื่นจึงรู้ได้จากรายการนี้เท่านั้น
     * ใช้กฎเดียวกับประตูแก้ไขเอกสาร: ลงนามใหม่แล้วเอกสารถูกล็อก จึงหลุดจากรายการเอง
     */
    public java.util.Map<Integer, String> sentBackDocuments(AcademicRequest request) {
        java.util.Map<Integer, String> sentBack = new java.util.LinkedHashMap<>();
        if (request == null || request.getCurrentStatus() == null || request.getCurrentStatus() == RequestStatus.DRAFT) {
            return sentBack;
        }
        for (int docType : DocumentFieldOwnership.applicantDocuments(com.ecom.academic.model.SignatureModule.ACADEMIC)) {
            if (isRevisionRequested(request.getId(), docType) && canApplicantEditDocument(request, docType)) {
                String note = getRevisionNote(request.getId(), docType);
                sentBack.put(docType, note != null ? note : "");
            }
        }
        return sentBack;
    }

    /**
     * เอกสารที่ส่งกลับให้ผู้ยื่นแก้ ยังไม่จบ พร้อมขั้นที่อยู่ ({@link RevisionProgress})
     *
     * <p>ต่างจาก {@link #sentBackDocuments} ตรงที่ไม่หายไปตอนเปิดซองลงนาม — ผู้ยื่นที่ยังไม่ได้ลงนาม
     * ยังเห็น "รอท่านลงนาม" และหลังลงนามเห็นว่ายื่นแล้ว รอตรวจ จนกว่าทุกคนจะลงนามใหม่ครบ
     */
    /**
     * ขั้นการแก้ไขของหลายคำร้องพร้อมกัน (แดชบอร์ด) — คืนเฉพาะคำร้องที่มีเอกสารค้างอยู่
     *
     * <p>ถามครั้งเดียวว่าคำร้องไหนเคยถูกส่งกลับ แล้วคำนวณเต็มเฉพาะคำร้องเหล่านั้น ซึ่งมีไม่กี่รายการ
     * ไม่ใช่ไล่คำนวณทุกคำร้องในประวัติของผู้ยื่น (N+1)
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, RevisionProgress.Summary> revisionProgressFor(List<AcademicRequest> requests) {
        java.util.Map<Long, RevisionProgress.Summary> result = new java.util.HashMap<>();
        if (requests == null || requests.isEmpty()) {
            return result;
        }
        java.util.Set<Long> sentBack = new java.util.HashSet<>(documentRepository.findRequestIdsWithRevisionRequested(
                requests.stream().map(AcademicRequest::getId).toList()));
        for (AcademicRequest request : requests) {
            if (sentBack.contains(request.getId())) {
                RevisionProgress.Summary summary = revisionProgress(request);
                if (!summary.isEmpty()) {
                    result.put(request.getId(), summary);
                }
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public RevisionProgress.Summary revisionProgress(AcademicRequest request) {
        java.util.Map<Integer, RevisionProgress.SentBackDocument> result = new java.util.TreeMap<>();
        if (request == null || request.getCurrentStatus() == null || request.getCurrentStatus() == RequestStatus.DRAFT
                || CLOSED_STATUSES.contains(request.getCurrentStatus())) {
            return new RevisionProgress.Summary(result);
        }
        var module = com.ecom.academic.model.SignatureModule.ACADEMIC;
        java.util.Map<Integer, LocalDateTime> requestedAt = new java.util.HashMap<>();
        java.util.Map<Integer, LocalDateTime> submittedAt = new java.util.HashMap<>();
        java.util.Map<Integer, String> notes = new java.util.HashMap<>();
        for (var doc : documentRepository.findByRequestId(request.getId())) {
            int type = doc.getDocumentType();
            if (doc.getRevisionRequestedAt() == null || !DocumentFieldOwnership.isApplicantDocument(module, type)) {
                continue;
            }
            LocalDateTime seen = requestedAt.get(type);
            if (seen == null || doc.getRevisionRequestedAt().isAfter(seen)) {
                requestedAt.put(type, doc.getRevisionRequestedAt());
                submittedAt.put(type, null);
                notes.put(type, doc.getRevisionNote() != null ? doc.getRevisionNote() : "");
            }
        }
        if (requestedAt.isEmpty()) {
            return new RevisionProgress.Summary(result);
        }
        List<Integer> awaiting = documentsAwaitingResign(request.getId());
        requestedAt.forEach((type, at) -> {
            boolean hasApplicantSlot = SignatureAnchorRegistry.slotsOf(module, type).stream()
                    .anyMatch(slot -> "applicant".equals(slot.slotKey()));
            var envelopes = signatureRequestRepository
                    .findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(module, request.getId(), type);
            RevisionProgress.Stage stage = RevisionProgress.stageOf(hasApplicantSlot, at, submittedAt.get(type),
                    envelopes, isDocumentLockedForSigning(request.getId(), type), awaiting.contains(type));
            if (stage != null) {
                result.put(type, new RevisionProgress.SentBackDocument(type, stage, notes.get(type)));
            }
        });
        return new RevisionProgress.Summary(result);
    }

    /** เวลาส่งกลับรอบล่าสุดของเอกสารฉบับนี้ หรือ null ถ้าไม่เคยถูกส่งกลับ */
    public LocalDateTime latestRevisionRequestedAt(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType)
                .stream()
                .map(AcademicDocument::getRevisionRequestedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /** แอดมินส่งเอกสารฉบับนี้กลับมาให้ผู้ยื่นแก้ไขแล้วหรือยัง */
    public boolean isRevisionRequested(Long requestId, int documentType) {
        var docs = documentRepository.findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType);
        return docs.stream()
                .anyMatch(AcademicDocument::isRevisionRequested);
    }

    /**
     * เอกสารที่เจ้าหน้าที่ส่งกลับให้แก้ แล้วยังลงนามใหม่ไม่ครบ
     *
     * <p>ส่งกลับ = ซองลายเซ็นเดิมถูกยกเลิก ลายเซ็นของ<em>ทุกคน</em>ในซองนั้นใช้ไม่ได้อีก ไม่ใช่แค่ของผู้ยื่น
     * เอกสารจะนับว่าลงนามใหม่แล้วก็ต่อเมื่อทุกช่องที่เคยลงนามไว้ (และช่องของผู้ยื่นเสมอ) ลงนามอีกครั้ง
     * ในซองที่เปิดหลังการส่งกลับ — ผู้ยื่นเซ็นแล้วแต่คณบดียังไม่เซ็นซ้ำ ยังไม่นับ
     *
     * <p>คำนวณจากเวลา ไม่ได้ล้างธงส่งกลับทิ้ง เพราะธงเดียวกันยังใช้บอกเหตุผลที่ส่งกลับอยู่
     *
     * @return เลขเอกสาร เรียงจากน้อยไปมาก — ว่างเมื่อไม่มีอะไรค้าง
     */
    @Transactional(readOnly = true)
    public List<Integer> documentsAwaitingResign(Long requestId) {
        var module = com.ecom.academic.model.SignatureModule.ACADEMIC;
        java.util.Map<Integer, LocalDateTime> sentBack = new java.util.TreeMap<>();
        documentRepository.findByRequestId(requestId).forEach(doc -> {
            if (doc.getRevisionRequestedAt() != null) {
                sentBack.merge(doc.getDocumentType(), doc.getRevisionRequestedAt(),
                        (x, y) -> x.isAfter(y) ? x : y);
            }
        });
        List<Integer> pending = new java.util.ArrayList<>();
        sentBack.forEach((type, at) -> {
            var slots = SignatureAnchorRegistry.slotsOf(module, type);
            if (slots.isEmpty()) {
                return; // ไม่มีช่องลงนาม แก้แล้วบันทึกก็จบ
            }
            var envelopes = signatureRequestRepository
                    .findByModuleAndRequestIdAndDocumentTypeOrderByCreatedAtDesc(module, requestId, type);

            java.util.Set<String> required = new java.util.HashSet<>();
            if (slots.stream().anyMatch(slot -> "applicant".equals(slot.slotKey()))) {
                required.add("applicant");
            }
            java.util.Set<String> signedAgain = new java.util.HashSet<>();
            for (var envelope : envelopes) {
                boolean before = envelope.getCreatedAt() != null && !envelope.getCreatedAt().isAfter(at);
                var status = envelope.getStatus();
                for (var step : envelope.getSteps()) {
                    if (step.getStatus() != com.ecom.academic.model.SignatureStepStatus.SIGNED) {
                        continue;
                    }
                    if (before) {
                        required.add(step.getSlotKey());
                    } else if (status == com.ecom.academic.model.SignatureRequestStatus.COMPLETED
                            || status == com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS) {
                        signedAgain.add(step.getSlotKey());
                    }
                }
            }
            if (!signedAgain.containsAll(required)) {
                pending.add(type);
            }
        });
        return pending;
    }

    /** ข้อความบอกเจ้าหน้าที่ว่าติดเอกสารฉบับไหน — null เมื่อเดินต่อได้ */
    private String resignBlocker(Long requestId) {
        List<Integer> pending = documentsAwaitingResign(requestId);
        if (pending.isEmpty()) {
            return null;
        }
        return "ยังเปลี่ยนสถานะไม่ได้ — เอกสารที่ส่งกลับให้แก้ไขยังลงนามใหม่ไม่ครบ: "
                + pending.stream().map(t -> "เอกสารที่ " + t + " (" + getDocLabel(t) + ")")
                        .reduce((a, b) -> a + ", " + b).orElse("")
                + " — ผู้ยื่นต้องแก้และลงนามใหม่ และผู้ลงนามคนอื่นในเอกสารนั้นต้องลงนามใหม่ครบก่อน";
    }

    /** เหตุผลที่แอดมินส่งเอกสารฉบับนี้กลับมาให้แก้ไข (ถ้ามี) */
    public String getRevisionNote(Long requestId, int documentType) {
        var docs = documentRepository.findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType);
        return docs.stream()
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
        List<AcademicDocument> docs = documentRepository.findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType);
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

    /**
     * เหตุผลที่คำร้องถูกส่งคืนให้ผู้ยื่นแก้ไข (Flow ข้อ 2) หรือ null ถ้าคำร้องไม่ได้อยู่ในสภาพถูกส่งคืน
     *
     * <p>อ่านจากประวัติสถานะล่าสุดที่ {@code RECEIVED → DRAFT} เฉพาะตอนคำร้องยังเป็นแบบร่างอยู่
     * พอผู้ยื่นยื่นใหม่แล้ว เหตุผลเก่าก็ไม่ต้องแสดงอีก
     */
    public String getReturnNote(Long requestId) {
        AcademicRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getCurrentStatus() != RequestStatus.DRAFT) {
            return null;
        }
        return historyRepository.findByRequestIdOrderByChangedAtDesc(requestId).stream()
                .filter(h -> h.getOldStatus() == RequestStatus.RECEIVED && h.getNewStatus() == RequestStatus.DRAFT)
                .map(RequestStatusHistory::getNote)
                .findFirst()
                .orElse(null);
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
        if (action == AcademicDocumentEditLog.EditAction.DRAFT_SAVED) {
            // Autosave fires on every pause in typing; one row per burst is enough.
            AcademicDocumentEditLog last = editLogRepository
                    .findFirstByRequestAndDocumentTypeOrderByEditedAtDescIdDesc(request, documentType)
                    .orElse(null);
            if (last != null && last.getAction() == action
                    && isSameUser(last.getEditedBy(), user)
                    && last.getEditedAt() != null
                    && last.getEditedAt().isAfter(LocalDateTime.now().minus(DRAFT_LOG_MERGE_WINDOW))) {
                last.setEditedAt(LocalDateTime.now());
                editLogRepository.save(last);
                return;
            }
        }
        AcademicDocumentEditLog logEntry = new AcademicDocumentEditLog();
        logEntry.setRequest(request);
        logEntry.setDocumentType(documentType);
        logEntry.setDocumentLabel(label != null ? label : getDocLabel(documentType));
        logEntry.setEditedBy(user);
        logEntry.setAction(action);
        editLogRepository.save(logEntry);
    }

    /** Edit-log document type for files that belong to the request rather than a numbered document. */
    public static final int REQUEST_FILES_DOC_TYPE = 0;

    /** Logs a change that isn't a form save (attachment, upload, re-sign…), with what it touched. */
    public void logDocumentChange(AcademicRequest request, int documentType, String detail,
            UserDtls user, AcademicDocumentEditLog.EditAction action) {
        String label = documentType == REQUEST_FILES_DOC_TYPE ? null : getDocLabel(documentType);
        if (detail != null && !detail.isBlank()) {
            label = (label != null ? label + " — " : "") + detail.strip();
        }
        if (label == null) {
            label = "ไฟล์ของคำร้อง";
        } else if (label.length() > 255) {
            label = label.substring(0, 252) + "...";
        }
        logDocumentEdit(request, documentType, label, user, action);
    }

    /**
     * ผู้ยื่นลงนามในเอกสารที่ถูกส่งกลับ = ยื่นการแก้ไข — บันทึกลงประวัติการแก้ไข
     *
     * <p>ถูกเรียกหลัง commit ของการลงนาม ต้องเปิด transaction ใหม่ ไม่งั้นจะเข้าร่วม transaction
     * ที่ commit ไปแล้วและแถวนี้ไม่ถูกบันทึกเลย
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logRevisionSubmitted(Long requestId, int documentType, UserDtls applicant) {
        requestRepository.findById(requestId).ifPresent(request -> logDocumentEdit(request, documentType, null,
                applicant, AcademicDocumentEditLog.EditAction.REVISION_SUBMITTED));
    }

    /** Autosaves by the same user on the same document within this window share one history row. */
    static final java.time.Duration DRAFT_LOG_MERGE_WINDOW = java.time.Duration.ofMinutes(10);

    private static boolean isSameUser(UserDtls a, UserDtls b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
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
     * this went wrong before: the list named COMPLETED and a since-removed refusal status but not
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
                    java.nio.file.Path requestDir = uploadPaths.dir("academic", String.valueOf(requestId));
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

    /**
     * ตรวจสอบว่าคำร้องฉบับร่างเป็นแบบร่างว่างเปล่าหรือไม่ (ยังไม่มีการกรอกข้อมูลและยังไม่มีไฟล์แนบ)
     * หากเป็นคำร้องที่ถูกส่งกลับมาแก้ไข (มี returnNote) จะไม่ถือว่าเป็นร่างว่างเปล่าเด็ดขาด
     */
    public boolean isDraftEmpty(AcademicRequest request) {
        if (request == null || request.getCurrentStatus() != RequestStatus.DRAFT) {
            return false;
        }
        // 1. คำร้องที่ถูกส่งกลับมาแก้ไข (returnNote) ต้องไม่ถูกมองว่าว่างเปล่า
        if (getReturnNote(request.getId()) != null) {
            return false;
        }
        // 2. มีไฟล์แนบหรือไม่
        if (attachmentRepository.countByRequestId(request.getId()) > 0) {
            return false;
        }
        // 3. ตรวจสอบเอกสาร (AcademicDocument)
        List<AcademicDocument> docs = getDocuments(request.getId());
        for (AcademicDocument doc : docs) {
            if (doc.getGeneratedFilePath() != null && !doc.getGeneratedFilePath().isBlank()) {
                return false;
            }
            if (hasNonEmptyDocumentData(doc.getJsonData())) {
                return false;
            }
        }
        // 4. ตรวจสอบว่ามีการลงนามหรือขอลงนามหรือไม่
        List<com.ecom.academic.model.SignatureRequest> envelopes = signatureRequestRepository
                .findByModuleAndRequestIdOrderByDocumentTypeAsc(com.ecom.academic.model.SignatureModule.ACADEMIC, request.getId());
        if (!envelopes.isEmpty()) {
            return false;
        }

        return true;
    }

    private boolean hasNonEmptyDocumentData(String jsonData) {
        if (jsonData == null || jsonData.isBlank() || jsonData.trim().equals("{}")) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = mapper.readValue(jsonData, Map.class);
            if (map == null || map.isEmpty()) {
                return false;
            }
            for (Object value : map.values()) {
                if (value != null && !value.toString().trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
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
            if (!"LINK".equalsIgnoreCase(att.getFileType())) {
                deletePhysicalFile(att.getStoredFilePath());
            }
            attachmentRepository.delete(att);
        });
    }

    private void deletePhysicalFile(String filePath) {
        if (filePath != null && !filePath.isBlank()) {
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                return;
            }
            try {
                java.nio.file.Path path = uploadPaths.resolve(filePath);
                if (path != null) {
                    java.nio.file.Files.deleteIfExists(path);
                }
            } catch (Exception e) {
                log.warn("Failed to delete physical file {}: {}", filePath, e.getMessage());
            }
        }
    }

    // ==================== Slot-Based Attachment Methods ====================

    private static final long MAX_SLOT_BYTES = 75L * 1024L * 1024L; // 75 MB per slot

    public List<AcademicAttachment> getAttachmentsBySlot(Long requestId, int slot) {
        return attachmentRepository.findByRequestIdAndChecklistItemAndIsDeletedFalseOrderByUploadedAtDesc(requestId, slot);
    }

    public java.util.Map<Integer, List<AcademicAttachment>> getAttachmentsGroupedBySlot(Long requestId) {
        List<AcademicAttachment> all = attachmentRepository.findByRequestIdAndIsDeletedFalseOrderByChecklistItemAscUploadedAtDesc(requestId);
        java.util.Map<Integer, List<AcademicAttachment>> map = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            map.put(i, new java.util.ArrayList<>());
        }
        for (AcademicAttachment att : all) {
            Integer slot = att.getChecklistItem();
            if (slot != null && slot >= 1 && slot <= 5) {
                map.get(slot).add(att);
            }
        }
        return map;
    }

    public long getSlotTotalSize(Long requestId, int slot) {
        return getAttachmentsBySlot(requestId, slot).stream()
                .mapToLong(att -> att.getFileSize() != null ? att.getFileSize() : 0L)
                .sum();
    }

    public void validateSlotQuota(Long requestId, int slot, long incomingBytes) {
        long currentSize = getSlotTotalSize(requestId, slot);
        if (currentSize + incomingBytes > MAX_SLOT_BYTES) {
            String usedMB = String.format("%.1f", currentSize / (1024.0 * 1024.0));
            String incomingMB = String.format("%.1f", incomingBytes / (1024.0 * 1024.0));
            throw new IllegalStateException(
                    "ช่องที่ " + slot + " มีขนาดรวม " + usedMB + " MB แล้ว "
                    + "ไม่สามารถอัปโหลดไฟล์ขนาด " + incomingMB + " MB เพิ่มได้ "
                    + "(จำกัด 75 MB ต่อช่อง)");
        }
    }

    public long countActiveAttachments(Long requestId) {
        return attachmentRepository.findByRequestIdAndIsDeletedFalseOrderByChecklistItemAscUploadedAtDesc(requestId).size();
    }

    /**
     * อัพเดตสถานะอัตโนมัติตามเอกสารที่ลงนามครบแล้ว
     * - Doc 4 ลงนามครบ → SUB_COMMITTEE_APPOINTED
     * - Doc 5 ลงนามครบ → MEETING_SCHEDULED
     * - Doc 7 ลงนามครบ → COMPLETED_PASS หรือ COMPLETED_FAIL (ตามผลคะแนน)
     * - Doc 9 ลงนามครบ → COMPLETED
     *
     * <p>ผู้เรียกจริงคือ {@link SignedDocumentStatusAdvancer} ตอนซองลายเซ็นปิด ไม่ใช่ตอน
     * เจ้าหน้าที่กดบันทึกเอกสารอีกต่อไป — การบันทึกคือการร่างเสร็จ ไม่ใช่ขั้นตอนเสร็จ
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
            case 4 -> {
                if (request.getCurrentStatus().canMoveTo(RequestStatus.SUB_COMMITTEE_APPOINTED)
                        && namesThreeSubCommitteeMembers(jsonData)) {
                    updateStatus(requestId, RequestStatus.SUB_COMMITTEE_APPOINTED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารคำสั่งแต่งตั้งอนุกรรมการ", sendNotify);
                }
            }
            case 6 -> {
                // Doc 6 auto-status is handled separately via /send-suggestion endpoint
            }
            case 5 -> {
                if (request.getCurrentStatus().canMoveTo(RequestStatus.MEETING_SCHEDULED)) {
                    updateStatus(requestId, RequestStatus.MEETING_SCHEDULED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ", sendNotify);
                }
            }
            case 7 -> {
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
            case 9 -> {
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
     *
     * <p>คำค้นสั้นกว่า {@link SearchQueryNormalizer#MIN_QUERY_LENGTH} ตัวอักษร
     * คืนลิสต์ว่าง ไม่ใช่ทุกแถว — อักษรไทยตัวเดียวเป็นสับสตริงของข้อมูลแทบทั้งหมด
     */
    public List<AcademicRequest> searchByApplicantName(String name) {
        String pattern = SearchQueryNormalizer.likePattern(name);
        if (pattern == null) {
            return List.of();
        }
        return requestRepository.searchByNameOrEmail(pattern);
    }

    /**
     * ดึงเอกสารเรียงตามลำดับ documentType
     */
    public List<AcademicDocument> getDocumentsSorted(Long requestId) {
        return documentRepository.findByRequestIdOrderByDocumentTypeAscCopyNumberAsc(requestId);
    }

    /**
     * ค้นหาวันหมดอายุผลประเมินล่าสุดของผู้ใช้
     * คำนวณ: วันอนุมัติใน Doc 9 + 5 ปี
     */
    public LocalDateTime getLatestEvaluationExpiry(Integer applicantId) {
        // Find completed requests (COMPLETED_PASS or COMPLETED)
        List<AcademicRequest> requests = requestRepository
                .findByApplicantIdOrderByCreatedAtDesc(applicantId);

        for (AcademicRequest req : requests) {
            if (req.getCurrentStatus().carriesAPassedResult()) {

                // Try to extract from Doc 9 JSON first
                try {
                    Map<String, String> doc9 = documentData(req.getId(), 9);
                    if (!doc9.isEmpty()) {
                        String statedExpiry = doc9.get("expiration_date");
                        LocalDateTime expiry = parseThaiDate(statedExpiry);
                        if (expiry != null) {
                            if (!expiry.equals(req.getEvaluationExpiryDate())) {
                                req.setEvaluationExpiryDate(expiry);
                                requestRepository.save(req);
                            }
                            return expiry;
                        }

                        String dateStr = firstNonBlank(doc9.get("evaluation_date"),
                                doc9.get("faculty_board_meeting_date"));
                        if (dateStr != null && !dateStr.isEmpty()) {
                            LocalDateTime approvalDate = parseThaiDate(dateStr);
                            if (approvalDate != null) {
                                LocalDateTime computedExpiry = approvalDate.plusYears(3);
                                if (!computedExpiry.equals(req.getEvaluationExpiryDate())) {
                                    req.setEvaluationExpiryDate(computedExpiry);
                                    requestRepository.save(req);
                                }
                                return computedExpiry;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Fall through
                }

                // If expiry is already computed and no Doc 9 override found, return it
                if (req.getEvaluationExpiryDate() != null) {
                    return req.getEvaluationExpiryDate();
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
     * month name into most documents, while the expiry field on document 9 has
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
            String normalized = com.ecom.util.ThaiDateUtil.toArabicDigits(thaiDate).trim();

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
     * Formats a LocalDateTime into standard Thai date string (e.g. "17 กุมภาพันธ์ 2572").
     */
    public static String formatThaiDate(LocalDateTime dt) {
        if (dt == null) return null;
        int day = dt.getDayOfMonth();
        int monthIdx = dt.getMonthValue() - 1;
        int year = dt.getYear() + 543;
        if (monthIdx >= 0 && monthIdx < THAI_MONTHS.length) {
            return day + " " + THAI_MONTHS[monthIdx] + " " + year;
        }
        return day + "/" + dt.getMonthValue() + "/" + year;
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

    /**
     * Flattens one evaluation into the facts the position flow needs — the
     * course, the year, the result and when it lapses.
     *
     * <p>Reads document 9, the notification of the result, and falls back to
     * document 1 for anything it does not carry. The split is not arbitrary:
     * document 9 is the authoritative record of the *result*, but it has no
     * academic-year field at all — only {@code semester}, written "1/2568" — while
     * document 1 is where the applicant states the year outright, and states it as
     * a required field. So the course code comes from document 9 where present
     * and document 1 otherwise, and the year comes from document 1 first, then
     * from the semester's second half, then from the year the evaluation was
     * carried out.
     *
     * @return a summary, never null; its fields are null where the documents are
     *         silent, and callers must expect that of an evaluation that has not
     *         reached a result yet
     */
    public EvaluationSummary summarize(AcademicRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, String> doc9 = documentData(request.getId(), 9);
        Map<String, String> doc1 = documentData(request.getId(), 1);

        String rawDoc1Year = doc1.get("academic_year");
        String rawSemester = firstNonBlank(doc9.get("semester"), doc1.get("semester"), rawDoc1Year);
        String semester = firstNonBlank(semesterOf(rawSemester), rawSemester);
        String evaluationDate = firstNonBlank(doc9.get("evaluation_date"),
                doc9.get("faculty_board_meeting_date"));

        LocalDateTime expiryAt = resolveExpiry(request, doc9, evaluationDate);
        Long daysLeft = expiryAt == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiryAt);

        return new EvaluationSummary(
                request.getId(),
                request.getRequestCode(),
                firstNonBlank(doc9.get("course_code"), doc1.get("course_code")),
                firstNonBlank(doc9.get("course_name"), doc1.get("course_name")),
                firstNonBlank(yearOf(rawDoc1Year), rawDoc1Year, yearOf(semester),
                        yearOf(evaluationDate)),
                semester,
                firstNonBlank(doc9.get("result_level"), doc9.get("eval_result_level"),
                        doc9.get("evaluation_result")),
                evaluationDate,
                firstNonBlank(doc9.get("expiration_date"), formatThaiDate(expiryAt)),
                expiryAt,
                daysLeft,
                doc1.get("current_position"),
                com.ecom.academic.model.AcademicRank.fromDoc1Checks(doc1));
    }

    /**
     * When this result lapses: the date on document 9 if it names one, otherwise
     * three years from the evaluation, otherwise the stored expiry, otherwise
     * three years from the submission — the same ladder
     * {@link #getLatestEvaluationExpiry} walks, so the countdown on the dashboard
     * and the one beside each choice cannot disagree.
     */
    private LocalDateTime resolveExpiry(AcademicRequest request, Map<String, String> doc9,
            String evaluationDate) {
        LocalDateTime stated = parseThaiDate(doc9.get("expiration_date"));
        if (stated != null) {
            return stated;
        }
        LocalDateTime evaluated = parseThaiDate(evaluationDate);
        if (evaluated != null) {
            return evaluated.plusYears(3);
        }
        if (request.getEvaluationExpiryDate() != null) {
            return request.getEvaluationExpiryDate();
        }
        return request.getSubmissionDate() == null ? null
                : request.getSubmissionDate().plusYears(3);
    }

    /** The stored form data of one document, or an empty map when there is none. */
    private Map<String, String> documentData(Long requestId, int documentType) {
        List<AcademicDocument> docs = documentRepository
                .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, documentType);
        for (AcademicDocument doc : docs) {
            if (doc.getJsonData() == null || doc.getJsonData().isBlank()) {
                continue;
            }
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        doc.getJsonData(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                        });
            } catch (Exception e) {
                log.warn("Could not read document {} of evaluation {}: {}", documentType,
                        requestId, e.getMessage());
            }
        }
        return Map.of();
    }

    /**
     * Extracts semester/term from a string like "1/2569", returning "1/2569",
     * ensuring downstream teaching evaluation forms get the full semester/year format.
     */
    private static String semesterOf(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String arabic = com.ecom.util.ThaiDateUtil.toArabicDigits(text.trim());
        while (arabic.matches("^\\d+/\\d+/\\d+$")) {
            arabic = arabic.substring(arabic.indexOf('/') + 1);
        }
        return arabic.contains("/") ? arabic : null;
    }

    /**
     * The Buddhist year inside a semester ("1/2568") or a date ("17 กุมภาพันธ์
     * 2569") — the last run of four digits, which is where the year sits in every
     * form these fields take.
     */
    private static String yearOf(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{4})(?!.*\\d{4})")
                .matcher(com.ecom.util.ThaiDateUtil.toArabicDigits(text));
        return m.find() ? m.group(1) : null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Why one submitted evaluation cannot back a position request, in words for
     * the applicant — null when it can. Follows {@link #isUsableEvaluation} check
     * for check, so the two never disagree.
     */
    public String unusableReason(AcademicRequest request) {
        if (isUsableEvaluation(request)) {
            return null;
        }
        RequestStatus status = request == null ? null : request.getCurrentStatus();
        if (status == null || status.isDraft()) {
            return "ยังไม่ได้ยื่นคำร้องประเมิน";
        }
        if (status == RequestStatus.COMPLETED_FAIL) {
            return "ผลการประเมินไม่ผ่าน";
        }
        if (!status.carriesAPassedResult()) {
            return "อยู่ระหว่างการประเมิน (สถานะ: " + status.getThaiLabel()
                    + ") — ใช้ได้เมื่อผลการประเมินผ่าน";
        }
        List<AcademicDocument> doc9s = documentRepository
                .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(request.getId(), 9);
        if (doc9s.isEmpty() || doc9s.get(0).getJsonData() == null) {
            return "ยังไม่ได้ออกหนังสือแจ้งผลการประเมิน";
        }
        return "ผลการประเมินหมดอายุแล้ว";
    }

    /** Evaluations this applicant has submitted, newest first — drafts are not choices. */
    public List<AcademicRequest> findSubmittedEvaluations(Integer applicantId) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId).stream()
                .filter(r -> r.getCurrentStatus() != null && !r.getCurrentStatus().isDraft())
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
        // The result is evidenced by document 9, the notification of the result.
        List<AcademicDocument> doc9s = documentRepository
                .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(request.getId(), 9);
        if (doc9s.isEmpty() || doc9s.get(0).getJsonData() == null) {
            return false;
        }
        return !isEvaluationExpired(doc9s.get(0).getJsonData());
    }

    /**
     * Whether document 9 names an expiry date that has passed.
     *
     * <p>No date means no expiry, which is the historical behaviour and the
     * right default: an older document that simply never carried the field must
     * not be treated as lapsed.
     */
    private boolean isEvaluationExpired(String doc9Json) {
        try {
            Map<String, Object> data = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(doc9Json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
            Object raw = data.get("expiration_date");
            if (raw == null || raw.toString().isBlank()) {
                return false;
            }
            LocalDateTime expiry = parseThaiDate(raw.toString());
            return expiry != null && expiry.isBefore(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Could not read the expiry date on document 9: {}", e.getMessage());
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
