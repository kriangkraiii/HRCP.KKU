package com.ecom.academic.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.model.UserDtls;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Smart Auto-Fill & Cross-Document Data Inheritance Engine.
 *
 * <p>Pre-populates applicant identity fields and propagates shared document data
 * (course details, committee members, meeting schedule, dean/head names) across
 * both Teaching Evaluation and Academic Position request lifecycles.
 */
@Component
public class DocumentDataAutoFillHelper {

    private static final Logger log = LoggerFactory.getLogger(DocumentDataAutoFillHelper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Reads the teaching evaluation a position request was built on. No cycle:
     * the academic service knows nothing of position requests or of this helper.
     */
    private final AcademicRequestService academicRequestService;

    /** Faculty directory synced from Fund Management — optional, absent in plain unit tests. */
    private FsFacultyRepository facultyRepository;

    public DocumentDataAutoFillHelper(AcademicRequestService academicRequestService) {
        this.academicRequestService = academicRequestService;
    }

    @Autowired(required = false)
    void setFacultyRepository(FsFacultyRepository facultyRepository) {
        this.facultyRepository = facultyRepository;
    }

    // ==================== Academic Request (Teaching Evaluation) ====================

    /**
     * Builds a comprehensive pre-filled data map for Teaching Evaluation documents (Docs 0 to 8).
     *
     * @param request      the academic request
     * @param docType      target document type (0..8)
     * @param existingJson already saved JSON for this document (if any)
     * @return merged Map of form field key-values
     */
    public Map<String, String> getPreFilledAcademicDocData(AcademicRequest request, int docType, String existingJson) {
        Map<String, String> data = new HashMap<>();

        // 1. Base User Profile defaults
        if (request != null && request.getApplicant() != null) {
            UserDtls user = request.getApplicant();
            fillUserProfileDefaults(data, user);
        }

        // 2. Cross-document data inheritance from prior documents
        if (request != null && request.getDocuments() != null) {
            Map<Integer, Map<String, String>> docsMap = parseAllAcademicDocs(request.getDocuments());

            // Inherit from Doc 1 (Course Info & Basic Request)
            Map<String, String> doc1 = docsMap.get(1);
            if (doc1 != null) {
                copyIfPresent(doc1, data, "course_code", "course_name", "academic_year",
                        "title", "applicant_name", "employee_type", "current_position",
                        "chk1", "chk2", "department", "faculty");
            }

            // Inherit Committee & Meeting info from Doc 4 or Doc 3 (Appointed Committee)
            Map<String, String> doc4 = docsMap.get(4);
            Map<String, String> doc3 = docsMap.get(3);
            Map<String, String> committeeSource = doc4 != null ? doc4 : doc3;
            if (committeeSource != null) {
                copyIfPresent(committeeSource, data,
                        "committee_1_name", "committee_2_name", "committee_3_name",
                        "committee_chair", "committee_member1", "committee_member2",
                        "meeting_date", "meeting_time", "meeting_location", "meeting_room",
                        "meeting_no", "subject_code", "subject_name");
            }

            // Inherit Meeting summary info from Doc 8 (Evaluation Result) into Doc 9
            if (docType == 9 && docsMap.containsKey(8)) {
                copyIfPresent(docsMap.get(8), data, "meeting_no", "meeting_date", "eval_result", "total_score", "eval_level");
            }
        }

        // 3. Overlay existing saved JSON data (Highest Priority - Never overwrite user modifications)
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                Map<String, String> saved = objectMapper.readValue(existingJson, new TypeReference<Map<String, String>>() {});
                if (saved != null) {
                    for (Map.Entry<String, String> e : saved.entrySet()) {
                        if (e.getValue() != null && !e.getValue().isBlank()) {
                            data.put(e.getKey(), e.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse existing JSON for academic doc {}: {}", docType, e.getMessage());
            }
        }

        return data;
    }

    // ==================== Position Request (Academic Position) ====================

    /**
     * Builds a comprehensive pre-filled data map for Academic Position documents (Docs 1 to 9).
     *
     * @param request      the position request
     * @param docType      target document type (1..9)
     * @param existingJson already saved JSON for this document (if any)
     * @return merged Map of form field key-values
     */
    public Map<String, String> getPreFilledPositionDocData(PositionRequest request, int docType, String existingJson) {
        Map<String, String> data = new HashMap<>();

        // 1. Base User Profile defaults
        if (request != null && request.getApplicant() != null) {
            UserDtls user = request.getApplicant();
            fillUserProfileDefaults(data, user);
            if (user.getFirstNameEn() != null && user.getLastNameEn() != null) {
                data.put("applicant_name_en", (user.getFirstNameEn() + " " + user.getLastNameEn()).trim());
            }
            if (user.getAcademicPositionEn() != null) {
                data.put("current_position_en", user.getAcademicPositionEn());
            }
        }

        // 2. Cross-document inheritance from Doc 1 (ก.พ.ว. 03) and Doc 2
        if (request != null && request.getDocuments() != null) {
            Map<Integer, Map<String, String>> docsMap = parseAllPositionDocs(request.getDocuments());

            // Doc 1 (ก.พ.ว. 03) is the master record for Position Requests
            Map<String, String> doc1 = docsMap.get(1);
            if (doc1 != null) {
                copyIfPresent(doc1, data,
                        "title", "applicant_name", "applicant_name_en",
                        "current_position", "current_position_en",
                        "target_position", "target_position_en",
                        "discipline", "field_of_study", "department", "faculty",
                        "birth_date", "age", "employment_date", "work_duration",
                        "salary", "education_bachelor", "education_master", "education_doctorate",
                        "head_of_department", "dean_name");
            }

            // Doc 2 (หนังสือแจ้งความประสงค์)
            Map<String, String> doc2 = docsMap.get(2);
            if (doc2 != null) {
                copyIfPresent(doc2, data, "target_position", "discipline", "faculty", "department");
            }

            // Doc 8 (รายชื่อผู้ทรงคุณวุฒิ)
            Map<String, String> doc8 = docsMap.get(8);
            if (doc8 != null && docType == 8) {
                copyIfPresent(doc8, data,
                        "expert_1_name", "expert_2_name", "expert_3_name",
                        "expert_1_affiliation", "expert_2_affiliation", "expert_3_affiliation");
            }
        }

        // 2b. The teaching evaluation this request was built on.
        //
        // The link has existed since the request was created but nothing ever
        // read it, so applicants retyped the course, the year and the result the
        // system was already holding — and a typo in any of them went unnoticed,
        // because there was nothing to compare against.
        if (request != null && request.getLinkedEvaluation() != null) {
            EvaluationSummary evaluation =
                    academicRequestService.summarize(request.getLinkedEvaluation());
            if (evaluation != null) {
                putIfAbsent(data, "teaching_eval_request_code", evaluation.requestCode());
                putIfAbsent(data, "teaching_eval_course_code", evaluation.courseCode());
                putIfAbsent(data, "teaching_eval_course_name", evaluation.courseName());
                putIfAbsent(data, "teaching_eval_academic_year", evaluation.academicYear());
                putIfAbsent(data, "teaching_eval_semester", evaluation.semester());
                putIfAbsent(data, "teaching_eval_result_level", evaluation.resultLevel());
                putIfAbsent(data, "teaching_eval_date", evaluation.evaluationDate());
                putIfAbsent(data, "teaching_eval_expiry", evaluation.expiryDate());

                // The first row of ก.พ.ว. มข. 03's teaching-experience table is
                // the course that was just evaluated often enough to be worth
                // starting from. Only ever a starting point: step 3 below puts
                // anything the applicant saved back on top.
                if (docType == 1) {
                    putIfAbsent(data, "teaching_subject_1", evaluation.courseName());
                    putIfAbsent(data, "teaching_semester_1", evaluation.semester());
                }
            }
        }

        // 2c. Doc 8 starting values the officer may change (Doc8AutoFill.defaults) — step 3 beats them.
        if (docType == 8 && request != null && request.getDocuments() != null) {
            data.putAll(Doc8AutoFill.defaults(parseAllPositionDocs(request.getDocuments()).get(4)));
        }

        Map<String, String> saved = Map.of();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                Map<String, String> parsed = objectMapper.readValue(existingJson, new TypeReference<Map<String, String>>() {});
                if (parsed != null) {
                    saved = parsed;
                }
            } catch (Exception e) {
                log.warn("Failed to parse existing JSON for position doc {}: {}", docType, e.getMessage());
            }
        }

        // 3. Overlay existing saved JSON data (Highest Priority)
        for (Map.Entry<String, String> e : saved.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                data.put(e.getKey(), e.getValue());
            }
        }

        // 4. Doc 7 (checklist) has its own field names — see Doc7AutoFill. What the
        // system knows is locked and beats the saved value; DEFAULT_ONLY fields are
        // starting values only (a saved ☐ is the officer unticking it).
        if (docType == 7 && request != null) {
            Map<String, String> derived = doc7Derived(request);
            data.putAll(Doc7AutoFill.locked(derived));
            for (String field : Doc7AutoFill.DEFAULT_ONLY) {
                String savedValue = saved.get(field);
                if (derived.containsKey(field) && (savedValue == null || savedValue.isBlank())) {
                    data.put(field, derived.get(field));
                }
            }
        }

        // 5. Doc 8 (summary for the experts) likewise — see Doc8AutoFill. Everything it
        // derives is locked; whatever the system does not know stays for the officer.
        if (docType == 8 && request != null) {
            data.putAll(doc8Derived(request));
        }

        return data;
    }

