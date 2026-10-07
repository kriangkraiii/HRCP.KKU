package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.repository.AcademicDocumentRepository;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.PositionRequestRepository;

/**
 * เรื่องที่ผู้ลงนามต้องรู้จากอีเมลฉบับเดียว: เป็นคำร้องของใคร เรื่องอะไร ({@link #caseOf}) และสำหรับผู้ลงนาม
 * จากนอก มข. ซึ่งไม่รู้จักระบบ — ท่านอยู่ในฐานะอะไร ต้องลงนามเอกสารใด เมื่อใด ({@link #forField}, {@link #forSlot})
 *
 * <p>อ่านข้อมูลในธุรกรรมของผู้เรียก แล้วคืนเป็นข้อความล้วน — อีเมลส่งแบบ async ซึ่งโหลดข้อมูลเพิ่มไม่ได้แล้ว
 */
@Component
// ร่วมธุรกรรมของการลงนาม (อ่านตอนสร้างอีเมล) — อ่านไม่สำเร็จต้องไม่ทำให้ธุรกรรมนั้นถูก rollback
@org.springframework.transaction.annotation.Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
public class SignerBriefing {

    /**
     * @param applicant ชื่อผู้ยื่นพร้อมคำนำหน้า
     * @param matter    เรื่อง (ประโยคเต็ม)
     * @param role      ฐานะของผู้ลงนามในเรื่องนี้
     * @param duties    สิ่งที่ผู้ลงนามต้องทำ ตามลำดับ
     */
    public record Briefing(String applicant, String matter, String role, List<String> duties) {
    }

    /**
     * คำร้องที่เอกสารนี้เป็นส่วนหนึ่ง
     *
     * @param applicant   ชื่อผู้ยื่นพร้อมคำนำหน้า
     * @param requestCode รหัสคำร้องที่ผู้ยื่นและเจ้าหน้าที่เห็นในระบบ
     * @param matter      เรื่อง เช่น "คำร้องขอรับการประเมินผลการสอนของ ผศ.สมชาย ใจดี"
     */
    public record CaseSummary(String applicant, String requestCode, String matter) {
    }

    /** ชื่อเอกสารตามที่ระบบแสดง (AcademicAdminController.DOC_LABELS) — ผู้ลงนามจะเห็นชื่อเดียวกันในหน้าลงนาม */
    private static final String EVALUATION_FORM = "แบบฟอร์มประเมินการสอน ตามประกาศ มข.1669-69";

    private final AcademicRequestRepository academicRequests;
    private final AcademicDocumentRepository academicDocuments;
    private final PositionRequestRepository positionRequests;

    public SignerBriefing(AcademicRequestRepository academicRequests,
            AcademicDocumentRepository academicDocuments, PositionRequestRepository positionRequests) {
        this.academicRequests = academicRequests;
        this.academicDocuments = academicDocuments;
        this.positionRequests = positionRequests;
    }

    /** คำร้องของใคร เรื่องอะไร — ใช้ในอีเมลเกี่ยวกับการลงนามทุกฉบับ */
    public Optional<CaseSummary> caseOf(SignatureModule module, Long requestId) {
        if (module == null || requestId == null) {
            return Optional.empty();
        }
        if (module == SignatureModule.ACADEMIC) {
            AcademicRequest request = academicRequests.findById(requestId).orElse(null);
            if (request == null || request.getApplicant() == null) {
                return Optional.empty();
            }
            String applicant = evaluationApplicant(request);
            String code = request.getRequestCode() != null ? request.getRequestCode() : String.valueOf(requestId);
            return Optional.of(new CaseSummary(applicant, code, "คำร้องขอรับการประเมินผลการสอนของ " + applicant));
        }
        PositionRequest request = positionRequests.findById(requestId).orElse(null);
        if (request == null || request.getApplicant() == null) {
            return Optional.empty();
        }
        String applicant = SignerNameResolver.printedName(request.getApplicant());
        String target = request.getTargetPosition();
        return Optional.of(new CaseSummary(applicant, request.getRequestCode(),
                "คำขอกำหนดตำแหน่งทางวิชาการของ " + applicant
                        + (target == null || target.isBlank() ? "" : " (ตำแหน่ง" + target.strip() + ")")));
    }

