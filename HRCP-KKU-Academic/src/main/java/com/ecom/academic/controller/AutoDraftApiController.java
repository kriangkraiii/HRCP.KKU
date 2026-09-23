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
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.Doc7Scoring;
import com.ecom.academic.service.DocumentFieldOwnership;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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

            boolean isAdmin = ROLE_ADMIN.equals(user.getRole());

            // บันทึกร่างอัตโนมัติก็คือการเขียนทับเอกสาร จึงต้องผ่านประตูเดียวกับการกดบันทึก:
            // หลังส่งคำร้องแล้วผู้ยื่นแก้ได้เฉพาะเอกสารที่แอดมินส่งกลับมาให้แก้เท่านั้น
            if (!isAdmin && !academicService.canApplicantEditDocument(request, docType)) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "เอกสารถูกล็อก แก้ไขได้เมื่อแอดมินส่งกลับมาให้แก้ไขเท่านั้น"));
            }
            // แอดมินเคยข้ามการเช็กล็อกทั้งหมดในทางนี้ ทั้งที่หน้าเว็บปกติห้ามไว้ — เอกสารที่
            // เวียนลงนามไปแล้วจึงถูกแก้เงียบ ๆ ผ่าน auto-draft ได้
            boolean signingComplete = academicService.isSigningComplete(requestId, docType);
            if (isAdmin && academicService.isDocumentLockedForSigning(requestId, docType)
                    && !signingComplete) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "เอกสารนี้อยู่ระหว่างการเวียนลงนาม จึงแก้ไขไม่ได้"));
            }

            String label = academicService.getDocLabel(docType);

            // ลงนามครบแล้ว: เหลือให้สารบรรณลงเลขที่หนังสือกับวันที่ และต้องลงครบทุกสำเนา
            // เอกสารที่ 5 มีสามแถว (กรรมการคนละท่าน) แต่เป็นหนังสือฉบับเดียวกัน
            if (isAdmin && signingComplete) {
                Map<String, String> submitted = parseFields(jsonData);
                if (submitted == null) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "ข้อมูลที่ส่งมาไม่ถูกต้อง"));
                }
                academicService.saveOfficeFieldsAcrossCopies(request, docType, submitted, label);
                academicService.logDocumentEdit(request, docType, label, user,
                        AcademicDocumentEditLog.EditAction.DRAFT_SAVED);
                return ResponseEntity.ok(Map.of("status", "saved", "type", "academic"));
            }

            // เจ้าหน้าที่แก้เอกสารของผู้ยื่นไม่ได้ และผู้ยื่นก็แก้ช่องของเจ้าหน้าที่ไม่ได้เช่นกัน
            String filtered = DocumentFieldOwnership.mergeJson(SignatureModule.ACADEMIC, docType,
                    isAdmin, jsonData, academicService.getLatestDocumentData(requestId, docType));
            if (filtered == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "ข้อมูลที่ส่งมาไม่ถูกต้อง"));
            }
            jsonData = filtered;

            // เอกสารที่ 7: ปุ่ม "ส่งเวียนลงนาม" บันทึกผ่านทางนี้ ไม่ใช่ปุ่มบันทึก — ถ้าไม่คำนวณผล
            // ตรงนี้ด้วย ซองจะแช่แข็งเอกสารที่ไม่มีระดับผลประเมิน และสถานะไม่เลื่อนเมื่อลงนามครบ
            if (isAdmin && docType == 7) {
                Map<String, String> fields = parseFields(jsonData);
                if (fields != null && Doc7Scoring.hasAllSectionScores(fields)) {
                    Map<String, String> derived = new java.util.LinkedHashMap<>(fields);
                    Doc7Scoring.derive(derived);
                    jsonData = new ObjectMapper().writeValueAsString(derived);
                }
            }

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

            boolean isAdmin = ROLE_ADMIN.equals(user.getRole());

            // ทางนี้ไม่เคยเช็กเลยว่าเอกสารฉบับนั้นเป็นของใคร ผู้ยื่นจึงยิงร่างทับเอกสารของ
            // เจ้าหน้าที่ (7, 8) ได้ ทั้งที่หน้าเว็บปกติกันไว้แล้ว
            if (!isAdmin && !positionService.canApplicantEditDocument(request, docType)) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "เอกสารถูกล็อก แก้ไขได้เมื่อแอดมินส่งกลับมาให้แก้ไขเท่านั้น"));
            }
            boolean signingComplete = positionService.isSigningComplete(requestId, docType);
            if (isAdmin && positionService.isDocumentLockedForSigning(requestId, docType)
                    && !signingComplete) {
                return ResponseEntity.status(409)
                        .body(Map.of("error", "เอกสารนี้อยู่ระหว่างการเวียนลงนาม จึงแก้ไขไม่ได้"));
            }

            String label = positionService.getDocLabel(docType);
            String filledBy = isAdmin ? "ADMIN" : "APPLICANT";

            // เอกสารที่ลงนามครบแล้ว เหลือให้สารบรรณลงเลขที่หนังสือกับวันที่เท่านั้น
            Map<String, String> existing = positionService.getLatestDocumentData(requestId, docType);
            String filtered = (isAdmin && signingComplete)
                    ? DocumentFieldOwnership.mergeOfficeFieldsJson(
                            SignatureModule.POSITION, docType, jsonData, existing)
                    : DocumentFieldOwnership.mergeJson(
                            SignatureModule.POSITION, docType, isAdmin, jsonData, existing);
            // เฟส 2 เก็บเอกสารฉบับละแถวเดียว ไม่มีสำเนาแบบเอกสารที่ 5 ของเฟส 1
            if (filtered == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "ข้อมูลที่ส่งมาไม่ถูกต้อง"));
            }
            jsonData = filtered;

            if (!isAdmin) {
                // ...nor change the position asked for, which was fixed at creation
                String pinned = positionService.pinTargetPositionInJson(requestId, jsonData);
                if (pinned != null)
                    jsonData = pinned;
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

    /** request body ดิบ → map ของช่อง คืน null เมื่ออ่านไม่ออก (ผู้เรียกต้องปฏิเสธ) */
    private Map<String, String> parseFields(String jsonData) {
        if (jsonData == null || jsonData.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(jsonData,
                    new TypeReference<Map<String, String>>() {
                    });
        } catch (Exception e) {
            return null;
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