    /**
     * Fields of an officer-filled document (7, 8) that the officer may not change — the
     * save paths write these back over whatever was sent. Empty for every other document.
     */
    public Map<String, String> lockedFields(PositionRequest request, int docType) {
        if (request == null) {
            return Map.of();
        }
        return switch (docType) {
            case 7 -> Doc7AutoFill.locked(doc7Derived(request));
            case 8 -> doc8Derived(request);
            default -> Map.of();
        };
    }

    /** JSON-in/JSON-out variant of {@link #lockedFields} for the auto-draft endpoint. */
    public String pinLockedFieldsInJson(PositionRequest request, int docType, String jsonData) {
        Map<String, String> locked = lockedFields(request, docType);
        if (locked.isEmpty() || jsonData == null) {
            return jsonData;
        }
        try {
            Map<String, String> data = new java.util.LinkedHashMap<>(
                    objectMapper.readValue(jsonData, new TypeReference<Map<String, String>>() {}));
            data.putAll(locked);
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Could not pin locked doc {} fields into draft JSON: {}", docType, e.getMessage());
            return null;
        }
    }

    private Map<String, String> doc8Derived(PositionRequest request) {
        Map<Integer, Map<String, String>> docs = request.getDocuments() == null
                ? Map.of() : parseAllPositionDocs(request.getDocuments());
        EvaluationSummary evaluation = request.getLinkedEvaluation() == null
                ? null : academicRequestService.summarize(request.getLinkedEvaluation());
        return Doc8AutoFill.derive(request, docs.get(1), docs.get(6), evaluation);
    }

