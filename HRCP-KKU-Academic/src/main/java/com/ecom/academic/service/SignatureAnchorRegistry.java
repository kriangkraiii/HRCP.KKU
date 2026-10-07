package com.ecom.academic.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ecom.academic.model.SignatureModule;

/**
 * Where each document's signatures go, and who signs them.
 *
 * <p>Every signature block in these templates follows the same shape:
 *
 * <pre>
 *   ลงชื่อ...................................     &lt;- dotted line (sometimes absent)
 *   ({{dean_name}})                              &lt;- the name, always a placeholder
 *   ตำแหน่ง คณบดี                                  &lt;- the role
 * </pre>
 *
 * <p>The name placeholder is what this registry anchors on. That choice is what
 * makes the feature possible without editing a single {@code .docx} in Word: a
 * survey of all 18 templates found a name placeholder above every signature
 * line, whereas the "ลงชื่อ" text itself is missing from seven of them and
 * spelled three different ways in the rest.
 *
 * <p>Kept as configuration rather than scattered constants so that adding a
 * signer to a document is a one-line change here.
 */
@Component
public class SignatureAnchorRegistry {

    /**
     * A question the signer must answer as part of signing.
     *
     * <p>Some forms ask the signer for a finding, not just a signature — the
     * supervisor's qualification review is the first of them. The answer belongs
     * to the signature, not to the body the applicant submitted, so it is
     * recorded on the step and only merged into the document when it is
     * rendered. Writing it into the document data instead would change the
     * content after freezing and void every signature on the envelope.
     *
     * @param fieldKey   the {@code {{...}}} placeholder the answer fills
     * @param question   Thai prompt shown on the signing page
     * @param options    the permitted answers, in display order
     * @param alertValue the answer that should raise a notification, or null if
     *                   none of them is remarkable
     */
    public record SignerChoice(
            String fieldKey,
            String question,
            List<String> options,
            String alertValue,
            boolean renderAsTick) {

        /** คำถามที่คำตอบถูกพิมพ์ลงเอกสารเป็นข้อความตรง ๆ เช่น "ครบถ้วน" ในช่องเติมคำ */
        public SignerChoice(String fieldKey, String question, List<String> options, String alertValue) {
            this(fieldKey, question, options, alertValue, false);
        }

        /**
         * ค่าที่จะถูกเขียนลงเอกสารสำหรับคำตอบนี้
         *
         * <p>บันทึกข้อความราชการส่วนใหญ่พิมพ์ข้อความไว้แล้วและเหลือวงเล็บว่างไว้ให้ติ๊ก
         * ({@code ( {{chk_hr_verify}} ) เห็นควรแต่งตั้ง...}) การเขียนคำว่า "เห็นควร" ลงในวงเล็บนั้น
         * จะได้ประโยคที่อ่านไม่รู้เรื่อง ช่องแบบนั้นจึงรับได้แค่เครื่องหมายถูกหรือความว่างเปล่า
         */
        public String renderedValue(String answer) {
            if (!renderAsTick) {
                return answer;
            }
            return options.get(0).equals(answer) ? TICK : "";
        }
    }

    /** เครื่องหมายที่ใส่ในวงเล็บของแบบฟอร์มราชการ */
    public static final String TICK = "✓";

    /**
     * ช่องในเอกสารที่เป็นของผู้ลงนามช่องนี้ นอกเหนือจากลายเซ็นและคำตอบของคำถาม
     *
     * @param forwardedTickFieldKey ช่อง "เพื่อโปรดพิจารณา" — ติ๊กเองเมื่อช่องลงนามนี้อยู่ในซอง
     *                              จริง ไม่ใช่ให้แอดมินติ๊กแทน เพราะถ้าเอกสารเวียนไปถึงคนนี้
     *                              ข้อความนั้นก็เป็นจริงโดยนิยามอยู่แล้ว
     * @param commentFieldKey       ช่องความเห็นแบบข้อความ เติมจากความเห็นที่ผู้ลงนามพิมพ์
     * @param commentTickFieldKey   ช่องกากบาทหน้าบรรทัดความเห็น ติ๊กเมื่อมีความเห็นจริง
     * @param signedDateFieldKey    ช่อง "วันที่" ใต้เส้นลงนาม เติมจากวันที่ผู้ลงนามเซ็นจริง
     *                              แทนที่จะให้ใครพิมพ์เอง ซึ่งไม่มีทางรู้ล่วงหน้าว่าจะเซ็นวันไหน
     */
    public record SignerMarks(
            String forwardedTickFieldKey,
            String commentFieldKey,
            String commentTickFieldKey,
            String signedDateFieldKey) {

        public SignerMarks(String forwardedTickFieldKey, String commentFieldKey, String commentTickFieldKey) {
            this(forwardedTickFieldKey, commentFieldKey, commentTickFieldKey, null);
        }

        /** ช่องลงนามที่เอกสารมีแค่บรรทัดวันที่ให้เติม */
        public static SignerMarks signedDate(String signedDateFieldKey) {
            return new SignerMarks(null, null, null, signedDateFieldKey);
        }
    }

