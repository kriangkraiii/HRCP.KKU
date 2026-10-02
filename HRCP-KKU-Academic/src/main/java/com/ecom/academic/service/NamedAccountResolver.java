package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * บัญชีของคนที่ชื่ออยู่ในแบบฟอร์ม — กติกาเดียวทั้งการส่งเวียนลงนาม การตรวจรายชื่อกรรมการ และการเลื่อนสถานะ
 *
 * <p>ตัวค้นหาชื่อ (person_picker.js) เก็บรหัสบัญชีคู่ชื่อในช่อง {@code <ช่อง>__signer} ใช้รหัสนั้นก่อน
 * ข้อมูลที่บันทึกก่อนมีตัวค้นหาชื่อไม่มีรหัส จึงจับคู่จากชื่อที่พิมพ์ลงเอกสาร (หรือชื่อในทะเบียนบุคลากร)
 */
@Component
public class NamedAccountResolver {

    /** ทำไมชื่อนี้ผูกกับบัญชีไม่ได้ */
    public enum Problem {
        /** รหัสบัญชีที่เลือกไว้ถูกปิดหรือถูกลบ */
        ACCOUNT_GONE,
        /** ชื่อในเอกสารถูกแก้หลังเลือก ไม่ใช่ชื่อของบัญชีที่เลือก */
        NAME_CHANGED,
        /** ไม่มีบัญชีชื่อนี้ */
        NOT_FOUND,
        /** มีบัญชีชื่อนี้มากกว่าหนึ่งคน */
        AMBIGUOUS
    }

    /** ผลการจับคู่ — มีบัญชี หรือมีเหตุที่จับคู่ไม่ได้ อย่างใดอย่างหนึ่ง */
    public record Resolution(UserDtls account, Problem problem) {
    }

    /** รายชื่อกรรมการสามคน (เอกสารที่ 3, 4, 7) */
    public static final List<String> COMMITTEE_FIELDS = List.of(
            "committee_1_name", "committee_2_name", "committee_3_name");

    private final UserRepository userRepository;
    private final StaffMemberService staffMemberService;

    public NamedAccountResolver(UserRepository userRepository, StaffMemberService staffMemberService) {
        this.userRepository = userRepository;
        this.staffMemberService = staffMemberService;
    }

