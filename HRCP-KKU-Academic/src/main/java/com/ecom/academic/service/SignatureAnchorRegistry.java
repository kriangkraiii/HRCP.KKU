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
     */
    public record SignerMarks(
            String forwardedTickFieldKey,
            String commentFieldKey,
            String commentTickFieldKey) {
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

    /**
     * คำถามเริ่มต้นของทุกช่องลงนามที่ไม่ใช่ของผู้ยื่น และเอกสารไม่ได้กำหนดคำถามเฉพาะไว้
     *
     * <p>ไม่ได้ใส่ลงใน {@link #SLOTS} ทีละช่อง — {@code SignatureWorkflowService.signerChoiceFor}
     * เติมให้ตอนจะลงนามแทน เพราะ {@code DocumentFieldOwnership.signerFields} อ่าน slot ที่มี
     * {@code choice()} แล้วลบ {@code fieldKey} นั้นทิ้งจากข้อมูลที่ฟอร์มส่งมา และ
     * {@code SignerNameResolver.choicesForEnvelope} จะพยายาม render มันลงเอกสาร ซึ่งไม่มี
     * เทมเพลต docx ฉบับไหนมีช่อง {@code {{consideration_result}}} รออยู่ การเติมที่ปลายทาง
     * การลงนามจึงได้สิ่งที่ต้องการพอดี: ถามผู้ลงนาม เก็บลงฐานข้อมูล แต่ไม่ไปยุ่งกับเนื้อเอกสาร
     */
    public static final SignerChoice CONSIDERATION = new SignerChoice(
            "consideration_result", "ผลการพิจารณา",
            List.of(APPROVED, NOT_APPROVED), NOT_APPROVED);

    private record DocKey(SignatureModule module, int documentType) {
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

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 7), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะกรรมการประเมิน",
                            "committee_1_name", "COMMITTEE", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 8), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะอนุกรรมการ",
                            "committee_president_name", "COMMITTEE", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 9), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            // ---------------------------------------------------------- Phase 2
            Map.entry(new DocKey(SignatureModule.POSITION, 1), List.of(
                    new SignatureSlot("applicant", "เจ้าของประวัติ",
                            "applicant_name", APPLICANT, 1))),

            Map.entry(new DocKey(SignatureModule.POSITION, 2), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1))),

            Map.entry(new DocKey(SignatureModule.POSITION, 3), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 2))),

            Map.entry(new DocKey(SignatureModule.POSITION, 4), List.of(
                    new SignatureSlot("applicant", "ผู้เสนอขอ",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("head", "ผู้บังคับบัญชาชั้นต้น",
                            "department_head_name", "HEAD", 2))),

            // แบบประเมินคุณสมบัติโดยผู้บังคับบัญชา — ผู้ลงนามไม่ได้แค่เซ็น แต่ต้องบันทึกผล
            // การตรวจสอบด้วย ผู้ยื่นกรอกได้เฉพาะส่วนหัวของฟอร์ม ช่องผลประเมินสองช่องนี้
            // เป็นของผู้ลงนามเท่านั้น
            Map.entry(new DocKey(SignatureModule.POSITION, 5), List.of(
                    new SignatureSlot("head", "หัวหน้าสาขาวิชา",
                            "department_head_name", "HEAD", 1,
                            new SignerChoice("qualification_status", "ผลการตรวจสอบคุณสมบัติ",
                                    List.of("ครบถ้วน", "ไม่ครบถ้วน"), "ไม่ครบถ้วน")),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 2,
                            new SignerChoice("dean_qualification_status", "ความเห็นคณบดี",
                                    List.of("ครบถ้วน", "ไม่ครบถ้วน"), "ไม่ครบถ้วน")))),

            Map.entry(new DocKey(SignatureModule.POSITION, 6), List.of(
                    new SignatureSlot("dean", "คณบดี",
                            "dean_signature_name", "DEAN", 1))),

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
        return SLOTS.getOrDefault(new DocKey(module, documentType), List.of())
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
