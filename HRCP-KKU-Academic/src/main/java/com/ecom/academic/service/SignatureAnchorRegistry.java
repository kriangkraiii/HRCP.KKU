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
     */
    public record SignatureSlot(
            String slotKey,
            String roleLabel,
            String anchorPlaceholder,
            String defaultStaffRole,
            int order) {
    }

    private record DocKey(SignatureModule module, int documentType) {
    }

    private static final String APPLICANT = null;

    private static final Map<DocKey, List<SignatureSlot>> SLOTS = Map.ofEntries(

            // ---------------------------------------------------------- Phase 1
            Map.entry(new DocKey(SignatureModule.ACADEMIC, 0), List.of(
                    new SignatureSlot("applicant", "ผู้ขอประเมินผลการสอน",
                            "applicant_name", APPLICANT, 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 1), List.of(
                    new SignatureSlot("applicant", "ผู้ขอรับการประเมิน",
                            "applicant_name", APPLICANT, 1),
                    new SignatureSlot("hr", "นักทรัพยากรบุคคล",
                            "hr_staff_name", "HR", 2))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 2), List.of(
                    new SignatureSlot("head", "หัวหน้าสาขาวิชา",
                            "department_head", "HEAD", 1),
                    new SignatureSlot("associate_dean", "รองคณบดี",
                            "associate_dean_name", "DEAN", 2),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 3),
                    new SignatureSlot("hr", "เจ้าหน้าที่บริหารงาน",
                            "hr_staff_name", "HR", 4))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 3), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 4), List.of(
                    new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1))),

            // Phase 1 doc 5 is a bare suggestions textbox ({{suggestions_text}})
            // with no signature block at all, so it is deliberately absent.

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 6), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะกรรมการประเมิน",
                            "committee_1_name", "COMMITTEE", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 7), List.of(
                    new SignatureSlot("committee_chair", "ประธานคณะอนุกรรมการ",
                            "committee_president_name", "COMMITTEE", 1))),

            Map.entry(new DocKey(SignatureModule.ACADEMIC, 8), List.of(
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

            Map.entry(new DocKey(SignatureModule.POSITION, 5), List.of(
                    new SignatureSlot("head", "หัวหน้าสาขาวิชา",
                            "department_head_name", "HEAD", 1),
                    new SignatureSlot("dean", "คณบดี",
                            "dean_name", "DEAN", 2))),

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
