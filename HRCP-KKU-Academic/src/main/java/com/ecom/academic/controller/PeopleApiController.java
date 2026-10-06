package com.ecom.academic.controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.service.ExternalSignerService;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.academic.service.StaffMemberService;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * รายชื่อผู้ลงนามสำหรับตัวค้นหาชื่อ (person_picker.js) — ทุกบัญชีที่เปิดใช้งานในระบบ
 *
 * <p>ช่องชื่อผู้ลงนามในแบบฟอร์มเลือกได้เฉพาะคนในรายการนี้ ไม่ใช่ข้อความที่พิมพ์เอง คนที่เลือกคือผู้ลงนาม
 * ตำแหน่งนั้น ({@code SignatureWorkflowService.signersNamedInForm}) ดู docs/PLAN-signer-picker.md
 */
@RestController
@RequestMapping("/api/people")
public class PeopleApiController {

    private static final Set<String> STAFF = Set.of("ROLE_ADMIN", "ROLE_STAFF");
    private static final int MAX_RESULTS = 50;

    /**
     * หนึ่งคนในรายการ
     *
     * @param name        ชื่อที่พิมพ์ลงเอกสาร (คำนำหน้า + ชื่อ-สกุล) — ค่าที่ช่องชื่อเก็บ
     * @param position    ตำแหน่งที่พิมพ์ในเอกสาร ใช้เติมช่องตำแหน่งข้างชื่อ อาจว่าง
     * @param email       ปิดบางส่วนเมื่อผู้ถามไม่ใช่เจ้าหน้าที่
     * @param recommended มีบทบาทตรงกับตำแหน่งที่ถาม (ทะเบียนบุคลากร) — ขึ้นก่อน
     * @param acting      ผู้รักษาการแทนของช่องที่ถาม — {@code position} เป็นตำแหน่งรักษาการแทนเต็ม
     */
    public record Person(Integer userId, String name, String position, String affiliation, String email,
            boolean recommended, boolean external, boolean acting) {
    }

    private final UserRepository users;
    private final StaffMemberService staffMembers;
    private final ExternalSignerService externalSigners;
    private final com.ecom.academic.service.SignerBriefing briefing;
    private final com.ecom.academic.repository.AcademicRequestRepository academicRequests;
    private final com.ecom.academic.repository.PositionRequestRepository positionRequests;

    /** ผู้รักษาการแทน — ไม่มีก็แนะนำตามบทบาทอย่างเดียวเหมือนเดิม */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.ecom.academic.service.ActingSignerService actingSigners;

    public PeopleApiController(UserRepository users, StaffMemberService staffMembers,
            ExternalSignerService externalSigners, com.ecom.academic.service.SignerBriefing briefing,
            com.ecom.academic.repository.AcademicRequestRepository academicRequests,
            com.ecom.academic.repository.PositionRequestRepository positionRequests) {
        this.users = users;
        this.staffMembers = staffMembers;
        this.externalSigners = externalSigners;
        this.briefing = briefing;
        this.academicRequests = academicRequests;
        this.positionRequests = positionRequests;
    }

    /**
     * ผู้ลงนามภายนอกที่จะเชิญ พร้อมเอกสารที่เชิญมาลงนาม (ไม่บังคับ) — ใช้เขียนหนังสือเชิญให้บอกได้ว่า
     * เป็นคำร้องของใคร เรื่องอะไร ท่านอยู่ในฐานะอะไร
     *
     * @param module       ACADEMIC หรือ POSITION
     * @param field        ช่องชื่อผู้ลงนามในแบบฟอร์ม เช่น committee_2_name
     */
    public record ExternalInvite(String title, String firstName, String lastName, String email, String affiliation,
            String module, Long requestId, Integer documentType, String field) {
    }