    private String evaluationApplicant(AcademicRequest request) {
        Map<String, String> doc1 = firstJson(academicDocuments
                .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(request.getId(), 1));
        return firstNonBlank(join(doc1.get("title"), doc1.get("applicant_name")),
                SignerNameResolver.printedName(request.getApplicant()));
    }

    /** ตอนเชิญ — ช่องในแบบฟอร์มที่ระบุชื่อผู้ลงนามคนนี้ */
    public Optional<Briefing> forField(SignatureModule module, Long requestId, int documentType, String field) {
        if (module == null || requestId == null || field == null) {
            return Optional.empty();
        }
        if (module == SignatureModule.ACADEMIC && (documentType == 3 || documentType == 7)) {
            int seat = committeeSeat(field);
            return seat == 0 ? Optional.empty() : evaluation(requestId, seat, false);
        }
        if (module == SignatureModule.POSITION && PositionDocTypes.base(documentType) == PositionDocTypes.WORK_PARTICIPATION) {
            return author(requestId, field.startsWith("firstauthor"));
        }
        return Optional.empty();
    }

    /** ตอนถึงคิวลงนาม — ตำแหน่งลงนามในซอง */
    public Optional<Briefing> forSlot(SignatureModule module, Long requestId, int documentType, String slotKey) {
        if (module == null || requestId == null || slotKey == null) {
            return Optional.empty();
        }
        if (module == SignatureModule.ACADEMIC && documentType == 7) {
            int seat = switch (slotKey) {
                case "committee_chair" -> 1;
                case "committee_member_2" -> 2;
                case "committee_member_3" -> 3;
                default -> 0;
            };
            return seat == 0 ? Optional.empty() : evaluation(requestId, seat, true);
        }
        if (module == SignatureModule.POSITION && PositionDocTypes.base(documentType) == PositionDocTypes.WORK_PARTICIPATION
                && (slotKey.equals("first_author") || slotKey.equals("corresponding_author"))) {
            return author(requestId, slotKey.equals("first_author"));
        }
        return Optional.empty();
    }

    private static int committeeSeat(String field) {
        return switch (field) {
            case "committee_1_name" -> 1;
            case "committee_2_name" -> 2;
            case "committee_3_name" -> 3;
            default -> 0;
        };
    }