    /**
     * One signature position within a document.
     *
     * @param slotKey            stable identifier for this position, stored on the
     *                           signing step so a signature can be matched back to
     *                           its place even if the order later changes
     * @param roleLabel          Thai label shown when assigning a signer
     * @param anchorPlaceholder  the {@code {{...}}} token naming the signer; the
     *                           image is stamped directly above it
     * @param defaultStaffRole   which {@code staff_member.staff_role} to offer
     *                           first in the picker, or null for the applicant
     * @param order              signing sequence, ascending; equal values may sign
     *                           in parallel
     * @param choice             a question to answer while signing, or null for
     *                           the usual case of signing alone
     */
    public record SignatureSlot(
            String slotKey,
            String roleLabel,
            String anchorPlaceholder,
            String defaultStaffRole,
            int order,
            SignerChoice choice,
            SignerMarks marks) {

        /** A slot that only collects a signature — by far the common case. */
        public SignatureSlot(String slotKey, String roleLabel, String anchorPlaceholder,
                String defaultStaffRole, int order) {
            this(slotKey, roleLabel, anchorPlaceholder, defaultStaffRole, order, null, null);
        }

        /** ช่องที่มีคำถามให้ตอบ แต่ไม่มีช่องกากบาทหรือช่องความเห็นอื่นในเอกสาร */
        public SignatureSlot(String slotKey, String roleLabel, String anchorPlaceholder,
                String defaultStaffRole, int order, SignerChoice choice) {
            this(slotKey, roleLabel, anchorPlaceholder, defaultStaffRole, order, choice, null);
        }
    }

    /** คำตอบที่แปลว่าผู้ลงนามไม่เห็นด้วย — เส้นทางนี้หยุดการเวียนและตีเอกสารกลับ */
    public static final String NOT_APPROVED = "ไม่เห็นควร";

    /** คำตอบที่แปลว่าผู้ลงนามเห็นด้วย ให้เดินต่อไปยังผู้ลงนามลำดับถัดไป */
    public static final String APPROVED = "เห็นควร";

    private record DocKey(SignatureModule module, int documentType) {
    }

    /** ฉบับแยกตามผลงานของเอกสารที่ 9 เฟส 2 ใช้ช่องลงนามของเอกสารที่ 9 ({@link PositionDocTypes}) */
    private static DocKey key(SignatureModule module, int documentType) {
        return new DocKey(module, module == SignatureModule.POSITION
                ? PositionDocTypes.base(documentType) : documentType);
    }

    private static final String APPLICANT = null;