    /**
     * @param q    คำค้น (ชื่อ อีเมล ตำแหน่ง หน่วยงาน) — ว่างคืนผู้ที่มีบทบาทตรงก่อน
     * @param role บทบาทของตำแหน่ง ({@code staff_member.staff_role} เช่น DEAN, HEAD, HR) — ไม่บังคับ
     * @param module       ACADEMIC/POSITION ของแบบฟอร์มที่ถาม — คู่กับ documentType และ field ใช้หาว่า
     *                     ช่องนี้คือตำแหน่งลงนามไหน แล้วแนะนำผู้รักษาการแทนของตำแหน่งนั้นก่อน
     * @param field        ช่องชื่อผู้ลงนามในแบบฟอร์ม เช่น dean_name
     */
    @GetMapping
    public ResponseEntity<List<Person>> search(@RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "documentType", required = false) Integer documentType,
            @RequestParam(value = "field", required = false) String field,
            Principal principal) {
        UserDtls caller = principal == null ? null : users.findByEmail(principal.getName());
        if (caller == null) {
            return ResponseEntity.status(401).build();
        }
        boolean fullEmail = STAFF.contains(caller.getRole());
        Set<Integer> recommended = new HashSet<>();
        if (role != null && !role.isBlank()) {
            for (StaffMember s : staffMembers.findByRoleWithAccountStatus(role.strip())) {
                if (s.isSignable()) {
                    recommended.add(s.getUser().getId());
                }
            }
        }
        String needle = normalize(q);
        com.ecom.academic.service.ActingSignerService.Acting acting = actingFor(module, documentType, field);

        List<Person> found = new ArrayList<>();
        for (UserDtls u : users.findAll()) {
            if (!Boolean.TRUE.equals(u.getIsEnable())) {
                continue;
            }
            Person p = acting != null && acting.user().getId().equals(u.getId())
                    ? actingPerson(u, acting, fullEmail)
                    : toPerson(u, recommended.contains(u.getId()), fullEmail);
            if (needle.isEmpty() ? p.recommended() : matches(u, p, needle)) {
                found.add(p);
            }
        }
        found.sort(Comparator.comparing(Person::acting).reversed()
                .thenComparing(Comparator.comparing(Person::recommended).reversed())
                .thenComparing(Person::name, Comparator.nullsLast(String::compareTo)));
        return ResponseEntity.ok(found.size() > MAX_RESULTS ? found.subList(0, MAX_RESULTS) : found);
    }

    /** เพิ่มผู้ลงนามจากนอก มข. — สร้างบัญชีภายนอกและส่งอีเมลเชิญ (ถ้ามีบัญชีอยู่แล้วคืนบัญชีเดิม) */
    @PostMapping("/external")
    public ResponseEntity<?> addExternal(@RequestBody ExternalInvite invite, Principal principal) {
        UserDtls caller = principal == null ? null : users.findByEmail(principal.getName());
        if (caller == null) {
            return ResponseEntity.status(401).build();
        }
        if (caller.isExternal()) {
            return ResponseEntity.status(403).build();
        }
        try {
            UserDtls signer = externalSigners.invite(
                    new ExternalSignerService.Invite(invite.title(), invite.firstName(), invite.lastName(),
                            invite.email(), invite.affiliation()),
                    caller, briefingFor(invite, caller));
            return ResponseEntity.ok(toPerson(signer, false, STAFF.contains(caller.getRole())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * เรื่องที่เชิญมา — เฉพาะเจ้าหน้าที่ หรือผู้ยื่นของคำร้องนั้นเอง ชื่อผู้ยื่นจะอยู่ในอีเมลเชิญ
     * จึงห้ามใครส่งรหัสคำร้องของคนอื่นมาให้ระบบเขียนชื่อนั้นส่งออกไปยังอีเมลที่ตัวเองกรอก
     */
    private com.ecom.academic.service.SignerBriefing.Briefing briefingFor(ExternalInvite invite, UserDtls caller) {
        if (invite.module() == null || invite.requestId() == null || invite.documentType() == null) {
            return null;
        }
        com.ecom.academic.model.SignatureModule module;
        try {
            module = com.ecom.academic.model.SignatureModule.valueOf(invite.module().strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        boolean staff = STAFF.contains(caller.getRole());
        if (!staff) {
            Integer owner = module == com.ecom.academic.model.SignatureModule.ACADEMIC
                    ? academicRequests.findById(invite.requestId()).map(r -> r.getApplicant().getId()).orElse(null)
                    : positionRequests.findById(invite.requestId()).map(r -> r.getApplicant().getId()).orElse(null);
            if (owner == null || !owner.equals(caller.getId())) {
                return null;
            }
        }
        try {
            return briefing.forField(module, invite.requestId(), invite.documentType(), invite.field()).orElse(null);
        } catch (RuntimeException e) {
            return null; // หนังสือเชิญยังส่งได้แบบไม่มีรายละเอียดเรื่อง
        }
    }

    private static Person toPerson(UserDtls u, boolean recommended, boolean fullEmail) {
        return new Person(u.getId(), SignerNameResolver.printedName(u), u.getPositionTitle(), u.getAffiliation(),
                fullEmail ? u.getEmail() : maskEmail(u.getEmail()), recommended, u.isExternal(), false);
    }

    /** ตำแหน่งคือ "รักษาการแทน..." เต็ม — ตัวค้นหาชื่อเติมค่านี้ลงช่องตำแหน่งข้างชื่อ */
    private static Person actingPerson(UserDtls u, com.ecom.academic.service.ActingSignerService.Acting acting,
            boolean fullEmail) {
        return new Person(u.getId(), SignerNameResolver.printedName(u), acting.printedPosition(), u.getAffiliation(),
                fullEmail ? u.getEmail() : maskEmail(u.getEmail()), true, u.isExternal(), true);
    }

    /** ผู้รักษาการแทนของช่องชื่อที่ถาม — ไม่บอกว่าเป็นช่องไหนก็ไม่มี */
    private com.ecom.academic.service.ActingSignerService.Acting actingFor(String module, Integer documentType,
            String field) {
        if (actingSigners == null || module == null || documentType == null || field == null) {
            return null;
        }
        com.ecom.academic.model.SignatureModule parsed;
        try {
            parsed = com.ecom.academic.model.SignatureModule.valueOf(module.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String slotKey = com.ecom.academic.service.SignatureAnchorRegistry.slotKeyForNameField(
                parsed, documentType, field);
        return slotKey == null ? null : actingSigners.activeFor(slotKey).orElse(null);
    }

    private static boolean matches(UserDtls u, Person p, String needle) {
        for (String hay : new String[] { p.name(), u.getEmail(), u.getPositionTitle(), u.getAffiliation(),
                u.getFirstNameEn() + " " + u.getLastNameEn() }) {
            if (normalize(hay).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** ผู้ยื่นเห็นอีเมลแค่พอแยกคนชื่อซ้ำ ไม่ใช่ทั้งรายชื่ออีเมลของบุคลากรทั้งระบบ */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        return (local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***") + email.substring(at);
    }
}
