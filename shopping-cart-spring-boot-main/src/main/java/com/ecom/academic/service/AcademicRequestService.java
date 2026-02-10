package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.RequestStatusHistory;
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
    private RequestStatusHistoryRepository historyRepository;

    @Autowired
    private AcademicEmailService emailService;

    public AcademicRequest createRequest(UserDtls applicant) {
        AcademicRequest request = new AcademicRequest();
        request.setApplicant(applicant);
        request.setCurrentStatus(RequestStatus.RECEIVED);
        request.setSubmissionDate(LocalDateTime.now());
        return requestRepository.save(request);
    }

    public Optional<AcademicRequest> findById(Long id) {
        return requestRepository.findById(id);
    }

    public List<AcademicRequest> findByApplicant(Integer applicantId) {
        return requestRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId);
    }

    public List<AcademicRequest> findAll() {
        return requestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public AcademicRequest updateStatus(Long requestId, RequestStatus newStatus, UserDtls changedBy, String note) {
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

        try {
            emailService.sendStatusChangeEmail(request, oldStatus, newStatus);
        } catch (Exception e) {
            // Log but don't fail the status update
            System.err.println("Failed to send email notification: " + e.getMessage());
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
}
