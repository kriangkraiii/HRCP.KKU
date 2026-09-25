package com.ecom.academic.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.dto.EvaluationSummary;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.repository.SignatureRequestRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ส่วนที่ ๓ (แบบประเมินผลการสอน) ของแบบ ก.พ.ว. มข. ๐๓ ฉบับเต็ม
 *
 * <p>ผลประเมินการสอนเกิดขึ้นใน Phase 1 และจบไปก่อนผู้ยื่นจะเริ่มคำร้องขอตำแหน่ง ข้อมูลทั้งหมด
 * จึงมีอยู่แล้วในเอกสารที่ 8 ของคำประเมินที่คำร้องผูกไว้ ({@link PositionRequest#getLinkedEvaluation()})
 * — เลขครั้งประชุม วันที่ รายวิชา ระดับผล ชื่อประธาน และลายเซ็นประธานในซองของเอกสารนั้น
 * ให้ผู้ยื่นพิมพ์ซ้ำก็มีแต่จะพิมพ์ผิด
 *
 * <p>เติมตอนสร้างไฟล์เท่านั้น เหมือน {@link OfficeFieldResolver} — ไม่เขียนลง {@code json_data}
 * ของเอกสารที่ 1 และไม่แตะ {@code frozenJson} ของซอง แฮชหลักฐานการลงนามจึงยังตรงเหมือนเดิม
 * และช่องพวกนี้ก็ไม่ไปโผล่เป็นช่องบังคับกรอกใน {@link DocumentCompleteness}
 */
@Component
public class TeachingEvaluationPartResolver {

    private static final Logger log = LoggerFactory.getLogger(TeachingEvaluationPartResolver.class);

    /** เอกสารที่ 1 ของคำร้องขอตำแหน่งคือแบบ ก.พ.ว. มข. ๐๓ — ฉบับเดียวที่มีส่วนที่ ๓ */
    public static final int FULL_FORM_DOCUMENT = 1;

    /** เอกสารที่ 8 ของ Phase 1 คือ "ส่วนที่ 3 แบบประเมินผลการสอน" ต้นทาง */
    static final int TEACHING_EVALUATION_DOCUMENT = 8;

    static final String CHAIR_SLOT = "committee_chair";

    /** ช่องลงนามของประธานในเทมเพลตเอกสารที่ 1 — ใช้วางรูปลายเซ็นจาก Phase 1 */
    public static final String CHAIR_ANCHOR = "s3_chair_name";

    static final String UNIVERSITY = "มหาวิทยาลัยขอนแก่น";

    private static final List<String> LEVELS = List.of("ชำนาญ", "ชำนาญพิเศษ", "เชี่ยวชาญ");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AcademicRequestService academicService;
    private final PositionRequestService positionService;
    private final SignatureRequestRepository envelopeRepository;
    private final SignatureStepRepository stepRepository;
    private final SignerNameResolver signerNameResolver;

    public TeachingEvaluationPartResolver(AcademicRequestService academicService,
            PositionRequestService positionService,
            SignatureRequestRepository envelopeRepository,
            SignatureStepRepository stepRepository,
            SignerNameResolver signerNameResolver) {
        this.academicService = academicService;
        this.positionService = positionService;
        this.envelopeRepository = envelopeRepository;
        this.stepRepository = stepRepository;
        this.signerNameResolver = signerNameResolver;
    }

    /**
     * ค่าของช่อง {@code s3_*} ทั้งหมด — ว่างเมื่อคำร้องไม่ได้ผูกผลประเมินไว้
     * ช่องที่ไม่มีค่าจะไม่อยู่ในผลลัพธ์ แล้วแบบฟอร์มจะขึ้นจุดไข่ปลาแทน
     */
    @Transactional(readOnly = true)
    public Map<String, String> partThreeFields(PositionRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        AcademicRequest evaluation = request == null ? null : request.getLinkedEvaluation();
        if (evaluation == null) {
            return fields;
        }

        Map<String, String> doc8 = academicService.getLatestDocumentData(evaluation.getId(),
                TEACHING_EVALUATION_DOCUMENT);
        if (doc8 == null) {
            doc8 = Map.of();
        }
        EvaluationSummary summary = academicService.summarize(evaluation);

        put(fields, "s3_meeting_no", doc8.get("meeting_no"));
        put(fields, "s3_meeting_date", doc8.get("meeting_date"));
        fields.put("s3_university", UNIVERSITY);
        put(fields, "s3_course_code", firstNonBlank(doc8.get("course_code"),
                summary == null ? null : summary.courseCode()));
        put(fields, "s3_course_name", firstNonBlank(doc8.get("course_name"),
                summary == null ? null : summary.courseName()));

        String level = levelOf(doc8, summary);
        put(fields, "s3_level", level != null && LEVELS.contains(level) ? level : null);
        put(fields, "s3_quality", qualityOf(level));

        // ชื่อและวันที่ในเอกสารที่ 8 คือสิ่งที่พิมพ์อยู่บนเอกสารฉบับนั้น ใช้ก่อน ถ้าว่างค่อยถามซองลงนาม
        String chairName = doc8.get("committee_president_name");
        String signDate = doc8.get("sign_date");
        if (isBlank(chairName) || isBlank(signDate)) {
            Optional<SignatureRequest> envelope = chairEnvelope(evaluation);
            if (envelope.isPresent()) {
                if (isBlank(chairName)) {
                    chairName = signerNameResolver.namesForEnvelope(envelope.get())
                            .get("committee_president_name");
                }
                if (isBlank(signDate)) {
                    signDate = chairStep(envelope.get())
                            .map(step -> AcademicRequestService.formatThaiDate(step.getSignedAt()))
                            .orElse(null);
                }
            }
        }
        put(fields, "s3_chair_name", chairName);
        put(fields, "s3_sign_date", signDate);
        return fields;
    }

    /**
     * JSON ของเอกสารที่เติมส่วนที่ ๓ แล้ว — เฉพาะเอกสารที่ 1 และเฉพาะช่องที่ยังว่าง
     *
     * @return JSON ชุดใหม่ หรือชุดเดิมเมื่อไม่มีอะไรต้องเติมหรืออ่านไม่ได้
     */
    @Transactional(readOnly = true)
    public String fillInto(PositionRequest request, int documentType, String json) {
        if (documentType != FULL_FORM_DOCUMENT || request == null || json == null || json.isBlank()) {
            return json;
        }
        try {
            Map<String, String> fields = partThreeFields(request);
            if (fields.isEmpty()) {
                return json;
            }
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            boolean changed = false;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                Object current = data.get(entry.getKey());
                if (current == null || String.valueOf(current).isBlank()) {
                    data.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            return changed ? objectMapper.writeValueAsString(data) : json;
        } catch (Exception e) {
            // ส่วนที่ ๓ ว่างดีกว่าเอกสารทั้งฉบับเปิดไม่ขึ้น
            log.warn("Could not fill teaching evaluation into position request {}: {}",
                    request.getId(), e.toString());
            return json;
        }
    }

    /** เหมือนข้างบน แต่เริ่มจากซองลงนามของเอกสารที่ 1 — ใช้ตอน render ซอง */
    @Transactional(readOnly = true)
    public String fillInto(SignatureRequest envelope, String json) {
        if (envelope == null || envelope.getModule() != SignatureModule.POSITION
                || envelope.getDocumentType() == null
                || envelope.getDocumentType() != FULL_FORM_DOCUMENT
                || envelope.getRequestId() == null) {
            return json;
        }
        return positionService.findById(envelope.getRequestId())
                .map(request -> fillInto(request, FULL_FORM_DOCUMENT, json))
                .orElse(json);
    }

    /**
     * ขั้นลงนามของประธานคณะอนุกรรมการที่เซ็นเอกสารที่ 8 ของ Phase 1 แล้ว — รูปลายเซ็นของขั้นนี้
     * ถูกวางซ้ำลงส่วนที่ ๓ ของเอกสารที่ 1 เพราะเป็นลายเซ็นเดียวกันบนข้อความเดียวกัน
     */
    @Transactional(readOnly = true)
    public Optional<SignatureStep> chairSignatureFor(SignatureRequest positionEnvelope) {
        if (positionEnvelope == null || positionEnvelope.getModule() != SignatureModule.POSITION
                || positionEnvelope.getDocumentType() == null
                || positionEnvelope.getDocumentType() != FULL_FORM_DOCUMENT
                || positionEnvelope.getRequestId() == null) {
            return Optional.empty();
        }
        return positionService.findById(positionEnvelope.getRequestId())
                .map(PositionRequest::getLinkedEvaluation)
                .flatMap(this::chairEnvelope)
                .flatMap(this::chairStep);
    }

    private Optional<SignatureRequest> chairEnvelope(AcademicRequest evaluation) {
        return envelopeRepository.findBlockingEnvelopes(SignatureModule.ACADEMIC, evaluation.getId(),
                TEACHING_EVALUATION_DOCUMENT).stream().findFirst();
    }

    private Optional<SignatureStep> chairStep(SignatureRequest envelope) {
        return stepRepository.findSignedSteps(envelope.getId()).stream()
                .filter(step -> CHAIR_SLOT.equals(step.getSlotKey()))
                .findFirst();
    }

    /**
     * ระดับผลการประเมิน — ช่องที่ติ๊กในเอกสารที่ 8 ก่อน เพราะเป็นสิ่งที่ประธานเซ็นรับรอง
     * ถ้าไม่มีค่อยใช้ผลสรุปของคำประเมิน (เอกสารที่ 9)
     */
    static String levelOf(Map<String, String> doc8, EvaluationSummary summary) {
        for (int i = 0; i < LEVELS.size(); i++) {
            String tick = doc8.get("final_level_" + (i + 1));
            if (!isBlank(tick)) {
                return LEVELS.get(i);
            }
        }
        return summary == null ? null : trimToNull(summary.resultLevel());
    }

    /** "อยู่" เมื่อได้ระดับใดระดับหนึ่ง "ไม่อยู่" เมื่อไม่ผ่าน ไม่รู้ผลก็ไม่เดา */
    static String qualityOf(String level) {
        if (level == null) {
            return null;
        }
        if (LEVELS.contains(level)) {
            return "อยู่";
        }
        if (level.contains("ไม่ผ่าน")) {
            return "ไม่อยู่";
        }
        return null;
    }

    private static void put(Map<String, String> fields, String key, String value) {
        if (!isBlank(value)) {
            fields.put(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