    private Map<String, String> doc7Derived(PositionRequest request) {
        Map<Integer, Map<String, String>> docs = request.getDocuments() == null
                ? Map.of() : parseAllPositionDocs(request.getDocuments());
        EvaluationSummary evaluation = request.getLinkedEvaluation() == null
                ? null : academicRequestService.summarize(request.getLinkedEvaluation());
        Map<String, String> derived = Doc7AutoFill.derive(request, docs.get(1), docs.get(2), docs.get(4),
                evaluation == null ? null : evaluation.resultLevel(), request.getLinkedEvaluation() != null,
                request.getSubmissionDate() != null ? request.getSubmissionDate().toLocalDate() : java.time.LocalDate.now());

        // ผู้ยื่นหลายคนไม่ได้กรอกเบอร์ในโปรไฟล์ — ฟอร์มของผู้ยื่นเติมจากทะเบียนบุคลากร (/api/my/profile)
        // แต่ทางนั้นคืนข้อมูลของคนที่ล็อกอินอยู่ เจ้าหน้าที่จึงต้องค้นทะเบียนของผู้ยื่นฝั่งเซิร์ฟเวอร์เอง
        if (facultyRepository != null && request.getApplicant() != null
                && (!derived.containsKey("phone_mobile") || !derived.containsKey("email"))) {
            facultyRepository.findByEmailNormalized(request.getApplicant().getEmail()).ifPresent(f -> {
                putIfAbsent(derived, "phone_mobile", f.getTel());
                putIfAbsent(derived, "email", f.getEmail());
            });
        }
        return derived;
    }

