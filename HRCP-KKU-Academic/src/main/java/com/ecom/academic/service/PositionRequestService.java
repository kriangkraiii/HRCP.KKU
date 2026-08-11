package com.ecom.academic.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionDocumentEditLogRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.PositionStatusHistoryRepository;
import com.ecom.model.UserDtls;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PositionRequestService {

    private final PositionRequestRepository requestRepository;

    private final PositionDocumentRepository documentRepository;

    private final PositionStatusHistoryRepository statusHistoryRepository;

    private final PositionDocumentEditLogRepository editLogRepository;

    private final AcademicRequestRepository academicRequestRepository;

    private final AcademicDocumentRepository academicDocumentRepository;

    private final PositionEmailService emailService;

    private final com.ecom.academic.repository.PositionAttachmentRepository attachmentRepository;

    public PositionRequestService(
            PositionRequestRepository requestRepository,
            PositionDocumentRepository documentRepository,
            PositionStatusHistoryRepository statusHistoryRepository,
            PositionDocumentEditLogRepository editLogRepository,
            AcademicRequestRepository academicRequestRepository,
            AcademicDocumentRepository academicDocumentRepository,
            PositionEmailService emailService,
            com.ecom.academic.repository.PositionAttachmentRepository attachmentRepository) {
        this.requestRepository = requestRepository;
        this.documentRepository = documentRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.editLogRepository = editLogRepository;
        this.academicRequestRepository = academicRequestRepository;
        this.academicDocumentRepository = academicDocumentRepository;
        this.emailService = emailService;
        this.attachmentRepository = attachmentRepository;
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
    public static final List<Integer> APPLICANT_DOCS = Arrays.asList(1, 2, 3, 4, 6, 7, 9);

    // Admin fills these (hidden from applicant)
    public static final List<Integer> ADMIN_DOCS = Arrays.asList(5, 8);

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
     * ดึง AcademicRequest ที่ผ่านการประเมินผลการสอน (COMPLETED)
     * และมีเอกสารที่ 8 ที่ยังไม่หมดอายุ
     */
    public List<AcademicRequest> getEligibleEvaluations(Integer userId) {
        List<AcademicRequest> completed = academicRequestRepository.findByApplicantIdOrderByCreatedAtDesc(userId)
                .stream()
                .filter(r -> r.getCurrentStatus() == RequestStatus.COMPLETED)
                .toList();

        return completed.stream().filter(r -> {
            try {
                List<AcademicDocument> doc8s = academicDocumentRepository
                        .findByRequestIdAndDocumentType(r.getId(), 8);
                if (doc8s.isEmpty())
                    return false;

                AcademicDocument doc8 = doc8s.get(0);
                if (doc8.getJsonData() == null)
                    return false;

                Map<String, Object> data = objectMapper.readValue(doc8.getJsonData(),
                        new TypeReference<Map<String, Object>>() {
                        });
                String expDateStr = (String) data.get("expiration_date");
                if (expDateStr == null || expDateStr.isBlank())
                    return true; // no expiry = valid

                // Thai date format: parse simply — if it contains year > current year, valid
                return !isExpired(expDateStr);
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    private boolean isExpired(String thaiDateStr) {
        try {
            // Extract Buddhist year and convert to Gregorian
            String cleaned = thaiDateStr.replaceAll("[^0-9]", " ").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length >= 3) {
                int buddhistYear = Integer.parseInt(parts[parts.length - 1]);
                int gregorianYear = buddhistYear - 543;
                int month = Integer.parseInt(parts[parts.length - 2]);
                int day = Integer.parseInt(parts[parts.length - 3]);
                LocalDate expDate = LocalDate.of(gregorianYear, month, day);
                return expDate.isBefore(LocalDate.now());
            }
            // If Thai month names are used, try pattern with month names
            return false; // assume not expired if can't parse
        } catch (Exception e) {
            return false; // assume not expired if can't parse
        }
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

    public Optional<PositionRequest> findDraftByApplicant(Integer userId) {
        return requestRepository.findDraftByApplicantId(userId);
    }

    public boolean hasActiveRequest(Integer userId) {
        List<PositionRequestStatus> terminal = Arrays.asList(
                PositionRequestStatus.SENT_TO_HR);
        return requestRepository.findActiveByApplicantId(userId, terminal).isPresent();
    }

    @Transactional
    public PositionRequest submitRequest(PositionRequest request) {
        PositionRequestStatus old = request.getCurrentStatus();
        request.setCurrentStatus(PositionRequestStatus.DOCUMENT_RECEIVED);
        request.setSubmissionDate(LocalDateTime.now());
        request = requestRepository.save(request);

        addStatusHistory(request, old, PositionRequestStatus.DOCUMENT_RECEIVED, null, "ส่งคำร้องเข้าระบบ");

        // Send notification to admins
        try {
            emailService.sendNewRequestNotificationToAdmins(request);
        } catch (Exception e) {
            System.err.println("Email notification failed on submit: " + e.getMessage());
        }

        return request;
    }

    @Transactional
    public PositionRequest updateStatus(Long requestId, PositionRequestStatus newStatus,
            UserDtls changedBy, String note) {
        PositionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + requestId));

        PositionRequestStatus old = request.getCurrentStatus();
        request.setCurrentStatus(newStatus);
        request = requestRepository.save(request);

        addStatusHistory(request, old, newStatus, changedBy, note);

        // Send email notification to applicant
        try {
            emailService.sendStatusChangeEmail(request, old, newStatus);
        } catch (Exception e) {
            System.err.println("Email notification failed on status update: " + e.getMessage());
        }

        return request;
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

    public List<PositionStatusHistory> getStatusHistory(Long requestId) {
        return statusHistoryRepository.findByRequestId(requestId);
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
            String filePath, String label, Integer copyNumber, String filledBy) {
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
        doc.setDocumentLabel(label);
        doc.setIsDraft(false);
        doc.setFilledBy(filledBy);
        PositionDocument saved = documentRepository.save(doc);

        // Sync doc 2 fields back to request
        if (documentType == 2 && jsonData != null) {
            syncDoc2ToRequest(request, jsonData);
        }

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

    // ================== Attachments ==================

    public List<com.ecom.academic.model.PositionAttachment> getAttachments(Long requestId) {
        return attachmentRepository.findActiveByRequestId(requestId);
    }

    public long countAttachments(Long requestId) {
        return attachmentRepository.countActiveByRequestId(requestId);
    }

    public void saveAttachment(com.ecom.academic.model.PositionAttachment attachment) {
        attachmentRepository.save(attachment);
    }

    public Optional<com.ecom.academic.model.PositionAttachment> findAttachmentById(Long id) {
        return attachmentRepository.findById(id);
    }

    public void deleteAttachment(Long id) {
        attachmentRepository.findById(id).ifPresent(att -> {
            att.setIsDeleted(true);
            attachmentRepository.save(att);
        });
    }
}
