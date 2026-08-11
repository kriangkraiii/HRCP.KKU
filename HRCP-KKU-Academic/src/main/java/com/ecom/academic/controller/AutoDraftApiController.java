package com.ecom.academic.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

            String label = "เอกสารที่ " + docType;
            academicService.saveDraft(request, docType, jsonData, label, null);

            // Log is not needed for academic (Phase 1) auto-draft — edit history is only for Position (Phase 2)

            return ResponseEntity.ok(Map.of("status", "saved", "type", "academic"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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

            String label = positionService.getDocLabel(docType);
            String filledBy = "ROLE_ADMIN".equals(user.getRole()) ? "ADMIN" : "APPLICANT";
            positionService.saveDraft(request, docType, jsonData, label, filledBy);

            // Log every document edit
            positionService.logDocumentEdit(request, docType, label, user,
                    PositionDocumentEditLog.EditAction.DRAFT_SAVED);

            return ResponseEntity.ok(Map.of("status", "saved", "type", "position"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private UserDtls getUser(Principal principal) {
        if (principal == null)
            return null;
        return userRepository.findByEmail(principal.getName());
    }
}
