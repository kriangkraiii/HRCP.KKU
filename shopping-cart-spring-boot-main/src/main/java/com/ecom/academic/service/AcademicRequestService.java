package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.ecom.model.UserDtls;

@Service
public class AcademicRequestService {

    @Autowired
    private AcademicRequestRepository requestRepository;

    @Autowired
    private AcademicDocumentRepository documentRepository;

    @Autowired
    private AcademicAttachmentRepository attachmentRepository;

    @Autowired
    private RequestStatusHistoryRepository historyRepository;

    @Autowired
    private AcademicEmailService emailService;

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
        request.setCurrentStatus(newStatus);
        requestRepository.save(request);

        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        historyRepository.save(history);

        if (sendNotification) {
            try {
                emailService.sendStatusChangeEmail(request, oldStatus, newStatus);
            } catch (Exception e) {
                System.err.println("Failed to send email notification: " + e.getMessage());
            }
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

    public List<RequestStatusHistory> getStatusHistory(Long requestId) {
        return historyRepository.findByRequestIdOrderByChangedAtDesc(requestId);
    }

    public AcademicRequest save(AcademicRequest request) {
        return requestRepository.save(request);
    }

    /**
     * Check if applicant has any active (non-terminal) requests.
     * Terminal statuses are: REJECTED, COMPLETED
     * DRAFT is excluded from active check (ถ้ามี draft ถือว่ายังสร้างคำร้องได้)
     * Active means: RECEIVED, SUB_COMMITTEE_APPOINTED, MEETING_SCHEDULED,
     * COMPLETED_PASS, COMPLETED_REVISE
     */
    public boolean hasActiveRequest(Integer applicantId) {
        List<RequestStatus> excludedStatuses = Arrays.asList(
                RequestStatus.REJECTED, RequestStatus.COMPLETED, RequestStatus.DRAFT);
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

    public Optional<AcademicAttachment> findAttachmentById(Long attachmentId) {
        return attachmentRepository.findById(attachmentId);
    }

    public void deleteAttachment(Long attachmentId) {
        attachmentRepository.deleteById(attachmentId);
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
                if (request.getCurrentStatus().ordinal() < RequestStatus.SUB_COMMITTEE_APPOINTED.ordinal()) {
                    updateStatus(requestId, RequestStatus.SUB_COMMITTEE_APPOINTED, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกเอกสารคำสั่งแต่งตั้งอนุกรรมการ", sendNotify);
                }
            }
            case 5 -> {
                // Doc 5 auto-status is handled separately via /send-suggestion endpoint
            }
            case 4 -> {
                if (request.getCurrentStatus().ordinal() < RequestStatus.MEETING_SCHEDULED.ordinal()) {
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
                    if ("ไม่ผ่าน".equals(evalLevel)) {
                        updateStatus(requestId, RequestStatus.COMPLETED_FAIL, changedBy,
                                "อัพเดตอัตโนมัติ: ผลการประเมิน - " + evalLevel, sendNotify);
                    } else if (!evalLevel.isEmpty()) {
                        updateStatus(requestId, RequestStatus.COMPLETED_PASS, changedBy,
                                "อัพเดตอัตโนมัติ: ผลการประเมิน - " + evalLevel, sendNotify);
                    }
                } catch (Exception e) {
                    updateStatus(requestId, RequestStatus.COMPLETED_PASS, changedBy,
                            "อัพเดตอัตโนมัติ: บันทึกแบบฟอร์มประเมิน", sendNotify);
                }
            }
            case 8 -> {
                updateStatus(requestId, RequestStatus.COMPLETED, changedBy,
                        "อัพเดตอัตโนมัติ: บันทึกเอกสารแจ้งผลการประเมิน", sendNotify);
            }
        }
    }

    /**
     * ค้นหาคำร้องตามชื่อผู้ยื่น
     */
    public List<AcademicRequest> searchByApplicantName(String name) {
        return requestRepository.findByApplicantNameContainingIgnoreCaseOrderByCreatedAtDesc(name);
    }

    /**
     * ดึงเอกสารเรียงตามลำดับ documentType
     */
    public List<AcademicDocument> getDocumentsSorted(Long requestId) {
        return documentRepository.findByRequestIdOrderByDocumentTypeAscCopyNumberAsc(requestId);
    }

    /**
     * ส่งอีเมลข้อเสนอแนะถึงผู้ยื่นคำร้อง
     */
    public void sendSuggestionEmail(AcademicRequest request, String suggestionsText) {
        emailService.sendSuggestionEmail(request, suggestionsText);
    }
}