    public static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().replaceAll("\\s+", " ");
    }

    /**
     * บัญชีของชื่อนี้
     *
     * @param name     ชื่อที่พิมพ์ลงเอกสาร (ไม่ว่าง)
     * @param chosenId รหัสบัญชีจากตัวค้นหาชื่อ หรือ null สำหรับข้อมูลเก่า
     */
    public Resolution resolve(Object name, Object chosenId) {
        String printed = normalize(name);
        if (chosenId != null && !String.valueOf(chosenId).isBlank()) {
            UserDtls account = parseId(chosenId).flatMap(userRepository::findById)
                    .filter(u -> Boolean.TRUE.equals(u.getIsEnable()))
                    .orElse(null);
            if (account == null) {
                return new Resolution(null, Problem.ACCOUNT_GONE);
            }
            if (!normalize(SignerNameResolver.printedName(account)).equals(printed)) {
                return new Resolution(null, Problem.NAME_CHANGED);
            }
            return new Resolution(account, null);
        }
        List<UserDtls> matches = accountsNamed(printed);
        if (matches.isEmpty()) {
            return new Resolution(null, Problem.NOT_FOUND);
        }
        if (matches.size() > 1) {
            return new Resolution(null, Problem.AMBIGUOUS);
        }
        return new Resolution(matches.get(0), null);
    }

    /** บัญชีของชื่อในช่องนี้ของข้อมูลเอกสาร (อ่านรหัสจาก {@code <ช่อง>__signer} เอง) */
    public Resolution resolve(Map<String, ?> data, String nameField) {
        return resolve(data.get(nameField), data.get(DocumentFieldOwnership.signerIdField(nameField)));
    }

    /**
     * เหตุที่รายชื่อกรรมการสามคนใช้ไม่ได้ — ว่างคือทุกคนมีชื่อ ผูกกับบัญชีได้ และเป็นคนละบัญชีกัน
     * กรรมการทั้งสามคนลงนามแบบประเมินผลการสอน (เอกสารที่ 7) คนที่ไม่มีบัญชีจึงถูกแต่งตั้งไม่ได้
     */
    public List<String> committeeProblems(Map<String, ?> data) {
        List<String> problems = new ArrayList<>();
        Map<Integer, Integer> seatOf = new LinkedHashMap<>();
        for (int i = 1; i <= COMMITTEE_FIELDS.size(); i++) {
            String field = COMMITTEE_FIELDS.get(i - 1);
            String name = normalize(data.get(field));
            if (name.isEmpty()) {
                problems.add("กรรมการคนที่ " + i + ": ยังไม่ได้เลือก");
                continue;
            }
            Resolution r = resolve(data, field);
            if (r.problem() != null) {
                problems.add("กรรมการคนที่ " + i + " “" + name + "”: " + describe(r.problem()));
                continue;
            }
            Integer earlier = seatOf.putIfAbsent(r.account().getId(), i);
            if (earlier != null) {
                problems.add("กรรมการคนที่ " + i + " “" + name + "”: เป็นบัญชีเดียวกับกรรมการคนที่ " + earlier);
            }
        }
        return problems;
    }

    /**
     * กรรมการสองที่นั่งเป็นคนเดียวกัน — ตรวจได้ทันทีตอนบันทึก ไม่ต้องค้นบัญชี
     * เทียบทั้งรหัสบัญชีจากตัวค้นหาชื่อ และชื่อที่พิมพ์ลงเอกสาร (ข้อมูลเก่าไม่มีรหัส หรือมีที่นั่งเดียวที่มีรหัส)
     *
     * @return ข้อความบอกว่าคนที่เท่าไรซ้ำกับคนที่เท่าไร หรือ null เมื่อไม่ซ้ำ
     */
    public static String duplicateCommitteeSeat(Map<String, ?> data) {
        Map<String, Integer> seatOf = new LinkedHashMap<>();
        for (int i = 1; i <= COMMITTEE_FIELDS.size(); i++) {
            String field = COMMITTEE_FIELDS.get(i - 1);
            String id = normalize(data.get(DocumentFieldOwnership.signerIdField(field)));
            String name = normalize(data.get(field));
            for (String key : new String[] { id.isEmpty() ? null : "id:" + id, name.isEmpty() ? null : "name:" + name }) {
                if (key == null) {
                    continue;
                }
                Integer earlier = seatOf.putIfAbsent(key, i);
                if (earlier != null && earlier != i) {
                    return "กรรมการคนที่ " + i + " เป็นคนเดียวกับกรรมการคนที่ " + earlier
                            + " (" + name + ") — กรรมการสามคนต้องเป็นคนละคน กรุณาเลือกใหม่";
                }
            }
        }
        return null;
    }

    public static String describe(Problem problem) {
        return switch (problem) {
            case ACCOUNT_GONE -> "บัญชีที่เลือกไว้ถูกปิดหรือไม่มีในระบบแล้ว";
            case NAME_CHANGED -> "ชื่อไม่ตรงกับบัญชีที่เลือกไว้";
            case NOT_FOUND -> "ไม่พบบัญชีชื่อนี้ในระบบ";
            case AMBIGUOUS -> "มีบัญชีชื่อนี้มากกว่าหนึ่งคน";
        };
    }

    /** บัญชีที่เปิดใช้งานซึ่งชื่อที่พิมพ์ลงเอกสาร (หรือชื่อในทะเบียนบุคลากร) ตรงกับชื่อนี้ */
    private List<UserDtls> accountsNamed(String name) {
        Map<Integer, UserDtls> found = new LinkedHashMap<>();
        for (UserDtls u : userRepository.findAll()) {
            if (Boolean.TRUE.equals(u.getIsEnable()) && normalize(SignerNameResolver.printedName(u)).equals(name)) {
                found.put(u.getId(), u);
            }
        }
        for (com.ecom.academic.model.StaffMember s : staffMemberService.findAllWithAccounts()) {
            if (s.isSignable() && Boolean.TRUE.equals(s.getUser().getIsEnable())
                    && normalize(s.getDisplayName()).equals(name)) {
                found.putIfAbsent(s.getUser().getId(), s.getUser());
            }
        }
        return new ArrayList<>(found.values());
    }

    private static Optional<Integer> parseId(Object raw) {
        try {
            return Optional.of(Integer.valueOf(String.valueOf(raw).strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
