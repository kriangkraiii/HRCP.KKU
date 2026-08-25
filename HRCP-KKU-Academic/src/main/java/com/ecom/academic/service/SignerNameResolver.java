package com.ecom.academic.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ชื่อที่จะพิมพ์ไว้ใต้เส้นลงนามของแต่ละช่อง
 *
 * <p>เดิมเอกสารที่รอลงนามจะขึ้นวงเล็บว่าง "()" ตรงช่องของคนที่ยังไม่ได้เซ็น
 * เพราะเทมเพลตมี {@code {{hr_staff_name}}} แต่ไม่มีใครกรอกค่านั้น — ผู้ลงนาม
 * จึงเห็นเอกสารโล่งๆ โดยไม่รู้ว่ากำลังจะส่งต่อให้ใคร
 *
 * <p>ชื่อมาจากสองแหล่ง เรียงตามความน่าเชื่อถือ:
 * <ol>
 *   <li>ขั้นตอนลงนามในซองจริง — คนที่ถูกมอบหมายแล้ว ใช้ชื่อ ณ ตอนมอบหมาย
 *       (snapshot) เพื่อไม่ให้เอกสารที่เซ็นไปแล้วเปลี่ยนชื่อตามโปรไฟล์ที่แก้ทีหลัง</li>
 *   <li>การตั้งค่าลงนามของเอกสารนั้น — ช่องที่ยังไม่ถูกมอบหมาย ใช้ผู้ลงนาม
 *       เริ่มต้นที่แอดมินตั้งไว้ (หรือที่จับคู่จากบทบาทเจ้าหน้าที่)</li>
 * </ol>
 *
 * <p>ทั้งหมดเป็นการเติมตอน render เท่านั้น ไม่แตะ {@code frozenJson} ที่เก็บไว้
 * ในฐานข้อมูล ค่าแฮชที่ใช้ตรวจความถูกต้องของลายเซ็นจึงไม่เปลี่ยน
 */
@Component
public class SignerNameResolver {

    private static final Logger log = LoggerFactory.getLogger(SignerNameResolver.class);

    private final DocumentWorkflowConfigService workflowConfigService;
    private final SignatureStepRepository stepRepository;
    private final StaffMemberService staffMemberService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignerNameResolver(DocumentWorkflowConfigService workflowConfigService,
            SignatureStepRepository stepRepository,
            StaffMemberService staffMemberService,
            UserRepository userRepository) {
        this.workflowConfigService = workflowConfigService;
        this.stepRepository = stepRepository;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
    }

    /**
     * เติมชื่อผู้ลงนามลงใน JSON ของเอกสาร เฉพาะช่องที่ยังว่าง
     *
     * <p>ไม่ทับค่าที่กรอกมาแล้ว เช่น {@code applicant_name} ที่ผู้ยื่นพิมพ์เอง
     * หรือชื่อเจ้าหน้าที่ที่แอดมินกรอกในแบบฟอร์ม
     *
     * @return JSON ชุดใหม่ หรือชุดเดิมเมื่อไม่มีอะไรต้องเติม
     */
    public String fillInto(SignatureRequest envelope, String json) {
        if (envelope == null || json == null || json.isBlank()) {
            return json;
        }
        Map<String, String> names = namesForEnvelope(envelope);
        if (names.isEmpty()) {
            return json;
        }

        try {
            Map<String, Object> data = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            boolean changed = false;
            for (Map.Entry<String, String> entry : names.entrySet()) {
                Object current = data.get(entry.getKey());
                if (current == null || String.valueOf(current).isBlank()) {
                    data.put(entry.getKey(), entry.getValue());
                    changed = true;
                }
            }
            return changed ? objectMapper.writeValueAsString(data) : json;
        } catch (Exception e) {
            // ชื่อหายดีกว่าเอกสารเปิดไม่ขึ้น
            log.warn("Could not fill signer names into envelope {}: {}", envelope.getId(), e.toString());
            return json;
        }
    }