    private Optional<Briefing> evaluation(Long requestId, int seat, boolean signingNow) {
        AcademicRequest request = academicRequests.findById(requestId).orElse(null);
        if (request == null || request.getApplicant() == null) {
            return Optional.empty();
        }
        Map<String, String> doc1 = firstJson(academicDocuments
                .findByRequestIdAndDocumentTypeOrderByCopyNumberAsc(requestId, 1));
        String applicant = evaluationApplicant(request);
        String requested = "✓".equals(doc1.get("chk1")) ? "ผู้ช่วยศาสตราจารย์"
                : "✓".equals(doc1.get("chk2")) ? "รองศาสตราจารย์"
                        : "✓".equals(doc1.get("chk3")) ? "ศาสตราจารย์" : null;
        String course = join(doc1.get("course_code"), doc1.get("course_name"));

        StringBuilder matter = new StringBuilder("การประเมินผลการสอนของ ").append(applicant);
        if (requested != null) {
            matter.append(" เพื่อประกอบการขอกำหนดตำแหน่ง").append(requested);
        }
        if (!course.isBlank()) {
            matter.append(" รายวิชา ").append(course);
        }

        String role = switch (seat) {
            case 1 -> "ประธานคณะอนุกรรมการประเมินผลการสอน";
            case 2 -> "อนุกรรมการประเมินผลการสอน (ผู้ทรงคุณวุฒิภายนอก)";
            default -> "อนุกรรมการและเลขานุการคณะอนุกรรมการประเมินผลการสอน";
        };
        String order = seat == 1 ? "ท่านลงนามเป็นลำดับแรก"
                : "ลงนามต่อจากประธานคณะอนุกรรมการ" + (seat == 3 ? " และอนุกรรมการ" : "");

        List<String> duties = new ArrayList<>();
        if (signingNow) {
            duties.add("ตรวจสอบ “" + EVALUATION_FORM + "” ซึ่งบันทึกผลการประเมินของคณะอนุกรรมการ");
            duties.add("ลงลายมือชื่ออิเล็กทรอนิกส์ในฐานะ" + role + " (" + order + ")");
        } else {
            duties.add("วิทยาลัยฯ จะมีหนังสือเชิญเป็นกรรมการ พร้อมแจ้งวัน เวลา และสถานที่ประเมินผลการสอนให้ท่านทราบ"
                    + " ภายหลังคณบดีลงนามคำสั่งแต่งตั้งคณะอนุกรรมการ");
            duties.add("ร่วมประเมินผลการสอนของผู้ขอรับการประเมินตามวันและเวลาดังกล่าว");
            duties.add("ภายหลังการประเมิน ลงลายมือชื่ออิเล็กทรอนิกส์ใน “" + EVALUATION_FORM
                    + "” ผ่านระบบนี้ (" + order + ") โดยระบบจะส่งอีเมลแจ้งท่านเมื่อถึงลำดับการลงนาม");
        }
        if (seat == 1) {
            duties.add("ลงลายมือชื่ออิเล็กทรอนิกส์ใน “ส่วนที่ 3 แบบประเมินผลการสอน” (สรุปผลการประเมิน) ในฐานะประธานคณะอนุกรรมการ");
        }
        return Optional.of(new Briefing(applicant, matter.toString(), role, duties));
    }

    private Optional<Briefing> author(Long requestId, boolean first) {
        PositionRequest request = positionRequests.findById(requestId).orElse(null);
        if (request == null || request.getApplicant() == null) {
            return Optional.empty();
        }
        String applicant = SignerNameResolver.printedName(request.getApplicant());
        String target = request.getTargetPosition();
        String matter = "การขอกำหนดตำแหน่งทางวิชาการของ " + applicant
                + (target == null || target.isBlank() ? "" : " ในตำแหน่ง" + target.strip());
        String role = first ? "ผู้ประพันธ์อันดับแรก (First Author) ของผลงานทางวิชาการที่ผู้ขอใช้ประกอบการพิจารณา"
                : "ผู้ประพันธ์บรรณกิจ (Corresponding Author) ของผลงานทางวิชาการที่ผู้ขอใช้ประกอบการพิจารณา";
        List<String> duties = List.of(
                "ตรวจสอบเอกสาร “ลักษณะการมีส่วนร่วมในผลงาน” ซึ่งระบุบทบาทและสัดส่วนการมีส่วนร่วมของผู้ขอในผลงานดังกล่าว",
                "ลงลายมือชื่ออิเล็กทรอนิกส์เพื่อรับรองว่าข้อมูลการมีส่วนร่วมดังกล่าวถูกต้อง"
                        + " โดยระบบจะส่งอีเมลแจ้งท่านเมื่อถึงลำดับการลงนาม");
        return Optional.of(new Briefing(applicant, matter, role, duties));
    }

    private static Map<String, String> firstJson(List<AcademicDocument> docs) {
        if (docs.isEmpty() || docs.get(0).getJsonData() == null) {
            return Map.of();
        }
        try {
            Map<String, String> data = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    docs.get(0).getJsonData(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {
                    });
            return data != null ? data : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** คำนำหน้าติดกับชื่อแบบที่พิมพ์ในเอกสาร ("ผศ.ดร." + "สมชาย ใจดี") */
    private static String join(String prefix, String rest) {
        String a = prefix == null ? "" : prefix.strip();
        String b = rest == null ? "" : rest.strip();
        if (a.isEmpty() || b.isEmpty()) {
            return (a + " " + b).strip();
        }
        return a.matches(".*[\\p{IsThai}.]$") && !a.matches("^[A-Za-z0-9].*") ? a + b : a + " " + b;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

}
