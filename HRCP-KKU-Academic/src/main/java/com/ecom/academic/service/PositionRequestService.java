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
import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionAttachment;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionAttachmentRepository;
import com.ecom.academic.repository.PositionDocumentEditLogRepository;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.PositionStatusHistoryRepository;
import com.ecom.model.UserDtls;
import com.ecom.search.service.SearchQueryNormalizer;

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

    private final com.ecom.service.UploadPaths uploadPaths;

    private final com.ecom.external.repository.ScopusPublicationRepository publicationRepository;

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
            PositionRequestPublicationRepository publicationLinkRepository,
            com.ecom.service.UploadPaths uploadPaths,
            com.ecom.external.repository.ScopusPublicationRepository publicationRepository) {
        this.uploadPaths = uploadPaths;
        this.publicationRepository = publicationRepository;
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

    /**
     * ชื่อเอกสารตามลำดับที่แสดง — เอกสารของผู้ยื่นก่อน ตามด้วยของเจ้าหน้าที่
     *
     * <p>key คือ document type ที่ใช้ใน URL ฐานข้อมูล และเทมเพลต ห้ามเปลี่ยน ส่วนเลขที่ผู้ใช้เห็น
     * ("เอกสารที่ N") คือลำดับในแผนที่นี้ ดู {@link #docNumber(int)}
     */
    private static final Map<Integer, String> DOC_LABELS = new LinkedHashMap<>();
    static {
        DOC_LABELS.put(1, "แบบ ก.พ.ว. มข. 03 (ส่วนที่ 1–5)");
        DOC_LABELS.put(2, "หนังสือแจ้งความประสงค์เรื่องการรับรู้ข้อมูล");
        DOC_LABELS.put(3, "แบบรับรองจริยธรรมและจรรยาบรรณ");
        DOC_LABELS.put(4, "บันทึกรับรองผลงานทางวิชาการ (วิทยานิพนธ์)");
        // 5 (แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา) รวมเข้าเป็นส่วนที่ ๒ ของเอกสารที่ 1 แล้ว
        DOC_LABELS.put(6, "บันทึกข้อความจริยธรรมการวิจัย (Exemption)");
        DOC_LABELS.put(9, "ลักษณะการมีส่วนร่วมในผลงาน");
        DOC_LABELS.put(7, "แบบฟอร์มตรวจสอบคุณสมบัติ (Checklist)");
        DOC_LABELS.put(8, "แบบสรุปรายละเอียดและรายชื่อผู้ทรงคุณวุฒิ");
    }

    /**
     * เลขที่แสดงเป็น "เอกสารที่ N" ของ document type นี้ — type 6 → 5, 9 → 6, 7 → 7, 8 → 8
     * type ที่ไม่รู้จักคืนตัวเอง
     */
    public static int docNumber(int type) {
        int n = 1;
        for (int key : DOC_LABELS.keySet()) {
            if (key == type) {
                return n;
            }
            n++;
        }
        return type;
    }

    // Who owns which document — and which fields inside it — lives in
    // DocumentFieldOwnership, shared with Phase 1. These two read from it rather
    // than keeping a second copy: ADMIN_DOCS used to be a hand-maintained list
    // that nothing referenced, so it quietly disagreed with the real rule.
    /**
     * เอกสารที่การ<em>ลงนามครบ</em>ทำให้คำร้องเดินไปขั้นถัดไป
     *
     * <p>ต้องตรงกับ {@code switch} ใน {@link #autoUpdateStatusByDocument} เสมอ
     * ผู้เรียกคือ {@link SignedDocumentStatusAdvancer} ตอนซองลายเซ็นปิด
     */
    public static final Set<Integer> STATUS_ADVANCING_DOCUMENTS = Set.of(7, 8);

    public static final List<Integer> APPLICANT_DOCS =
            DocumentFieldOwnership.applicantDocuments(SignatureModule.POSITION);

    public static final List<Integer> ADMIN_DOCS = DOC_LABELS.keySet().stream()
            .filter(type -> !APPLICANT_DOCS.contains(type))
            .sorted()
            .toList();

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
        return DOC_LABELS.getOrDefault(type, "เอกสารที่ " + docNumber(type));
    }

    /** {@link #docNumber(int)} สำหรับเทมเพลต: {@code ${@positionRequestService.docNo(documentType)}} */
    public int docNo(int type) {
        return docNumber(type);
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
        Set<Long> spent = getSpentEvaluations(userId).keySet();
        return academicRequestService.findUsableEvaluations(userId).stream()
                .filter(evaluation -> !spent.contains(evaluation.getId()))
                .toList();
    }

    /**
     * Every teaching evaluation this applicant has submitted, each marked with
     * whether it has already been used by a position request and, if it cannot
     * back one at all, why.
     *
     * <p>The selectable ones are exactly {@link #getEligibleEvaluations}; the rest
     * are here so the screen can say why they are not. An applicant whose only
     * evaluation was still being assessed used to meet an empty list — and, with
     * nothing else on the page, took the professor route that needs none.
     */
    public List<EvaluationChoice> getEvaluationChoices(Integer applicantId) {
        Map<Long, String> spent = getSpentEvaluations(applicantId);
        return academicRequestService.findSubmittedEvaluations(applicantId).stream()
                .map(evaluation -> {
                    EvaluationSummary summary = academicRequestService.summarize(evaluation);
                    return new EvaluationChoice(summary,
                            spent.containsKey(summary.evaluationId()),
                            spent.get(summary.evaluationId()),
                            academicRequestService.unusableReason(evaluation));
                })
                // ที่ใช้ได้ขึ้นก่อน ในแต่ละกลุ่มยังเรียงใหม่สุดก่อนตามเดิม
                .sorted(java.util.Comparator.comparing(choice -> !choice.usable()))
                .toList();
    }

    /**
     * ผลประเมินการสอนของผู้ยื่นที่ผูกกับคำร้องขอตำแหน่งไปแล้ว → รหัสคำร้องที่ผูกไว้
     *
     * <p>ผลประเมินหนึ่งฉบับเป็นของคำร้องหนึ่งฉบับ ทุกสถานะ (Flow ข้อ 30: เอกสารประเมินการสอน 1 ชุด)
     * Flow ไม่มีการปฏิเสธที่ทำให้คำร้องตกไป มีแต่ตีกลับให้แก้ จึงไม่มีสถานะไหนคืนสิทธิ์
     * ผลประเมินจะว่างลงได้ทางเดียวคือแบบร่างถูกลบ
     *
     * <p>กุญแจคือตัวผลประเมิน ไม่ใช่วิชา ใช้วิชาเดิมยื่นตำแหน่งใหม่ได้ถ้าประเมินใหม่พร้อมเนื้อหาใหม่
     * รหัสคำร้องแนบมาด้วยเพื่อให้หน้าจอบอกได้ว่าคำร้องไหนใช้ไป
     */
    public Map<Long, String> getSpentEvaluations(Integer applicantId) {
        if (applicantId == null) {
            return Map.of();
        }
        Map<Long, String> spent = new LinkedHashMap<>();
        for (PositionRequest request : requestRepository.findLinkedByApplicant(applicantId)) {
            spent.put(request.getLinkedEvaluation().getId(),
                    request.getRequestCode() != null ? request.getRequestCode()
                            : String.valueOf(request.getId()));
        }
        return spent;
    }

    /** Whether this applicant may still start a request on this evaluation. */
    public boolean canUseEvaluation(Integer applicantId, Long evaluationId) {
        if (evaluationId == null) {
            return false;
        }
        return getEligibleEvaluations(applicantId).stream()
                .anyMatch(evaluation -> evaluationId.equals(evaluation.getId()));
    }

    // ================== Rank rules ==================

    public static final String ERROR_RANK_NOT_HIGHER = "rank_not_higher";
    public static final String ERROR_EVALUATION_REQUIRED = "evaluation_required";
    public static final String ERROR_POSITION_MISMATCH = "position_mismatch";
    public static final String ERROR_LEVEL_BELOW_RANK = "level_below_rank";
    public static final String ERROR_EVALUATION_UNUSABLE = "evaluation_unusable";
    public static final String ERROR_ALREADY_PROFESSOR = "already_professor";

    /** ศาสตราจารย์เป็นตำแหน่งสูงสุด — ยื่นคำร้องขอตำแหน่งใหม่ไม่ได้ ดูจากโปรไฟล์ */
    public boolean holdsHighestRank(UserDtls applicant) {
        return AcademicRankPolicy.holdsHighestRank(applicant);
    }

    /**
     * ปัญหาของการเริ่มคำร้องด้วยผลประเมินฉบับนี้ ถ้ามี — ตำแหน่งที่ผลประเมินขอต้องสูงกว่าตำแหน่งปัจจุบัน
     * ซึ่งยึดตามเอกสารที่ 1 ของผลประเมินก่อนโปรไฟล์
     */
    public Optional<String> rankProblemForEvaluation(UserDtls applicant, Long evaluationId) {
        EvaluationSummary summary = academicRequestRepository.findById(evaluationId)
                .map(academicRequestService::summarize)
                .orElse(null);
        if (AcademicRankPolicy.holdsHighestRank(applicant, summary == null ? null : summary.currentPosition())) {
            return Optional.of(ERROR_ALREADY_PROFESSOR);
        }
        if (summary == null || summary.targetRank() == null) {
            return Optional.empty();
        }
        AcademicRank current = AcademicRankPolicy.currentRank(applicant, summary.currentPosition());
        return AcademicRankPolicy.rankViolation(current, summary.targetRank())
                .map(message -> ERROR_RANK_NOT_HIGHER);
    }

    /**
     * ปัญหาของการเริ่มคำร้องขอ ศ. โดยไม่ใช้ผลประเมิน ถ้ามี — ยังไม่มีเอกสาร จึงดูตำแหน่งจากโปรไฟล์
     *
     * <p>ใช้ได้ทั้ง รศ. (วิธีปกติ) และอาจารย์/ผศ. ที่ข้ามขั้น (วิธีพิเศษ) เพราะขอ ศ. ไม่ต้องประเมินการสอน
     */
    public Optional<String> rankProblemForProfessor(UserDtls applicant) {
        if (AcademicRankPolicy.holdsHighestRank(applicant)) {
            return Optional.of(ERROR_ALREADY_PROFESSOR);
        }
        AcademicRank current = AcademicRankPolicy.currentRank(applicant);
        Optional<String> violation = AcademicRankPolicy.rankViolation(current, AcademicRank.PROFESSOR)
                .map(message -> ERROR_RANK_NOT_HIGHER);
        if (violation.isPresent()) {
            return violation;
        }
        if (AcademicRankPolicy.requiresTeachingEvaluation(current, AcademicRank.PROFESSOR)) {
            return Optional.of(ERROR_EVALUATION_REQUIRED);
        }
        return Optional.empty();
    }

    /**
     * ตรวจกติกาตำแหน่งอีกรอบตอนยื่น — แบบร่างค้างได้เป็นสัปดาห์ และข้อมูลเก่าอาจสร้างก่อนมีกติกานี้
     *
     * <ul>
     * <li>ขอ ผศ./รศ. ต้องมีผลประเมินการสอน ขอ ศ. ไม่ต้องมี (1669/2569 ข้อ ๖)
     * <li>ตำแหน่งที่ขอต้องตรงกับที่ผลประเมินระบุ และระดับผลต้องถึงเกณฑ์ของตำแหน่งนั้น (ข้อ ๙.๔)
     * <li>ผลประเมินยังใช้ได้ ณ วันที่ยื่น — ร่างที่สร้างตอนผลยังไม่หมดอายุอาจมายื่นหลังครบ 3 ปีแล้ว (ข้อ ๗)
     * <li>ต้องสูงกว่าตำแหน่งปัจจุบัน — ยึดเอกสารแรกที่กรอก: เอกสารที่ 1 ของผลประเมิน แล้วเอกสารที่ 1
     * ของคำร้องนี้ แล้วค่อยโปรไฟล์
     * </ul>
     *
     * ศาสตราจารย์ยื่นไม่ได้เลยไม่ว่าขอตำแหน่งอะไร คำร้องอื่นที่ยังไม่รู้ว่าขอตำแหน่งอะไรจะถูกปล่อยผ่าน เพราะยังไม่มีอะไรให้เทียบ
     *
     * @return error code สำหรับหน้าจอ ถ้าผิดกติกา
     */
    public Optional<String> submissionProblem(PositionRequest request) {
        EvaluationSummary evaluation = academicRequestService.summarize(request.getLinkedEvaluation());
        Map<String, String> doc1 = getLatestDocumentData(request.getId(), 1);
        if (AcademicRankPolicy.holdsHighestRank(request.getApplicant(),
                evaluation == null ? null : evaluation.currentPosition(),
                doc1 == null ? null : doc1.get("current_position"))) {
            return Optional.of(ERROR_ALREADY_PROFESSOR);
        }
        AcademicRank target = AcademicRank.of(request.getTargetPosition());
        if (target == null) {
            return Optional.empty();
        }
        AcademicRank current = AcademicRankPolicy.currentRank(request.getApplicant(),
                evaluation == null ? null : evaluation.currentPosition(),
                doc1 == null ? null : doc1.get("current_position"));
        // ศ. ต้องใช้ผลประเมินหรือไม่ขึ้นกับตำแหน่งปัจจุบัน จึงต้องรู้ current ก่อนตัดสิน
        if (evaluation == null && AcademicRankPolicy.requiresTeachingEvaluation(current, target)) {
            return Optional.of(ERROR_EVALUATION_REQUIRED);
        }
        if (evaluation != null && evaluation.targetRank() != null && evaluation.targetRank() != target) {
            return Optional.of(ERROR_POSITION_MISMATCH);
        }
        // ผลที่ตัดสินก่อนมีกติกา 1669/2569 ข้อ ๙.๔ อาจค้างสถานะผ่านไว้ทั้งที่ระดับไม่ถึงเกณฑ์ของตำแหน่งที่ขอ
        if (evaluation != null && Doc7Scoring.fallsShortOf(evaluation.resultLevel(), target)) {
            return Optional.of(ERROR_LEVEL_BELOW_RANK);
        }
        if (evaluation != null && !academicRequestService.isUsableEvaluation(request.getLinkedEvaluation())) {
            return Optional.of(ERROR_EVALUATION_UNUSABLE);
        }
        return AcademicRankPolicy.rankViolation(current, target).map(message -> ERROR_RANK_NOT_HIGHER);
    }

    /** ชื่อช่องตำแหน่งที่ขอในแบบฟอร์มฝั่งผู้ยื่น — เอกสาร 1, 6 ใช้ชื่อหนึ่ง เอกสาร 2, 4 อีกชื่อหนึ่ง */
    private static final List<String> TARGET_POSITION_FIELDS = List.of("target_position", "request_position");

    /**
     * ตรึงตำแหน่งที่ขอในข้อมูลฟอร์มให้เป็นตำแหน่งของคำร้อง — ตำแหน่งมาจากผลประเมินการสอน
     * (หรือเป็น ศ.) ตั้งแต่ตอนสร้าง ช่องในฟอร์มแค่แสดง ไม่ใช่ที่เปลี่ยนมัน
     */
    public void pinTargetPosition(PositionRequest request, Map<String, String> formData) {
        String target = request.getTargetPosition();
        if (target == null || formData == null) {
            return;
        }
        for (String field : TARGET_POSITION_FIELDS) {
            if (formData.containsKey(field)) {
                formData.put(field, target);
            }
        }
    }

    /** JSON-in/JSON-out variant of {@link #pinTargetPosition} for the auto-draft endpoint. */
    public String pinTargetPositionInJson(Long requestId, String jsonData) {
        PositionRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getTargetPosition() == null || jsonData == null) {
            return jsonData;
        }
        try {
            Map<String, String> submitted = objectMapper.readValue(jsonData,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
            pinTargetPosition(request, submitted);
            return objectMapper.writeValueAsString(submitted);
        } catch (Exception e) {
            return jsonData;
        }
    }

    public static final String FACULTY = "วิทยาลัยการคอมพิวเตอร์";
    public static final String UNIVERSITY = "มหาวิทยาลัยขอนแก่น";

    /** เอกสารที่มีช่องตำแหน่งปัจจุบัน ({@code current_position}) */
    private static final Set<Integer> CURRENT_POSITION_DOCS = Set.of(1, 2, 4, 6);

    /**
     * ช่องในเอกสารของผู้ยื่นที่ระบบรู้ค่าอยู่แล้ว — ดึงมาใส่และล็อก ผู้ยื่นแก้เองไม่ได้
     *
     * <ul>
     *   <li>ข้อมูลบุคลากร: คำนำหน้า ชื่อ และตำแหน่งปัจจุบัน (เหมือนเอกสารที่ 1–2 ของเฟส 1)
     *       — เอกสารที่ 6 ใช้ช่อง {@code applicant_title} ส่วนเอกสารที่ 9 ไม่มีช่องคำนำหน้า</li>
     *   <li>หน่วยงาน: คณะ มหาวิทยาลัย (เอกสารที่ 1) และสังกัด (เอกสารที่ 4)</li>
     *   <li>เอกสารที่ 1 เป็นต้นฉบับ: สาขาวิชาในเอกสารที่ 2 ตามเอกสารที่ 1</li>
     *   <li>งานสอนจากผลประเมินการสอน ({@link #teachingHistoryRows}) — เฉพาะรายวิชาและภาค/ปี
     *       ระดับและชั่วโมงผู้ยื่นกรอกเอง</li>
     * </ul>
     *
     * <p>ช่องที่หาค่าไม่ได้ไม่อยู่ในผลลัพธ์ จึงยังกรอกเองได้ ตำแหน่งที่ขอตรึงแยกไว้ที่
     * {@link #pinTargetPosition}
     */
    public Map<String, String> lockedFields(PositionRequest request, int documentType) {
        if (request == null || !APPLICANT_DOCS.contains(documentType)) {
            return Map.of();
        }
        Map<String, String> profile = AcademicRequestService.profileFieldsOf(request.getApplicant());
        Map<String, String> fields = new LinkedHashMap<>();
        String title = profile.get("title");
        if (title != null && documentType != 9) {
            fields.put(documentType == 6 ? "applicant_title" : "title", title);
        }
        putIfPresent(fields, "applicant_name", profile.get("applicant_name"));
        if (CURRENT_POSITION_DOCS.contains(documentType)) {
            putIfPresent(fields, "current_position", profile.get("current_position"));
        }
        switch (documentType) {
            case 1 -> {
                fields.put("faculty", FACULTY);
                fields.put("university", UNIVERSITY);
                fields.putAll(teachingHistoryRows(request));
            }
            case 2 -> {
                Map<String, String> doc1 = getLatestDocumentData(request.getId(), 1);
                putIfPresent(fields, "major", doc1 == null ? null : doc1.get("major"));
                putIfPresent(fields, "status", evaluationEmployeeType(request));
            }
            case 4 -> fields.put("affiliation", FACULTY + " " + UNIVERSITY);
            default -> {
            }
        }
        return fields;
    }

    /** ประเภทบุคลากรที่เลือกในเอกสารที่ 1 ของผลประเมินการสอนที่ผูกกับคำร้องนี้ */
    private String evaluationEmployeeType(PositionRequest request) {
        if (request.getLinkedEvaluation() == null) {
            return null;
        }
        Map<String, String> evaluationDoc1 = academicRequestService
                .getLatestDocumentData(request.getLinkedEvaluation().getId(), 1);
        return evaluationDoc1 == null ? null : evaluationDoc1.get("employee_type");
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (!isBlank(value)) {
            fields.put(key, value.trim());
        }
    }

    /** เขียนทับช่องที่ล็อกด้วยค่าจากระบบ — ใช้ทุกทางที่ผู้ยื่นบันทึกเอกสาร */
    public void pinLockedFields(PositionRequest request, int documentType, Map<String, String> formData) {
        if (formData != null) {
            formData.putAll(lockedFields(request, documentType));
        }
    }

    /** JSON-in/JSON-out variant of {@link #pinLockedFields} for the auto-draft endpoint. */
    public String pinLockedFieldsInJson(PositionRequest request, int documentType, String jsonData) {
        Map<String, String> fields = lockedFields(request, documentType);
        if (fields.isEmpty() || jsonData == null) {
            return jsonData;
        }
        try {
            Map<String, String> submitted = objectMapper.readValue(jsonData,
                    new TypeReference<Map<String, String>>() {
                    });
            submitted.putAll(fields);
            return objectMapper.writeValueAsString(submitted);
        } catch (Exception e) {
            return jsonData;
        }
    }

    /** บรรทัดผลงานในแบบ ก.พ.ว. มข.๐๓: งานวิจัย ผลงานลักษณะอื่น ตำรา ของทุกระดับตำแหน่ง */
    private static final Pattern WORK_LINE = Pattern.compile("^(asst|assoc|prof)_(research|other|book)_working_\\d+$");

    /**
     * ผลงานในเอกสารที่ 1 ของคำร้องนี้ที่ผู้ยื่นเคยใช้ยื่นคำร้องก่อนหน้าแล้ว — ใช้ซ้ำไม่ได้
     *
     * <p>ผลงานที่เลือกจากรายการถูกกันด้วยรหัสผลงานอยู่แล้ว ตัวนี้ครอบผลงานที่พิมพ์เองหรือแก้ข้อความ
     * โดยเทียบกับผลงานในคำร้องอื่นของผู้ยื่นคนเดียวกันที่พ้นแบบร่างแล้ว และชื่อเรื่องของผลงานที่เคย
     * เลือกจากรายการไปแล้วในคำร้องอื่น ({@link WorkReuseMatcher})
     *
     * @return ข้อความผลงานที่ซ้ำ ตามที่พิมพ์ในคำร้องนี้ — ว่างเมื่อไม่ซ้ำ
     */
    public List<String> reusedWorks(PositionRequest request) {
        if (request == null || request.getApplicant() == null) {
            return List.of();
        }
        List<String> mine = workLines(getLatestDocumentData(request.getId(), 1));
        if (mine.isEmpty()) {
            return List.of();
        }
        List<String> earlier = new java.util.ArrayList<>();
        for (PositionRequest other : requestRepository.findByApplicantId(request.getApplicant().getId())) {
            if (!other.getId().equals(request.getId()) && other.getCurrentStatus() != PositionRequestStatus.DRAFT) {
                earlier.addAll(workLines(getLatestDocumentData(other.getId(), 1)));
            }
        }
        // ผลงานที่ผูกกับคำร้องนี้เอง (คำร้องที่ถูกส่งกลับมาแก้) ไม่นับว่าใช้ไปแล้ว
        Set<Long> ownLinks = new java.util.HashSet<>();
        publicationLinkRepository.findByRequestId(request.getId()).forEach(l -> ownLinks.add(l.getPublicationId()));
        List<Long> spentIds = publicationLinkRepository.findSpentPublicationIds(
                request.getApplicant().getId(), Set.of(PositionRequestStatus.DRAFT)).stream()
                .filter(id -> !ownLinks.contains(id))
                .toList();
        List<String> spentTitles = spentIds.isEmpty() ? List.of()
                : publicationRepository.findAllById(spentIds).stream()
                        .map(com.ecom.external.model.ScopusPublication::getTitle)
                        .toList();
        if (earlier.isEmpty() && spentTitles.isEmpty()) {
            return List.of();
        }
        return mine.stream().filter(line -> WorkReuseMatcher.reused(line, earlier, spentTitles)).toList();
    }

    private static List<String> workLines(Map<String, String> doc1) {
        if (doc1 == null) {
            return List.of();
        }
        return doc1.entrySet().stream()
                .filter(e -> WORK_LINE.matcher(e.getKey()).matches() && !isBlank(e.getValue()))
                .map(e -> e.getValue().trim())
                .toList();
    }

    private static final Pattern BE_YEAR = Pattern.compile("(25\\d\\d)");

    /**
     * แถวงานสอน (๓.๑ ของแบบ ก.พ.ว. มข. 03) เท่าที่ระบบรู้ — รายวิชาจากคำร้องประเมินผลการสอนของผู้ยื่น
     * ย้อนหลัง ๓ ปีการศึกษา ผลประเมินที่ผูกกับคำร้องนี้ขึ้นก่อน ไม่ซ้ำรายวิชาและภาคเดียวกัน
     *
     * <p>ระบบไม่เก็บระดับ (ป.ตรี/บัณฑิต) และชั่วโมงต่อสัปดาห์ ช่องพวกนั้นผู้ยื่นกรอกเอง
     *
     * @return {@code teaching_subject_N} / {@code teaching_semester_N} เริ่มที่ N = 1 — ว่างเมื่อไม่มีข้อมูล
     */
    public Map<String, String> teachingHistoryRows(PositionRequest request) {
        Map<String, String> rows = new LinkedHashMap<>();
        if (request == null || request.getApplicant() == null) {
            return rows;
        }
        List<AcademicRequest> evaluations = new java.util.ArrayList<>();
        if (request.getLinkedEvaluation() != null) {
            evaluations.add(request.getLinkedEvaluation());
        }
        academicRequestService.findByApplicant(request.getApplicant().getId()).stream()
                .filter(e -> e.getCurrentStatus() != RequestStatus.DRAFT)
                .filter(e -> evaluations.stream().noneMatch(x -> x.getId().equals(e.getId())))
                .forEach(evaluations::add);
        if (evaluations.isEmpty()) {
            return rows;
        }

        Map<Long, EvaluationSummary> summaries = academicRequestService.summarizeAll(evaluations);
        int oldestYear = LocalDate.now().getYear() + 543 - 3;
        Set<String> seen = new java.util.HashSet<>();
        int n = 0;
        for (AcademicRequest evaluation : evaluations) {
            EvaluationSummary s = summaries.get(evaluation.getId());
            if (s == null || (isBlank(s.courseCode()) && isBlank(s.courseName()))) {
                continue;
            }
            String semester = semesterLabel(s);
            Integer year = beYear(semester != null ? semester : s.academicYear());
            if (year != null && year < oldestYear) {
                continue;
            }
            String subject = (nullToEmpty(s.courseCode()) + " " + nullToEmpty(s.courseName())).trim();
            if (!seen.add(subject + "|" + nullToEmpty(semester))) {
                continue;
            }
            n++;
            rows.put("teaching_subject_" + n, subject);
            if (semester != null) {
                rows.put("teaching_semester_" + n, semester);
            }
        }
        return rows;
    }

    /** "1/2568" — ภาคจากผลประเมิน ต่อปีการศึกษาเมื่อภาคไม่มีปีติดมา */
    private static String semesterLabel(EvaluationSummary s) {
        String semester = isBlank(s.semester()) ? null : s.semester().trim();
        String year = isBlank(s.academicYear()) ? null : s.academicYear().trim();
        if (semester == null) {
            return year;
        }
        return semester.contains("/") || year == null ? semester : semester + "/" + year;
    }

    private static Integer beYear(String text) {
        Matcher m = text == null ? null : BE_YEAR.matcher(text);
        return m != null && m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    // ================== Request CRUD ==================

    /**
     * สร้างแบบร่างคำร้อง ตำแหน่งที่ขอดึงมาจากผลประเมินการสอน (เอกสารที่ 1 ช่อง chk1/chk2)
     *
     * <p>ผลประเมินที่ผูกไว้แล้วจะชนกับ UNIQUE ของ {@code linked_evaluation_id} และได้
     * {@link org.springframework.dao.DataIntegrityViolationException} — ผู้เรียกต้องรับไว้
     */
    @Transactional
    public PositionRequest createDraftRequest(UserDtls applicant, Long linkedEvaluationId) {
        AcademicRequest evaluation = linkedEvaluationId == null ? null
                : academicRequestRepository.findById(linkedEvaluationId).orElse(null);
        AcademicRank target = evaluation == null ? null
                : academicRequestService.summarize(evaluation).targetRank();
        return newDraft(applicant, evaluation, target);
    }

    /** สร้างแบบร่างคำร้องขอ ศ. — ไม่ต้องใช้ผลประเมินการสอน */
    @Transactional
    public PositionRequest createProfessorDraftRequest(UserDtls applicant) {
        return newDraft(applicant, null, AcademicRank.PROFESSOR);
    }

    private PositionRequest newDraft(UserDtls applicant, AcademicRequest evaluation, AcademicRank target) {
        PositionRequest request = new PositionRequest();
        request.setApplicant(applicant);
        request.setCurrentStatus(PositionRequestStatus.DRAFT);
        request.setLinkedEvaluation(evaluation);
        if (target != null) {
            request.setTargetPosition(target.thaiLabel());
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

    /**
     * ค้นหาคำร้องขอตำแหน่งจากชื่อหรืออีเมลผู้ยื่น
     *
     * <p>คำค้นสั้นกว่า {@link SearchQueryNormalizer#MIN_QUERY_LENGTH} ตัวอักษร
     * คืนลิสต์ว่าง ไม่ใช่ทุกแถว — อักษรไทยตัวเดียวเป็นสับสตริงของข้อมูลแทบทั้งหมด
     */
    public List<PositionRequest> searchByNameOrEmail(String keyword) {
        String pattern = SearchQueryNormalizer.likePattern(keyword);
        if (pattern == null) {
            return List.of();
        }
        return requestRepository.searchByNameOrEmail(pattern);
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

                // Delete entire position request folder from disk — attachments live under
                // position/{id}, generated Phase 2 documents under academic/position/{id}
                try {
                    for (java.nio.file.Path requestDir : List.of(
                            uploadPaths.dir("position", String.valueOf(requestId)),
                            uploadPaths.dir("academic", "position", String.valueOf(requestId)))) {
                        if (java.nio.file.Files.exists(requestDir)) {
                            org.springframework.util.FileSystemUtils.deleteRecursively(requestDir);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not delete position request folder for #{}: {}", requestId, e.getMessage());
                }

                // Delete document edit logs
                List<PositionDocumentEditLog> editLogs = editLogRepository.findByRequestOrderByEditedAtDescIdDesc(req);
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
     * ตรวจสอบว่าคำร้องขอตำแหน่งฉบับร่างเป็นแบบร่างว่างเปล่าหรือไม่
     */
    public boolean isDraftEmpty(PositionRequest request) {
        if (request == null || request.getCurrentStatus() != PositionRequestStatus.DRAFT) {
            return false;
        }
        // 1. มีไฟล์แนบหรือไม่
        if (attachmentRepository.countActiveByRequestId(request.getId()) > 0) {
            return false;
        }
        // 2. ตรวจสอบเอกสาร (PositionDocument)
        List<PositionDocument> docs = getDocuments(request.getId());
        for (PositionDocument doc : docs) {
            if (doc.getGeneratedFilePath() != null && !doc.getGeneratedFilePath().isBlank()) {
                return false;
            }
            if (hasNonEmptyDocumentData(doc.getJsonData())) {
                return false;
            }
        }
        // 3. ตรวจสอบว่ามีการลงนามหรือขอลงนามหรือไม่
        List<com.ecom.academic.model.SignatureRequest> envelopes = signatureRequestRepository
                .findByModuleAndRequestIdOrderByDocumentTypeAsc(
                        com.ecom.academic.model.SignatureModule.POSITION, request.getId());
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
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(jsonData, Map.class);
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
        // ข้อ 22 NO → 19: ส่งกลับให้แก้แล้ว เรื่องเดินต่อไม่ได้จนกว่าจะลงนามใหม่ครบ
        // (การส่งกลับอีกรอบ — REVISION_REQUESTED — ยังทำได้เสมอ)
        if (oldStatus != newStatus && newStatus != PositionRequestStatus.REVISION_REQUESTED) {
            String blocker = resignBlocker(requestId);
            if (blocker != null) {
                throw new IllegalStateException(blocker);
            }
        }
        request.setCurrentStatus(newStatus);
        request = requestRepository.save(request);

        addStatusHistory(request, oldStatus, newStatus, changedBy, note);

        // Send email notification to applicant if requested
        if (sendNotify) {
            Long notifyId = request.getId();
            afterCommit.run(() -> emailService.sendStatusChangeEmail(notifyId, oldStatus, newStatus, note));
        }

        return request;
    }

    /**
     * ผู้ยื่นเป็นข้าราชการ (ช่องสถานะในเอกสารที่ 2 หรือเอกสารที่ 1 ของผลประเมินการสอนที่ผูกไว้) — ข้าราชการใช้ข้อบังคับ มข.
     * ฉบับข้าราชการพลเรือนในสถาบันอุดมศึกษา พ.ศ. 2565 ไม่ใช่ฉบับพนักงาน พ.ศ. 2569 ที่ระบบตรวจตาม ไม่มีข้อมูลถือเป็นพนักงาน
     */
    public boolean isCivilServant(PositionRequest request) {
        if (request == null || request.getId() == null) {
            return false;
        }
        Map<String, String> doc2 = getLatestDocumentData(request.getId(), 2);
        String status = doc2 == null ? null : doc2.get("status");
        if (status == null || status.isBlank()) {
            status = evaluationEmployeeType(request);
        }
        return "ข้าราชการ".equals(status == null ? null : status.trim());
    }

    /**
     * วันที่ที่บันทึกพร้อมการเปลี่ยนสถานะ — ใช้เฉพาะสถานะที่ต้องมี (ช่องอื่นปล่อย null)
     *
     * @param collegeResolution    วันมติกรรมการประจำวิทยาลัยฯ (จำเป็นเมื่อ {@code COLLEGE_APPROVED})
     * @param correctionsReceived  มติให้แก้ไข: วันที่ได้รับเอกสารแก้ไขครบ (ไม่บังคับ)
     * @param councilResolution    วันมติสภามหาวิทยาลัย (จำเป็นเมื่อบันทึกผลสภา)
     * @param councilAcknowledged  วันที่ผู้ขอรับทราบมติ (จำเป็นเมื่อสภาไม่อนุมัติ เพื่อนับ 90 วัน)
     * @param appealReceived       ขอทบทวน: วันที่หน่วยงานของส่วนงานรับเรื่อง (จำเป็น — ใช้นับ 90 วันและเป็นวันสภารับเรื่อง)
     * @param appealEndorsed       ขอทบทวน: วันที่คณะกรรมการประจำส่วนงานเห็นชอบให้เสนอมหาวิทยาลัย (จำเป็น)
     */
    public record StatusDates(java.time.LocalDate collegeResolution, java.time.LocalDate correctionsReceived,
            java.time.LocalDate councilResolution, java.time.LocalDate councilAcknowledged,
            java.time.LocalDate appealReceived, java.time.LocalDate appealEndorsed) {

        public static final StatusDates NONE = new StatusDates(null, null, null, null);

        public StatusDates(java.time.LocalDate collegeResolution, java.time.LocalDate correctionsReceived,
                java.time.LocalDate councilResolution, java.time.LocalDate councilAcknowledged) {
            this(collegeResolution, correctionsReceived, councilResolution, councilAcknowledged, null, null);
        }

        /** วันที่ของการขอทบทวน (ข้อบังคับ 2569 ข้อ 35) */
        public static StatusDates appeal(java.time.LocalDate received, java.time.LocalDate endorsed) {
            return new StatusDates(null, null, null, null, received, endorsed);
        }
    }

    /**
     * เปลี่ยนสถานะพร้อมบันทึกวันที่ตามประกาศ มข. 1670/2569 (วันสภารับเรื่อง) และข้อบังคับ 2569 ข้อ 35 (ขอทบทวน)
     */
    @Transactional
    public PositionRequest updateStatus(Long requestId, PositionRequestStatus newStatus, UserDtls changedBy,
            String note, StatusDates dates) {
        PositionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + requestId));
        java.time.LocalDate today = java.time.LocalDate.now();
        StatusDates d = dates == null ? StatusDates.NONE : dates;
        switch (newStatus) {
            case COLLEGE_APPROVED -> {
                requireDate(d.collegeResolution(), "วันที่คณะกรรมการประจำวิทยาลัยฯ มีมติ", today);
                if (d.correctionsReceived() != null) {
                    requireDate(d.correctionsReceived(), "วันที่ได้รับเอกสารแก้ไขครบตามมติ", today);
                    if (d.correctionsReceived().isBefore(d.collegeResolution())) {
                        throw new IllegalStateException("วันที่ได้รับเอกสารแก้ไขต้องไม่ก่อนวันที่มีมติ");
                    }
                }
                request.setCollegeResolutionDate(d.collegeResolution());
                request.setCorrectionsReceivedDate(d.correctionsReceived());
            }
            case COUNCIL_APPROVED, COUNCIL_REJECTED -> {
                requireDate(d.councilResolution(), "วันที่สภามหาวิทยาลัยมีมติ", today);
                if (newStatus == PositionRequestStatus.COUNCIL_REJECTED || d.councilAcknowledged() != null) {
                    requireDate(d.councilAcknowledged(), "วันที่ผู้ขอรับทราบมติ", today);
                    if (d.councilAcknowledged().isBefore(d.councilResolution())) {
                        throw new IllegalStateException("วันที่รับทราบมติต้องไม่ก่อนวันที่สภามหาวิทยาลัยมีมติ");
                    }
                }
                request.setCouncilResolutionDate(d.councilResolution());
                request.setCouncilAcknowledgedDate(d.councilAcknowledged());
            }
            case APPEAL_SUBMITTED -> {
                // ข้อ 35 — ยื่นที่ส่วนงาน (วันรับเรื่อง = วันสภารับเรื่อง) แล้วคณะกรรมการประจำส่วนงานเห็นชอบก่อนเสนอมหาวิทยาลัย
                requireDate(d.appealReceived(), "วันที่ส่วนงานรับเรื่องขอทบทวน", today);
                requireDate(d.appealEndorsed(), "วันที่คณะกรรมการประจำส่วนงานเห็นชอบให้เสนอขอทบทวน", today);
                if (d.appealEndorsed().isBefore(d.appealReceived())) {
                    throw new IllegalStateException("วันที่คณะกรรมการประจำส่วนงานเห็นชอบต้องไม่ก่อนวันที่รับเรื่องขอทบทวน");
                }
                // 90 วันนับถึงวันที่ส่วนงานรับเรื่อง ไม่ใช่วันที่เจ้าหน้าที่มาบันทึก
                String problem = CouncilTimeline.appealProblem(request, getStatusHistory(requestId),
                        d.appealReceived());
                if (problem != null) {
                    throw new IllegalStateException(problem);
                }
                if (note == null || note.isBlank()) {
                    throw new IllegalArgumentException("กรุณาระบุเหตุผลทางวิชาการที่ขอทบทวน (ข้อบังคับ 2569 ข้อ 35)");
                }
                request.setAppealReceivedDate(d.appealReceived());
                request.setAppealEndorsedDate(d.appealEndorsed());
            }
            default -> {
            }
        }
        requestRepository.save(request);
        return updateStatus(requestId, newStatus, changedBy, note, true);
    }

    private static void requireDate(java.time.LocalDate date, String what, java.time.LocalDate today) {
        if (date == null) {
            throw new IllegalStateException("กรุณาระบุ" + what);
        }
        if (date.isAfter(today)) {
            throw new IllegalStateException(what + "ต้องไม่เป็นวันในอนาคต");
        }
    }

    @Transactional
    public void autoUpdateStatusByDocument(Long requestId, int documentType, UserDtls changedBy, String jsonData,
            boolean sendNotify) {
        PositionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("ไม่พบคำร้อง ID: " + requestId));

        switch (documentType) {
            // เหลือเฉพาะเอกสารที่ 7 — เอกสารที่ 5 ย้ายไปเป็นของผู้ยื่นแล้ว การบันทึกเอกสารของ
            // ตัวเองไม่ควรเลื่อนสถานะคำร้องเป็น "ตรวจสอบเอกสาร" ให้ตัวเอง ส่วน 0 เป็นเลขที่ค้าง
            // จากตอนเอกสารยังเริ่มนับที่ 0 ไม่มีเอกสารเลขนี้อีกแล้ว
            case 7 -> {
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
        if (action == PositionDocumentEditLog.EditAction.DRAFT_SAVED) {
            // Autosave fires on every pause in typing; one row per burst is enough.
            PositionDocumentEditLog last = editLogRepository
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
        PositionDocumentEditLog log = new PositionDocumentEditLog();
        log.setRequest(request);
        log.setDocumentType(documentType);
        log.setDocumentLabel(label != null ? label : getDocLabel(documentType));
        log.setEditedBy(user);
        log.setAction(action);
        editLogRepository.save(log);
    }

    /** Edit-log document type for files that belong to the request rather than a numbered document. */
    public static final int REQUEST_FILES_DOC_TYPE = 0;

    /** Logs a change that isn't a form save (attachment, upload, re-sign…), with what it touched. */
    public void logDocumentChange(PositionRequest request, int documentType, String detail,
            UserDtls user, PositionDocumentEditLog.EditAction action) {
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
                applicant, PositionDocumentEditLog.EditAction.REVISION_SUBMITTED));
    }

    /** Autosaves by the same user on the same document within this window share one history row. */
    static final java.time.Duration DRAFT_LOG_MERGE_WINDOW = java.time.Duration.ofMinutes(10);

    private static boolean isSameUser(UserDtls a, UserDtls b) {
        return a != null && b != null && a.getId() != null && a.getId().equals(b.getId());
    }

    public List<PositionDocumentEditLog> getEditHistory(Long requestId) {
        PositionRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null) return List.of();
        return editLogRepository.findByRequestOrderByEditedAtDescIdDesc(request);
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

    /**
     * ข้อมูลฟอร์มของเอกสารประเภทหนึ่ง ของหลายคำร้องใน query เดียว — ฉบับแรกตามลำดับของ
     * {@link #getDocumentsByType} ที่มีข้อมูล หน้ารายการเคยเรียก getDocumentsByType ทีละคำร้อง
     * คำร้องที่ยังไม่มีเอกสารนี้จะไม่อยู่ใน map
     */
    public Map<Long, Map<String, String>> documentDataFor(java.util.Collection<Long> requestIds, int documentType) {
        Map<Long, Map<String, String>> result = new java.util.HashMap<>();
        List<Long> ids = requestIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        for (int from = 0; from < ids.size(); from += AcademicRequestService.IN_CLAUSE_CHUNK) {
            List<Long> chunk = ids.subList(from, Math.min(from + AcademicRequestService.IN_CLAUSE_CHUNK, ids.size()));
            for (Object[] row : documentRepository.findJsonData(chunk, documentType)) {
                Long requestId = (Long) row[0];
                String json = (String) row[1];
                if (result.containsKey(requestId) || json == null || json.isBlank()) {
                    continue;
                }
                try {
                    result.put(requestId, objectMapper.readValue(json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            }));
                } catch (Exception e) {
                    log.warn("Could not read document {} of position request {}: {}", documentType, requestId,
                            e.getMessage());
                }
            }
        }
        return result;
    }

    /**
     * เขียนเลขที่หนังสือและวันที่ลงทุกแถวของเอกสารฉบับนี้ หลังลงนามครบแล้ว
     *
     * <p>คู่แฝดของ {@code AcademicRequestService.saveOfficeFieldsAcrossCopies} — สองเฟส
     * ต้องเขียนแบบเดียวกัน เดิมฝั่งนี้เรียก {@link #saveDraft} ซึ่งเห็นว่ามีแถวที่ส่งแล้วอยู่
     * จึง<em>เปิดแถวร่างใหม่</em>ขึ้นมาอีกแถวตามกติกาของมัน ("ไม่แตะแถวที่ส่งแล้ว") ผลคือ
     * ข้อมูลชุดเดียวกันแตกเป็นสองแถว แถวที่ส่งแล้วไม่เคยมีเลขที่หนังสือ และการถามว่า
     * "ออกเลขหรือยัง" จากแถวที่ไม่ใช่ร่างจะได้คำตอบผิดเสมอ
     *
     * <p>กติกา "ไม่แตะแถวที่ส่งแล้ว" ถูกต้องสำหรับการแก้เนื้อเอกสาร แต่ไม่ใช่กรณีนี้ —
     * เลขที่หนังสือเป็นของที่สารบรรณออกให้ <em>หลัง</em> ลงนาม จึงต้องลงบนฉบับจริง
     * ช่องอื่นไม่ถูกแตะ เพราะ {@link DocumentFieldOwnership#mergeOfficeFields} รับเฉพาะ
     * ช่องสารบรรณเท่านั้น
     *
     * @param submitted ค่าที่ส่งมาจากฟอร์ม (จะถูกกรองเหลือเฉพาะช่องสารบรรณ)
     * @return จำนวนแถวที่เขียนจริง
     */
    @Transactional
    public int saveOfficeFieldsAcrossCopies(PositionRequest request, int documentType,
            Map<String, String> submitted, String label) {
        return saveOfficeFieldsAcrossCopies(request, documentType, submitted, label, false);
    }

    /**
     * เหมือนข้างบน แต่เมื่อ {@code includeAdminFields} รับช่องของแอดมินทั้งหมดด้วย
     * ({@link DocumentFieldOwnership#lateFields}) — ใช้ตอนผู้ยื่นลงนามแล้วแต่ยังไม่ได้ส่งต่อ
     * ให้ผู้ลงนามคนถัดไป ดู {@link SignatureWorkflowService#awaitsMoreSigners}
     */
    @Transactional
    public int saveOfficeFieldsAcrossCopies(PositionRequest request, int documentType,
            Map<String, String> submitted, String label, boolean includeAdminFields) {
        List<PositionDocument> docs = getDocumentsByType(request.getId(), documentType);
        if (docs.isEmpty()) {
            // ยังไม่มีแถวเลย — เปิดแถวร่างให้ เพื่อไม่ให้เลขที่กรอกไว้หายไปเฉย ๆ
            Map<String, String> merged = DocumentFieldOwnership.mergeOfficeFields(
                    SignatureModule.POSITION, documentType, submitted, null, includeAdminFields);
            saveDraft(request, documentType, writeOfficeJson(merged), label, "ADMIN");
            return 1;
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        int written = 0;
        for (PositionDocument doc : docs) {
            Map<String, String> existing = null;
            String json = doc.getJsonData();
            if (json != null && !json.isBlank()) {
                try {
                    existing = mapper.readValue(json,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                            });
                } catch (Exception e) {
                    // แถวที่อ่านไม่ออกก็ยังลงเลขให้ได้ ดีกว่าปล่อยให้ทั้งชุดล้มเพราะแถวเดียว
                    log.warn("เอกสารตำแหน่ง {} มี JSON ที่อ่านไม่ออก เขียนช่องสารบรรณลงบนแมปใหม่แทน",
                            doc.getId());
                }
            }
            Map<String, String> merged = DocumentFieldOwnership.mergeOfficeFields(
                    SignatureModule.POSITION, documentType, submitted, existing, includeAdminFields);
            doc.setJsonData(writeOfficeJson(merged));
            // ไฟล์ที่สร้างไว้จากข้อมูลชุดก่อนไม่มีค่าที่เพิ่งกรอก — ทิ้งไป ทางสำรองจะได้สร้างใหม่
            doc.setGeneratedFilePath(null);
            // ทางนี้ใช้เฉพาะเอกสารที่ลงนามแล้ว ฉบับที่ลงนามคือฉบับจริง ไม่ใช่ร่าง — แถวที่ถูกส่งลงนาม
            // ผ่านบันทึกร่างอัตโนมัติยังติดธงร่างอยู่ กล่องเอกสารจึงค้างสีส้มแม้ออกเลขครบแล้ว
            doc.setIsDraft(false);
            documentRepository.save(doc);
            written++;
        }
        return written;
    }

    private String writeOfficeJson(Map<String, String> data) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("แปลงข้อมูลเอกสารเป็น JSON ไม่ได้", e);
        }
    }

    // ================== ประตูแก้ไขเอกสารของผู้ยื่น ==================

    /**
     * ผู้ยื่นแก้ไขเอกสารฉบับนี้ได้หรือไม่ — กติกาเดียวกับ
     * {@link AcademicRequestService#canApplicantEditDocument}
     *
     * <p>ก่อนหน้านี้เฟส 2 ไม่มีประตูนี้เลย ทั้ง GET และ POST ฝั่งผู้ยื่นไม่เช็กอะไรนอกจากความเป็น
     * เจ้าของคำร้อง ผู้ยื่นจึงแก้เอกสารที่ลงนามครบแล้วได้ ซึ่งทำให้เนื้อหาไม่ตรงกับแฮชที่ซองลายเซ็น
     * เก็บไว้ และลายเซ็นทุกใบในซองนั้นเป็นโมฆะย้อนหลังโดยไม่มีใครรู้
     *
     * <p>เรื่องนี้จำเป็นขึ้นมากเมื่อแอดมินแก้เอกสารของผู้ยื่นไม่ได้อีกต่อไป เพราะการส่งกลับมาให้
     * ผู้ยื่นแก้กลายเป็นทางเดียวที่เหลือ มันจึงต้องเปิดสิทธิ์ให้ได้จริง
     */
    public boolean canApplicantEditDocument(PositionRequest request, int documentType) {
        if (request == null || request.getCurrentStatus() == null) {
            return false;
        }
        if (!DocumentFieldOwnership.isApplicantDocument(SignatureModule.POSITION, documentType)) {
            return false;
        }
        // ก่อนดูว่าเป็นแบบร่าง — ผู้ยื่นลงนามเอกสารของตัวเองได้ตั้งแต่ยังไม่ส่งคำร้อง ถ้าเช็กทีหลัง
        // เอกสารที่ลงนามแล้วถูกเขียนทับได้ (บันทึกร่างอัตโนมัติที่ยิงช้า หรือ POST ตรง)
        if (isDocumentLockedForSigning(request.getId(), documentType)) {
            return false;
        }
        if (request.getCurrentStatus().isDraft()) {
            return true;
        }
        if (request.getCurrentStatus().isTerminal()) {
            return false;
        }
        return isRevisionRequested(request.getId(), documentType);
    }

    /** เอกสารกำลังเวียนลงนาม หรือลงนามครบแล้ว */
    public boolean isDocumentLockedForSigning(Long requestId, int documentType) {
        return !signatureRequestRepository
                .findBlockingEnvelopes(SignatureModule.POSITION, requestId, documentType)
                .isEmpty();
    }

    /**
     * ลงนามครบและสารบรรณออกเลขที่หนังสือกับวันที่ครบแล้ว — คู่แฝดของ
     * {@code AcademicRequestService.isOfficeIssued}
     *
     * <p>ไม่ได้ดูว่ายังเหลือผู้ลงนามที่ต้องส่งต่อหรือไม่ (เอกสารที่ 4 มีผู้ยื่นแล้วต่อด้วยหัวหน้าสาขา)
     * ผู้เรียกต้องตัดกรณีนั้นเองด้วย {@code SignatureWorkflowService.awaitsMoreSigners}
     */
    public boolean isOfficeIssued(Long requestId, int documentType) {
        if (!isSigningComplete(requestId, documentType)) {
            return false;
        }
        List<PositionDocument> docs = getDocumentsByType(requestId, documentType);
        return !docs.isEmpty() && docs.stream().allMatch(d -> DocumentCompleteness.officeFieldsIssued(
                SignatureModule.POSITION, documentType, d.getJsonData()));
    }

    /**
     * ลงนามครบแล้ว รอบเวียนลงนามปิดไปแล้ว
     *
     * <p>แคบกว่า {@link #isDocumentLockedForSigning} หนึ่งขั้น: ระหว่างเวียนลงนามห้ามขยับ
     * อะไรทั้งสิ้นเพราะคนถัดไปต้องเห็นของเดิม แต่เมื่อปิดรอบแล้วไม่มีคนถัดไปให้เข้าใจผิด
     * จึงเหลือช่องให้สารบรรณลงเลขที่หนังสือและวันที่ซึ่งออกให้หลังเอกสารเสร็จ
     */
    public boolean isSigningComplete(Long requestId, int documentType) {
        return signatureRequestRepository
                .findBlockingEnvelopes(SignatureModule.POSITION, requestId, documentType)
                .stream()
                .anyMatch(envelope -> envelope.getStatus() == SignatureRequestStatus.COMPLETED);
    }

    /**
     * เอกสารที่เจ้าหน้าที่ส่งกลับให้ผู้ยื่นแก้ และผู้ยื่นยังแก้/ลงนามใหม่ไม่เสร็จ → เหตุผล ("" ถ้าไม่ได้ระบุ)
     *
     * <p>สถานะคำร้องไม่เปลี่ยนตอนส่งกลับ (ยังเป็น "รับคำร้อง" ฯลฯ) ผู้ยื่นจึงรู้ได้จากรายการนี้เท่านั้น
     * ใช้กฎเดียวกับประตูแก้ไขเอกสาร: ลงนามใหม่แล้วเอกสารถูกล็อก จึงหลุดจากรายการเอง
     */
    public java.util.Map<Integer, String> sentBackDocuments(PositionRequest request) {
        java.util.Map<Integer, String> sentBack = new java.util.LinkedHashMap<>();
        if (request == null || request.getCurrentStatus() == null || request.getCurrentStatus().isDraft()) {
            return sentBack;
        }
        for (int docType : DocumentFieldOwnership.applicantDocuments(com.ecom.academic.model.SignatureModule.POSITION)) {
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
    public java.util.Map<Long, RevisionProgress.Summary> revisionProgressFor(List<PositionRequest> requests) {
        java.util.Map<Long, RevisionProgress.Summary> result = new java.util.HashMap<>();
        if (requests == null || requests.isEmpty()) {
            return result;
        }
        java.util.Set<Long> sentBack = new java.util.HashSet<>(documentRepository.findRequestIdsWithRevisionRequested(
                requests.stream().map(PositionRequest::getId).toList()));
        for (PositionRequest request : requests) {
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
    public RevisionProgress.Summary revisionProgress(PositionRequest request) {
        java.util.Map<Integer, RevisionProgress.SentBackDocument> result = new java.util.TreeMap<>();
        if (request == null || request.getCurrentStatus() == null || request.getCurrentStatus().isDraft()
                || request.getCurrentStatus().isTerminal()) {
            return new RevisionProgress.Summary(result);
        }
        var module = com.ecom.academic.model.SignatureModule.POSITION;
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
                submittedAt.put(type, doc.getRevisionSubmittedAt());
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

    /**
     * ผู้ยื่นกด "ยื่นการแก้ไข" — ใช้กับเอกสารที่ไม่มีช่องลงนามของผู้ยื่นเท่านั้น
     *
     * <p>เอกสารที่มีช่องลงนามของผู้ยื่น การลงนามใหม่คือการยื่น ไม่ต้องมีปุ่มนี้ (และไม่ยอมให้ใช้
     * เพราะจะข้ามการลงนามไปได้) ยื่นแล้วประตูแก้ไขปิด
     *
     * @return null เมื่อสำเร็จ หรือข้อความบอกผู้ยื่นว่าทำไมยื่นไม่ได้
     */
    @Transactional
    public String submitRevision(PositionRequest request, int documentType, UserDtls applicant) {
        if (!canSubmitRevision(request, documentType)) {
            return "เอกสารฉบับนี้ไม่อยู่ในสถานะที่ยื่นการแก้ไขได้";
        }
        List<PositionDocument> docs = documentRepository.findByRequestId(request.getId()).stream()
                .filter(d -> d.getDocumentType() == documentType)
                .toList();
        LocalDateTime now = LocalDateTime.now();
        docs.forEach(d -> d.setRevisionSubmittedAt(now));
        documentRepository.saveAll(docs);
        logDocumentEdit(request, documentType, null, applicant, PositionDocumentEditLog.EditAction.REVISION_SUBMITTED);
        return null;
    }

    /** เอกสารถูกส่งกลับ ยังไม่ยื่น และไม่มีช่องลงนามของผู้ยื่น — ปุ่ม "ยื่นการแก้ไข" จึงแสดง */
    public boolean canSubmitRevision(PositionRequest request, int documentType) {
        if (!canApplicantEditDocument(request, documentType)
                || request.getCurrentStatus().isDraft()) {
            return false;
        }
        return SignatureAnchorRegistry.slotsOf(SignatureModule.POSITION, documentType).stream()
                .noneMatch(slot -> "applicant".equals(slot.slotKey()));
    }

    /** เวลาส่งกลับรอบล่าสุดของเอกสารฉบับนี้ หรือ null ถ้าไม่เคยถูกส่งกลับ */
    public LocalDateTime latestRevisionRequestedAt(Long requestId, int documentType) {
        return documentRepository.findByRequestIdAndDocType(requestId, documentType)
                .stream()
                .map(PositionDocument::getRevisionRequestedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /** แอดมินส่งเอกสารฉบับนี้กลับมาให้ผู้ยื่นแก้ไขแล้วหรือยัง */
    public boolean isRevisionRequested(Long requestId, int documentType) {
        return getDocumentsByType(requestId, documentType).stream()
                .anyMatch(PositionDocument::isRevisionRequested);
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
        var module = com.ecom.academic.model.SignatureModule.POSITION;
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

    /**
     * เอกสารที่ส่งกลับให้ผู้ยื่นแก้ แล้วผู้ยื่นยังไม่ได้ลงนามฉบับแก้ไข — ระหว่างนี้งานของเจ้าหน้าที่
     * ในคำร้องนี้ทั้งหมดพักไว้ ดู {@link ApplicantRevisionRule}
     */
    @Transactional(readOnly = true)
    public List<Integer> documentsAwaitingApplicant(Long requestId) {
        java.util.Map<Integer, LocalDateTime> sentBack = new java.util.HashMap<>();
        documentRepository.findByRequestId(requestId).forEach(doc -> {
            if (doc.getRevisionRequestedAt() != null) {
                sentBack.merge(doc.getDocumentType(), doc.getRevisionRequestedAt(),
                        (x, y) -> x.isAfter(y) ? x : y);
            }
        });
        return ApplicantRevisionRule.awaitingApplicant(com.ecom.academic.model.SignatureModule.POSITION,
                requestId, sentBack, signatureRequestRepository);
    }

    /** ข้อความบอกเจ้าหน้าที่ว่าติดเอกสารฉบับไหน — null เมื่อเดินต่อได้ */
    private String resignBlocker(Long requestId) {
        List<Integer> pending = documentsAwaitingResign(requestId);
        if (pending.isEmpty()) {
            return null;
        }
        return "ยังเปลี่ยนสถานะไม่ได้ — เอกสารที่ส่งกลับให้แก้ไขยังลงนามใหม่ไม่ครบ: "
                + pending.stream().map(t -> "เอกสารที่ " + docNumber(t) + " (" + getDocLabel(t) + ")")
                        .reduce((a, b) -> a + ", " + b).orElse("")
                + " — ผู้ยื่นต้องแก้และลงนามใหม่ และผู้ลงนามคนอื่นในเอกสารนั้นต้องลงนามใหม่ครบก่อน";
    }

    /** เหตุผลที่แอดมินส่งเอกสารฉบับนี้กลับมาให้แก้ไข (ถ้ามี) */
    public String getRevisionNote(Long requestId, int documentType) {
        return getDocumentsByType(requestId, documentType).stream()
                .filter(PositionDocument::isRevisionRequested)
                .map(PositionDocument::getRevisionNote)
                .filter(note -> note != null && !note.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * แอดมินส่งเอกสารกลับให้ผู้ยื่นแก้ไข — ปลดล็อกเฉพาะเอกสารฉบับที่ระบุ
     *
     * <p>ถ้ายังไม่เคยมีแถวของเอกสารฉบับนั้น (ผู้ยื่นยังไม่เคยกรอก) จะสร้างแถวเปล่าไว้ถือสถานะ
     * มิฉะนั้นการส่งกลับจะเงียบหายไปเฉย ๆ และผู้ยื่นก็ยังแก้ไม่ได้อยู่ดี
     */
    @Transactional
    public void openDocumentForRevision(Long requestId, int documentType, String note) {
        String trimmed = (note != null && !note.isBlank()) ? note.trim() : null;
        if (trimmed != null && trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }

        List<PositionDocument> docs = getDocumentsByType(requestId, documentType);
        if (docs.isEmpty()) {
            PositionRequest request = requestRepository.findById(requestId).orElse(null);
            if (request == null) {
                return;
            }
            PositionDocument placeholder = new PositionDocument();
            placeholder.setRequest(request);
            placeholder.setDocumentType(documentType);
            placeholder.setDocumentLabel(getDocLabel(documentType));
            placeholder.setIsDraft(true);
            docs = List.of(placeholder);
        }

        for (PositionDocument doc : docs) {
            doc.setRevisionRequestedAt(LocalDateTime.now());
            doc.setRevisionNote(trimmed);
        }
        documentRepository.saveAll(docs);
    }

    public List<Integer> getCompletedDocTypes(Long requestId) {
        List<Integer> completed = new java.util.ArrayList<>(documentRepository.findCompletedDocTypes(requestId));
        // ส่งลงนามแล้วคือฉบับจริงแม้แถวยังติดธงร่าง — ปุ่มส่งลงนามบันทึกผ่านร่างอัตโนมัติ
        // ถ้าไม่นับ เอกสารที่ลงนามแล้วขึ้น "รอกรอก" และผู้ยื่นส่งคำร้องไม่ได้เพราะถูกนับว่ากรอกไม่ครบ
        documentRepository.findDraftDocTypes(requestId).stream()
                .filter(type -> !completed.contains(type))
                .filter(type -> isDocumentLockedForSigning(requestId, type))
                .forEach(completed::add);
        return completed;
    }

    /**
     * เอกสารที่มีแต่แถวร่าง — เริ่มทำแล้วแต่ยังไม่เสร็จ
     *
     * <p>ตัดเอกสารที่บันทึกแล้วออก เพราะแถวร่างเก่าอาจค้างอยู่คู่กัน กล่องเดียวมีสองสีไม่ได้
     */
    public List<Integer> getDraftDocTypes(Long requestId) {
        List<Integer> completed = getCompletedDocTypes(requestId);
        return documentRepository.findDraftDocTypes(requestId).stream()
                .filter(type -> !completed.contains(type))
                .toList();
    }

    public PositionRequest save(PositionRequest request) {
        return requestRepository.save(request);
    }

    @SuppressWarnings("unchecked")
    private void syncDoc2ToRequest(PositionRequest request, String jsonData) {
        try {
            Map<String, String> data = objectMapper.readValue(jsonData,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
            // ตำแหน่งที่ขอตั้งไว้ตอนสร้างคำร้อง (จากผลประเมินการสอน หรือ ศ.) เอกสารที่ 2 เติมให้ได้
            // เฉพาะคำร้องที่ยังไม่มี ไม่ใช่ที่เปลี่ยนมัน
            String pos = data.get("request_position");
            if (pos != null && !pos.isBlank() && request.getTargetPosition() == null) {
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
                // แถวที่ลบข้อความทิ้งแล้ว — id เดิมยังค้างใน JSON เพราะการบันทึกรวมค่าใหม่ทับค่าเดิม
                // ไม่ได้ลบคีย์ที่ไม่ได้ส่งมา นับเฉพาะแถวที่ยังมีผลงานอยู่จริง
                String line = data.get(entry.getKey().replace("_scopus_id_", "_"));
                if (line == null || line.isBlank()) {
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
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                return;
            }
            try {
                java.nio.file.Path path = uploadPaths.resolve(filePath);
                if (path != null) {
                    java.nio.file.Files.deleteIfExists(path);
                }
            } catch (Exception e) {
                log.warn("Failed to delete position physical file {}: {}", filePath, e.getMessage());
            }
        }
    }
}
