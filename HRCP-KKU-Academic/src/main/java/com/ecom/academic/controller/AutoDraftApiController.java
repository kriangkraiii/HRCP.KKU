package com.ecom.academic.controller;

import java.security.Principal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@RestController
@RequestMapping("/api")
public class AutoDraftApiController {

    private static final Logger log = LoggerFactory.getLogger(AutoDraftApiController.class);
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AcademicRequestService academicService;

    private final PositionRequestService positionService;

    private final UserRepository userRepository;

    public AutoDraftApiController(
            AcademicRequestService academicService,
            PositionRequestService positionService,
            UserRepository userRepository) {
        this.academicService = academicService;
        this.positionService = positionService;
        this.userRepository = userRepository;
    }

    /** Auto-draft for Phase 1 (Teaching Evaluation) */
    @PostMapping("/draft/academic/{requestId}/{docType}")
    public ResponseEntity<?> academicDraft(@PathVariable Long requestId,
            @PathVariable int docType,
            @RequestBody String jsonData,
            Principal principal) {
        try {
            UserDtls user = getUser(principal);
            if (user == null)
                return ResponseEntity.status(401).build();

            AcademicRequest request = academicService.findById(requestId).orElse(null);
            if (request == null)
                return ResponseEntity.notFound().build();

            if (!mayEdit(user, request.getApplicant()))
                return ResponseEntity.status(403).build();

            // บันทึกร่างอัตโนมัติก็คือการเขียนทับเอกสาร จึงต้องผ่านประตูเดียวกับการกดบันทึก:
            // หลังส่งคำร้องแล้วผู้ยื่นแก้ได้เฉพาะเอกสารที่แอดมินส่งกลับมาให้แก้เท่านั้น
            if (!ROLE_ADMIN.equals(user.getRole())
                    && !academicService.canApplicantEditDocument(request, docType)) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "เอกสารถูกล็อก แก้ไขได้เมื่อแอดมินส่งกลับมาให้แก้ไขเท่านั้น"));
            }

            String label = academicService.getDocLabel(docType);
            academicService.saveDraft(request, docType, jsonData, label, null);

            // Log academic document edit
            academicService.logDocumentEdit(request, docType, label, user,
                    AcademicDocumentEditLog.EditAction.DRAFT_SAVED);

            return ResponseEntity.ok(Map.of("status", "saved", "type", "academic"));
        } catch (Exception e) {
            return internalError("academic draft", requestId, e);
        }
    }

    /** Auto-draft for Phase 2 (Position Request) */
    @PostMapping("/draft/position/{requestId}/{docType}")
    public ResponseEntity<?> positionDraft(@PathVariable Long requestId,
            @PathVariable int docType,
            @RequestBody String jsonData,
            Principal principal) {
        try {
            UserDtls user = getUser(principal);
            if (user == null)
                return ResponseEntity.status(401).build();

            PositionRequest request = positionService.findById(requestId)
                    .orElse(null);
            if (request == null)
                return ResponseEntity.notFound().build();

            if (!mayEdit(user, request.getApplicant()))
                return ResponseEntity.status(403).build();

            String label = positionService.getDocLabel(docType);
            String filledBy = ROLE_ADMIN.equals(user.getRole()) ? "ADMIN" : "APPLICANT";
            // Applicants may neither forge nor erase staff-filled fields (e.g. doc 7)
            if (!ROLE_ADMIN.equals(user.getRole())) {
                String sanitized = positionService.preserveStaffOnlyFieldsInJson(requestId, docType, jsonData);
                if (sanitized != null)
                    jsonData = sanitized;
            }
            positionService.saveDraft(request, docType, jsonData, label, filledBy);

            // Log every document edit
            positionService.logDocumentEdit(request, docType, label, user,
                    PositionDocumentEditLog.EditAction.DRAFT_SAVED);

            return ResponseEntity.ok(Map.of("status", "saved", "type", "position"));
        } catch (Exception e) {
            return internalError("position draft", requestId, e);
        }
    }

    /** Toggle auto-draft setting */
    @PostMapping("/user/auto-draft-toggle")
    public ResponseEntity<?> toggleAutoDraft(Principal principal) {
        try {
            UserDtls user = getUser(principal);
            if (user == null)
                return ResponseEntity.status(401).build();

            Boolean current = user.getAutoDraftEnabled();
            boolean newVal = !(current != null && current);
            user.setAutoDraftEnabled(newVal);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("enabled", newVal));
        } catch (Exception e) {
            return internalError("auto-draft toggle", null, e);
        }
    }

    private UserDtls getUser(Principal principal) {
        if (principal == null)
            return null;
        return userRepository.findByEmail(principal.getName());
    }

    /** Admins may edit any request; everyone else only their own. */
    private boolean mayEdit(UserDtls user, UserDtls applicant) {
        if (ROLE_ADMIN.equals(user.getRole()))
            return true;
        return applicant != null && applicant.getId() != null
                && applicant.getId().equals(user.getId());
    }

    /**
     * Logs the real cause server-side and returns an opaque body — exception
     * text has leaked database hosts and filesystem paths to callers before.
     */
    private ResponseEntity<Map<String, String>> internalError(String operation, Long requestId, Exception e) {
        log.error("Auto-draft {} failed for request {}: {}", operation, requestId, e.toString(), e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "ไม่สามารถบันทึกร่างเอกสารได้"));
    }
}