    /** Writes a value only when there is one and nothing has claimed the key. */
    private void putIfAbsent(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.putIfAbsent(key, value);
        }
    }

    // ==================== Helper Methods ====================

    private void fillUserProfileDefaults(Map<String, String> data, UserDtls user) {
        String fullName = (user.getFirstName() != null && user.getLastName() != null)
                ? (user.getFirstName() + " " + user.getLastName()).trim()
                : (user.getName() != null ? user.getName() : "");

        if (user.getTitle() != null && !user.getTitle().isBlank()) {
            data.put("title", user.getTitle());
        }
        if (!fullName.isBlank()) {
            data.put("applicant_name", fullName);
            data.put("name", fullName);
        }
        if (user.getAcademicPosition() != null && !user.getAcademicPosition().isBlank()) {
            data.put("current_position", user.getAcademicPosition());
            data.put("academic_position", user.getAcademicPosition());
        }
        if (user.getEmail() != null) {
            data.put("applicant_email", user.getEmail());
            data.put("email", user.getEmail());
        }
        if (user.getMobileNumber() != null) {
            data.put("applicant_phone", user.getMobileNumber());
            data.put("tel", user.getMobileNumber());
            data.put("phone", user.getMobileNumber());
        }

        // Standard organizational defaults for College of Computing, Khon Kaen University
        data.putIfAbsent("faculty", "วิทยาลัยการคอมพิวเตอร์");
        data.putIfAbsent("department", "สาขาวิชาวิทยาการคอมพิวเตอร์");
        data.putIfAbsent("employee_type", "พนักงานมหาวิทยาลัย");
    }

    private Map<Integer, Map<String, String>> parseAllAcademicDocs(List<AcademicDocument> documents) {
        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (AcademicDocument doc : documents) {
            if (doc.getDocumentType() != null && doc.getJsonData() != null && !doc.getJsonData().isBlank()) {
                try {
                    Map<String, String> parsed = objectMapper.readValue(doc.getJsonData(), new TypeReference<Map<String, String>>() {});
                    result.put(doc.getDocumentType(), parsed);
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private Map<Integer, Map<String, String>> parseAllPositionDocs(List<PositionDocument> documents) {
        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (PositionDocument doc : documents) {
            if (doc.getDocumentType() != null && doc.getJsonData() != null && !doc.getJsonData().isBlank()) {
                try {
                    Map<String, String> parsed = objectMapper.readValue(doc.getJsonData(), new TypeReference<Map<String, String>>() {});
                    result.put(doc.getDocumentType(), parsed);
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    private void copyIfPresent(Map<String, String> source, Map<String, String> target, String... keys) {
        for (String key : keys) {
            String val = source.get(key);
            if (val != null && !val.isBlank() && !target.containsKey(key)) {
                target.put(key, val);
            }
        }
    }
}