    private static final Map<DocKey, List<SignatureSlot>> SLOTS = Map.ofEntries(

            // ---------------------------------------------------------- Phase 1
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 1), List.of(
                    new SignatureSlot("applicant", "ผู้ขอประเมินผลการสอน",
                            "applicant_name", APPLICANT, 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 2), List.of(
                    new SignatureSlot("applicant", "ผู้ขอรับการประเมิน",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("hr", "นักทรัพยากรบุคคล",
                            "hr_staff_name", "HR", 2))),

            // คำสั่งแต่งตั้งคณะอนุกรรมการ — ฉบับเดียวในระบบที่มีช่อง "เห็นควร/เห็นชอบ" ของจริง
            // พิมพ์อยู่ในเนื้อเอกสาร เดิมแอดมินติ๊กแทนทุกช่องก่อนปล่อยเวียน แล้วเจ้าของความเห็น
            // ค่อยมาเซ็นทับสิ่งที่ตัวเองไม่ได้เลือก ตอนนี้แต่ละช่องเป็นคำตอบของคนที่เซ็นช่องนั้น
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 3), List.of(
                    new SignatureSlot("head", "หัวหน้าสาขาวิชา",
                            "department_head", "HEAD", 1,
                            new SignerChoice("chk_cs_head2",
                                    "เห็นควรแต่งตั้งคณะอนุกรรมการประเมิน จำนวน 3 รายชื่อ",
                                    List.of(APPROVED, NOT_APPROVED), NOT_APPROVED, true),
                            // หัวหน้าสาขาเสนอเรื่องต่อคณบดี
                            new SignerMarks("chk_cs_head", null, null)),
                    new SignatureSlot("associate_dean", "รองคณบดี",
                            "associate_dean_name", "DEAN", 2, null,
                            // รองคณบดีเสนอเรื่องต่อหัวหน้าสาขาวิชา
                            new SignerMarks("chk_dean_sign1", null, null)),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 3,
                            new SignerChoice("chk_dean_sign2",
                                    "เห็นชอบ และดำเนินการนัดวันประเมินผลการสอนต่อไป",
                                    List.of(APPROVED, NOT_APPROVED), NOT_APPROVED, true),
                            new SignerMarks(null, "dean_comment", "chk_dean_sign3")),
                    new SignatureSlot("hr", "เจ้าหน้าที่บริหารงาน",
                            "hr_staff_name", "HR", 4,
                            new SignerChoice("chk_hr_verify",
                                    "เห็นควรแต่งตั้งคณะอนุกรรมการประเมินผลการสอนและนัดวันประเมินผลการสอนต่อไป",
                                    List.of(APPROVED, NOT_APPROVED), NOT_APPROVED, true)))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 4), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 5), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            // Phase 1 doc 6 (formerly doc 5) is a bare suggestions textbox ({{suggestions_text}})
            // with no signature block at all, so it is deliberately absent.

            // แบบประเมินผลการสอน — กรรมการทั้งสามคนตามคำสั่งแต่งตั้งลงนาม ประธานก่อน แล้วกรรมการคนที่ 2 และ 3
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 7), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะกรรมการประเมิน",
                            "committee_1_name", "COMMITTEE", 1),
                    new SignatureSlot("committee_member_2", "กรรมการ (คนที่ 2)",
                            "committee_2_name", "COMMITTEE", 2),
                    new SignatureSlot("committee_member_3", "กรรมการ (คนที่ 3)",
                            "committee_3_name", "COMMITTEE", 3))),

            // วันที่ใต้ชื่อประธานคือวันที่ประธานลงนามจริง ระบบเติมให้ตอนลงนาม ไม่ต้องกรอกในฟอร์ม
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 8), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะอนุกรรมการ",
                            "committee_president_name", "COMMITTEE", 1, null,
                            SignerMarks.signedDate("sign_date")))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 9), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            // ---------------------------------------------------------- Phase 2
            // แบบ ก.พ.ว. มข. ๐๓ ฉบับเต็ม — ส่วนที่ ๒ (แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา) อยู่ในไฟล์
            // เดียวกันแล้ว จึงเวียนต่อจากผู้ยื่นไปหัวหน้าสาขาและคณบดีในซองเดียว ผู้ลงนามสองคนหลังไม่ได้
            // แค่เซ็น แต่ต้องบันทึกผลการตรวจสอบด้วย ช่องผลประเมินสองช่องนี้เป็นของผู้ลงนามเท่านั้น
            // ถ้อยคำตัวเลือกตามแบบฟอร์มทางการ: หัวหน้าตอบ ครบถ้วน/ไม่ครบถ้วน คณบดีตอบ เข้าข่าย/ไม่เข้าข่าย
            Map.entry(new DocKey(SignatureModule.POSITION, 1), List.of(
                    new SignatureSlot("applicant", "เจ้าของประวัติ",
                            "applicant_name", APPLICANT, 1, null,
                            SignerMarks.signedDate("sign_date")),
                    new SignatureSlot("head", "หัวหน้าสาขาวิชา",
                            "department_head_name", "HEAD", 2,
                            new SignerChoice("qualification_status", "ผลการตรวจสอบคุณสมบัติ",
                                    List.of("ครบถ้วน", "ไม่ครบถ้วน"), "ไม่ครบถ้วน"),
                            SignerMarks.signedDate("head_sign_date")),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 3,
                            new SignerChoice("dean_qualification_status", "ความเห็นคณบดี",
                                    List.of("เข้าข่าย", "ไม่เข้าข่าย"), "ไม่เข้าข่าย"),
                            SignerMarks.signedDate("dean_sign_date")),
                    // ส่วนที่ ๓ แบบประเมินผลการสอน — เนื้อความดึงจากผลประเมินใน Phase 1 แต่ประธาน
                    // คณะอนุกรรมการลงนามรับรองในเอกสารฉบับนี้ใหม่ ไม่ใช้ลายเซ็นเดิมจาก Phase 1
                    new SignatureSlot("committee_chair", "ประธานคณะอนุกรรมการประเมินผลการสอน",
                            TeachingEvaluationPartResolver.CHAIR_ANCHOR, "COMMITTEE", 4, null,
                            SignerMarks.signedDate("s3_sign_date")))),

            Map.entry(new DocKey(SignatureModule.POSITION, 2), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1))),

            // วันที่ใต้ลายเซ็นทั้งสองช่องคือวันที่ลงนามจริง ระบบเติมให้ตอนลงนาม ไม่ต้องกรอกในฟอร์ม
            Map.entry(new DocKey(SignatureModule.POSITION, 3), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1, null,
                            SignerMarks.signedDate("certification_date")),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 2, null,
                            SignerMarks.signedDate("verify_date")))),

            Map.entry(new DocKey(SignatureModule.POSITION, 4), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("head", "ผู้บังคับบัญชาชั้นต้น",
                            "department_head_name", "HEAD", 2))),

            // บันทึกจริยธรรมการวิจัยในมนุษย์: ผู้ขอลงนามก่อน แล้วคณบดี — ตามบรรทัดลงนามในเทมเพลต
            Map.entry(new DocKey(SignatureModule.POSITION, 6), List.of(
                    new SignatureSlot("applicant", "ผู้ขอกำหนดตำแหน่งทางวิชาการ",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_signature_name", "DEAN", 2))),

            Map.entry(new DocKey(SignatureModule.POSITION, 7), List.of(
                    new SignatureSlot("hr", "เจ้าหน้าที่ผู้ตรวจสอบ",
                            "hr_officer_name", "HR", 1),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 2))),

            Map.entry(new DocKey(SignatureModule.POSITION, 8), List.of(
                    new SignatureSlot("hr", "นักทรัพยากรบุคคล",
                            "name_admin", "HR", 1))),

            Map.entry(new DocKey(SignatureModule.POSITION, 9), List.of(
                    new SignatureSlot("applicant", "ผู้ขอกำหนดตำแหน่งทางวิชาการ",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("first_author", "ผู้ประพันธ์อันดับแรก",
                            "firstauthor_name", APPLICANT, 2),
                    new SignatureSlot("corresponding_author", "ผู้ประพันธ์บรรณกิจ",
                            "corres_name", APPLICANT, 3))));

    /**
     * ช่องที่เจ้าหน้าที่ผู้ตรวจเอกสารลงนามเองเสมอ — ไม่มีการเลือกผู้ลงนามหรือส่งต่อให้คนอื่น
     *
     * <p>เอกสารที่ 2 เฟส 1: นักทรัพยากรบุคคลที่ติ๊กคอลัมน์ "เจ้าหน้าที่" คือคนที่รับรองผลตรวจนั้น
     * คนตรวจกับคนเซ็นจึงต้องเป็นคนเดียวกัน
     */
    private static final Map<DocKey, java.util.Set<String>> REVIEWER_SIGNED = Map.of(
            new DocKey(SignatureModule.ACADEMIC, 2), java.util.Set.of("hr"));

    /** ช่องนี้ต้องเป็นเจ้าหน้าที่ที่กดยืนยันเองหรือไม่ */
    public static boolean isSignedByReviewer(SignatureModule module, int documentType, String slotKey) {
        return REVIEWER_SIGNED.getOrDefault(key(module, documentType), java.util.Set.of()).contains(slotKey);
    }

    /**
     * เอกสารที่ผู้ลงนามแต่ละตำแหน่งคือคนที่ชื่ออยู่ในช่องชื่อของตำแหน่งนั้นในแบบฟอร์ม
     * ({@link SignatureSlot#anchorPlaceholder()}) — เลือกผู้ลงนามแยกจากชื่อในเอกสารไม่ได้
     *
     * <p>เอกสารที่ 3 เฟส 1: เจ้าหน้าที่กรอกชื่อหัวหน้าสาขา รองคณบดี คณบดี และเจ้าหน้าที่ลงในคำสั่ง
     * เดิมเลือกผู้ลงนามได้อีกที่ เอกสารจึงระบุชื่อคนหนึ่งแต่ให้อีกคนเซ็นได้
     */
    /*
     * ช่องชื่อผู้ลงนามในแบบฟอร์มที่เป็นตัวค้นหาชื่อ (person_picker.js) → ช่องตำแหน่งที่คู่กัน ("" = ไม่มี)
     * ทุกช่องตรงกับ anchorPlaceholder ของตำแหน่งลงนามในตาราง SLOTS ดู docs/PLAN-signer-picker.md
     * ตำแหน่งที่ชื่อไม่ได้อยู่ในแบบฟอร์ม (เช่น เฟส 2 ฉบับ 1) ยังเลือกผู้ลงนามในแผงลงนามตามเดิม
     */
    private static final Map<DocKey, Map<String, String>> SIGNER_NAME_FIELDS = Map.ofEntries(
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 3), Map.of(
                    "department_head", "dropdownCsHead",
                    "associate_dean_name", "dropdownAssociateDean",
                    "dean_name", "dropdownDean",
                    "hr_staff_name", "dropdownHr")),
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 4), Map.of("dean_name", "deandropdown_position")),
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 5), Map.of("dean_name", "dropdownDean")),
            // กรรมการเลือกจากบัญชีในเอกสารที่ 3 แล้วส่งต่อมาแบบอ่านอย่างเดียว (AcademicRequestService.carriedFields)
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 7), Map.of(
                    "committee_1_name", "",
                    "committee_2_name", "",
                    "committee_3_name", "")),
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 8), Map.of("committee_president_name", "")),
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 9), Map.of("dean_name", "dean_position")),
            Map.entry(new DocKey(SignatureModule.POSITION, 3), Map.of("dean_name", "dean_position")),
            Map.entry(new DocKey(SignatureModule.POSITION, 4), Map.of("department_head_name", "")),
            Map.entry(new DocKey(SignatureModule.POSITION, 6), Map.of("dean_signature_name", "position_title")),
            Map.entry(new DocKey(SignatureModule.POSITION, 7), Map.of(
                    "hr_officer_name", "hr_officer_position",
                    "dean_name", "dean_position")),
            Map.entry(new DocKey(SignatureModule.POSITION, 8), Map.of("name_admin", "")),
            Map.entry(new DocKey(SignatureModule.POSITION, 9), Map.of(
                    "firstauthor_name", "",
                    "corres_name", "")));

    /**
     * ช่องชื่อผู้ลงนามที่ดึงมาจากเอกสารก่อนหน้า แก้ในฉบับนี้ไม่ได้ (AcademicRequestService.carriedFields)
     * → เอกสารที่ต้องไปแก้ ชื่อที่ผูกบัญชีไม่ได้จึงแก้ที่ต้นทาง ไม่ใช่ค้นหาในช่องที่ล็อก
     */
    private static final Map<DocKey, Map<String, String>> CARRIED_NAME_FIELDS = Map.of(
            new DocKey(SignatureModule.ACADEMIC, 7), Map.of(
                    "committee_1_name", "เอกสารที่ 3",
                    "committee_2_name", "เอกสารที่ 3",
                    "committee_3_name", "เอกสารที่ 3"),
            new DocKey(SignatureModule.ACADEMIC, 8), Map.of("committee_president_name", "เอกสารที่ 3"));

    /** เอกสารที่ชื่อในช่องนี้ถูกเลือกไว้ หรือ null เมื่อเลือกในฉบับนี้เอง */
    public static String nameCarriedFrom(SignatureModule module, int documentType, String nameField) {
        return CARRIED_NAME_FIELDS.getOrDefault(key(module, documentType), Map.of()).get(nameField);
    }

    /** ผู้ลงนามของเอกสารนี้ (บางตำแหน่ง) มาจากชื่อที่เลือกในแบบฟอร์มหรือไม่ */
    public static boolean isSignerNamedInForm(SignatureModule module, int documentType) {
        return SIGNER_NAME_FIELDS.containsKey(key(module, documentType));
    }

    /** ช่องชื่อผู้ลงนามที่เป็นตัวค้นหาชื่อของเอกสารนี้ — ผู้ลงนามตำแหน่งนั้นคือคนที่เลือกในช่อง */
    public static java.util.Set<String> signerNameFields(SignatureModule module, int documentType) {
        return SIGNER_NAME_FIELDS.getOrDefault(key(module, documentType), Map.of()).keySet();
    }

    /** ช่องตำแหน่งที่คู่กับช่องชื่อ หรือ null เมื่อไม่มี */
    public static String positionFieldFor(SignatureModule module, int documentType, String nameField) {
        String field = SIGNER_NAME_FIELDS.getOrDefault(key(module, documentType), Map.of()).get(nameField);
        return field == null || field.isEmpty() ? null : field;
    }

    /**
     * ช่องตำแหน่งใต้ชื่อผู้ลงนาม ของเอกสารที่ผู้ลงนามไม่ได้เลือกในแบบฟอร์ม — แบบฟอร์มจริงเว้นจุดไข่ปลาไว้
     * ให้เขียนเอง ระบบเติมเฉพาะเมื่อลงนามในฐานะรักษาการแทน ({@link SignerNameResolver})
     */
    private static final Map<DocKey, Map<String, String>> SIGNER_POSITION_LINES = Map.of(
            new DocKey(SignatureModule.POSITION, 1), Map.of(
                    "department_head_name", "head_position_line",
                    "dean_name", "dean_position_line"));

    /**
     * ช่องที่พิมพ์ตำแหน่งของผู้ลงนามช่องนี้ — ช่องตำแหน่งคู่กับตัวค้นหาชื่อ หรือบรรทัดตำแหน่งในแบบฟอร์ม
     *
     * @return ชื่อช่อง หรือ null เมื่อเอกสารไม่มีที่พิมพ์ตำแหน่งของช่องนี้
     */
    public static String printedPositionFieldFor(SignatureModule module, int documentType, String nameField) {
        String paired = positionFieldFor(module, documentType, nameField);
        if (paired != null) {
            return paired;
        }
        return SIGNER_POSITION_LINES.getOrDefault(key(module, documentType), Map.of()).get(nameField);
    }

    /** ตำแหน่งลงนามที่ใช้ช่องชื่อนี้ในเอกสารนี้ หรือ null เมื่อไม่ใช่ช่องชื่อผู้ลงนาม */
    public static String slotKeyForNameField(SignatureModule module, int documentType, String nameField) {
        if (nameField == null) {
            return null;
        }
        return slotsOf(module, documentType).stream()
                .filter(slot -> nameField.equals(slot.anchorPlaceholder()))
                .map(SignatureSlot::slotKey)
                .findFirst()
                .orElse(null);
    }

    /** ช่องของเอกสารนี้ที่เจ้าหน้าที่ผู้ตรวจลงนามเอง */
    public static java.util.Set<String> reviewerSignedSlots(SignatureModule module, int documentType) {
        return REVIEWER_SIGNED.getOrDefault(key(module, documentType), java.util.Set.of());
    }

    /** The signature positions for a document, in signing order. Empty if unsignable. */
    public List<SignatureSlot> slotsFor(SignatureModule module, int documentType) {
        return slotsOf(module, documentType);
    }

    /**
     * Same as {@link #slotsFor}, reachable without a bean.
     *
     * <p>{@link DocumentFieldOwnership} derives the signer-owned field names from
     * this table so the two can never drift apart, and it is a plain utility with
     * no Spring lifecycle of its own.
     */
    public static List<SignatureSlot> slotsOf(SignatureModule module, int documentType) {
        return SLOTS.getOrDefault(key(module, documentType), List.of())
                .stream()
                .sorted(java.util.Comparator.comparingInt(SignatureSlot::order))
                .toList();
    }

    /** Whether this document has anywhere to put a signature. */
    public boolean isSignable(SignatureModule module, int documentType) {
        return !slotsFor(module, documentType).isEmpty();
    }

    /** One slot by its key, for validating a signing step against the template. */
    public java.util.Optional<SignatureSlot> slot(SignatureModule module, int documentType, String slotKey) {
        return slotsFor(module, documentType).stream()
                .filter(s -> s.slotKey().equals(slotKey))
                .findFirst();
    }

    /** Every document that can be signed, for tests and admin screens. */
    public List<SignatureModule> modules() {
        return List.of(SignatureModule.ACADEMIC, SignatureModule.POSITION);
    }

    /** Signable document types within a module, ascending. */
    public List<Integer> signableDocumentTypes(SignatureModule module) {
        return SLOTS.keySet().stream()
                .filter(k -> k.module() == module)
                .map(DocKey::documentType)
                .sorted()
                .toList();
    }
}