    /**
     * ชื่อผู้ลงนามของซองนี้ แยกตาม anchor placeholder ของเทมเพลต
     *
     * <p>ช่องที่มีขั้นตอนลงนามอยู่แล้วชนะค่าเริ่มต้นเสมอ เพราะคนที่ถูกมอบหมาย
     * จริงอาจไม่ใช่คนเดียวกับที่ตั้งไว้ในการตั้งค่า
     */
    public Map<String, String> namesForEnvelope(SignatureRequest envelope) {
        Map<String, String> names = expectedNames(envelope.getModule(), envelope.getDocumentType());

        for (SignatureStep step : stepRepository.findStepsWithSigner(envelope.getId())) {
            if (step.getStatus() == SignatureStepStatus.SKIPPED || step.getAnchorPlaceholder() == null) {
                continue;
            }
            String name = printedName(step);
            if (name != null && !name.isBlank()) {
                names.put(step.getAnchorPlaceholder(), name);
            }
        }
        return names;
    }

    /**
     * ชื่อผู้ลงนามเริ่มต้นตามการตั้งค่าลงนามของเอกสาร
     *
     * <p>ช่องของผู้ยื่นไม่มีผู้ลงนามเริ่มต้น (ขึ้นกับว่าใครเป็นเจ้าของคำร้อง)
     * จึงไม่ถูกเติมจากทางนี้ — ชื่อผู้ยื่นมาจากแบบฟอร์มอยู่แล้ว
     */
    public Map<String, String> expectedNames(SignatureModule module, int documentType) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Integer> defaultSigners = workflowConfigService.defaultSignerUserIds(module, documentType);
        List<SignatureSlot> slots = workflowConfigService.effectiveSlotsFor(module, documentType);

        for (SignatureSlot slot : slots) {
            String name = null;

            Integer userId = defaultSigners.get(slot.slotKey());
            if (userId != null) {
                name = userRepository.findById(userId).map(SignerNameResolver::printedName).orElse(null);
            }

            // ทะเบียนเจ้าหน้าที่เป็นแหล่งสำรอง: คนที่ยังไม่มีบัญชีเข้าระบบก็ยัง
            // เป็นคนที่เอกสารต้องระบุชื่อไว้ให้เซ็น
            if ((name == null || name.isBlank()) && slot.defaultStaffRole() != null) {
                name = staffMemberService.findByRoleWithAccountStatus(slot.defaultStaffRole()).stream()
                        .map(StaffMember::getDisplayName)
                        .filter(candidate -> candidate != null && !candidate.isBlank())
                        .findFirst()
                        .orElse(null);
            }

            if (name != null && !name.isBlank()) {
                names.put(slot.anchorPlaceholder(), name);
            }
        }
        return names;
    }

    /**
     * ชื่อของขั้นตอนหนึ่ง — ใช้ชื่อ ณ ตอนมอบหมายเป็นหลัก
     *
     * <p>คำนำหน้าอ่านจากบัญชีปัจจุบัน เพราะ snapshot เก็บไว้แต่ชื่อ-สกุล และ
     * เอกสารราชการต้องมีคำนำหน้าในวงเล็บ
     */
    private static String printedName(SignatureStep step) {
        String name = step.getSignerNameSnapshot();
        UserDtls signer = step.getSigner();
        if (name == null || name.isBlank()) {
            return signer != null ? printedName(signer) : null;
        }
        String prefix = signer != null ? titleOf(signer) : "";
        return (prefix + name).trim();
    }

    /** ชื่อที่พิมพ์ลงเอกสาร: คำนำหน้า + ชื่อ-สกุล เช่น "นางสาวสมหญิง รักงาน" */
    public static String printedName(UserDtls user) {
        if (user == null) {
            return "";
        }
        String name = user.getName();
        if (name == null || name.isBlank()) {
            return "";
        }
        return (titleOf(user) + name).trim();
    }

    /**
     * คำนำหน้าสำหรับพิมพ์ในวงเล็บ
     *
     * <p>อ่านจากช่อง title เท่านั้น (นาย / นาง / ผศ.ดร. ...) ไม่ใช้ตำแหน่งงาน
     * เพราะตำแหน่งพิมพ์อยู่ใต้เส้นลงนามอยู่แล้ว การเอามาต่อหน้าชื่อจะกลายเป็น
     * "นักทรัพยากรบุคคลสมหญิง รักงาน"
     */
    private static String titleOf(UserDtls user) {
        String title = user.getTitle();
        return (title != null && !title.isBlank()) ? title.trim() : "";
    }
}
