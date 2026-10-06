package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.ActingSigner;
import com.ecom.academic.repository.ActingSignerRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * ผู้รักษาการแทนของตำแหน่งลงนาม — แหล่งเดียวที่ตอบว่า "ตอนนี้ใครรักษาการแทนช่องนี้ และพิมพ์ว่าอะไร"
 *
 * <p>ตั้งครั้งเดียวต่อหนึ่งตำแหน่ง แล้วมีผลกับช่องของตำแหน่งนั้นในทุกเอกสาร ({@code slotKey} เดียวกับ
 * {@link SignatureAnchorRegistry}) ผลของการเปิดใช้:
 * <ul>
 *   <li>ผู้ลงนามเริ่มต้นของช่องนั้นเป็นผู้รักษาการแทน ({@link DocumentWorkflowConfigService#defaultSignerUserIds})</li>
 *   <li>ตัวค้นหาชื่อในแบบฟอร์มแนะนำผู้รักษาการแทนก่อน พร้อมตำแหน่งรักษาการแทน</li>
 *   <li>ขั้นลงนามที่สร้าง <em>หลังจากนี้</em> และมอบให้ผู้รักษาการแทน จำตำแหน่งรักษาการแทนไว้
 *       ({@code SignatureStep.actingPosition}) ซองที่ส่งไปก่อนแล้วไม่เปลี่ยน</li>
 * </ul>
 *
 * <p>ตำแหน่งที่พิมพ์คือ "รักษาการแทน" ต่อด้วยตำแหน่งเต็มที่แอดมินกรอก ไม่ย่อทั้งสองส่วน
 */
@Service
public class ActingSignerService {

    public static final String PREFIX = "รักษาการแทน";

    private static final String COLLEGE = "วิทยาลัยการคอมพิวเตอร์";

    /**
     * ตำแหน่งที่มีการรักษาการแทนได้ — ตำแหน่งบริหาร ไม่ใช่ผู้ยื่น กรรมการ หรือเจ้าหน้าที่
     *
     * @param example ตำแหน่งเต็มที่ใช้เมื่อหาจากข้อมูลในระบบไม่ได้ ({@link #suggestedPosition})
     */
    public record Role(String slotKey, String label, String example) {
    }

    public static final List<Role> ROLES = List.of(
            new Role("dean", "คณบดี", "คณบดี" + COLLEGE),
            new Role("associate_dean", "รองคณบดี", "รองคณบดี" + COLLEGE),
            new Role("head", "หัวหน้าสาขาวิชา", "หัวหน้าสาขาวิชา " + COLLEGE));

    /** ผู้รักษาการแทนที่มีผลอยู่ */
    public record Acting(String slotKey, UserDtls user, String printedPosition) {
    }

    /**
     * หนึ่งแถวในหน้าตั้งค่า
     *
     * @param positionTitle ตำแหน่งที่บันทึกไว้ หรือตำแหน่งที่ระบบเสนอเมื่อยังไม่เคยบันทึก
     * @param suggested     {@code positionTitle} มาจากระบบ ไม่ใช่ที่บันทึกไว้
     */
    public record Row(Role role, boolean active, Integer userId, String userName, String positionTitle,
            boolean suggested, LocalDateTime updatedAt, String updatedBy) {

        public String printedPosition() {
            return positionTitle == null || positionTitle.isBlank() ? "" : PREFIX + positionTitle;
        }
    }

    /** คำนำหน้าที่ผู้ใช้อาจพิมพ์มาเอง — ระบบเติม "รักษาการแทน" ให้เสมอ จึงตัดออกก่อนเก็บ */
    private static final Pattern TYPED_PREFIX = Pattern.compile("^(?:รักษาการแทน|รักษาการ|รก\\.)\\s*");

    /** สังกัดที่เป็นชื่อห้องปฏิบัติการ (ซิงก์มาจาก labName) ไม่ใช่ชื่อสาขาวิชา */
    private static final Pattern NOT_A_PROGRAMME = Pattern.compile("ห้องปฏิบัติการ|\\blab", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRAMME_PREFIX = Pattern.compile("^(?:สาขาวิชา|สาขา)\\s*");

    private final ActingSignerRepository repository;
    private final UserRepository userRepository;
    private final StaffMemberRepository staffMembers;

    public ActingSignerService(ActingSignerRepository repository, UserRepository userRepository,
            StaffMemberRepository staffMembers) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.staffMembers = staffMembers;
    }

    /**
     * ตำแหน่งเต็มที่ระบบเขียนให้ แอดมินไม่ต้องพิมพ์เอง — แก้ทับได้ถ้าไม่ตรง
     *
     * <ol>
     *   <li>หัวหน้าสาขาวิชา: ตามสาขาของผู้รักษาการแทน (ทะเบียนบุคลากร) — รักษาการแทนหัวหน้าสาขาของตัวเอง</li>
     *   <li>ตำแหน่งของผู้ดำรงตำแหน่งตัวจริงที่บันทึกไว้ในบัญชี ถ้ามีคนเดียว เช่น "คณบดีวิทยาลัยการคอมพิวเตอร์"</li>
     *   <li>ตำแหน่งเต็มตั้งต้นของตำแหน่งนั้น</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public String suggestedPosition(String slotKey, Integer actingUserId) {
        Role role = role(slotKey).orElse(null);
        if (role == null) {
            return "";
        }
        if ("head".equals(slotKey) && actingUserId != null) {
            String programme = staffMembers.findByUserId(actingUserId)
                    .map(staff -> headOf(staff.getDepartment()))
                    .orElse(null);
            if (programme != null) {
                return programme;
            }
        }
        List<String> held = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsEnable()))
                .map(u -> normalizePosition(u.getPositionTitle()))
                .filter(title -> title.startsWith(role.label()))
                .distinct()
                .toList();
        return held.size() == 1 ? held.get(0) : role.example();
    }

    /** "สาขาวิชาวิทยาการคอมพิวเตอร์" หรือ "วิทยาการคอมพิวเตอร์" → "หัวหน้าสาขาวิชาวิทยาการคอมพิวเตอร์" */
    public static String headOf(String department) {
        if (department == null || department.isBlank() || NOT_A_PROGRAMME.matcher(department).find()) {
            return null;
        }
        String programme = PROGRAMME_PREFIX.matcher(department.strip()).replaceFirst("").strip();
        return programme.isEmpty() ? null : "หัวหน้าสาขาวิชา" + programme;
    }

    public static boolean isActable(String slotKey) {
        return role(slotKey).isPresent();
    }

    private static Optional<Role> role(String slotKey) {
        return ROLES.stream().filter(r -> r.slotKey().equals(slotKey)).findFirst();
    }

    /**
     * ตำแหน่งเต็มสำหรับเก็บ: ยุบช่องว่าง และตัด "รักษาการแทน" ที่พิมพ์มาเองออก
     * ไม่อย่างนั้นเอกสารจะพิมพ์ "รักษาการแทนรักษาการแทนคณบดี..."
     */
    static String normalizePosition(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip().replaceAll("\\s+", " ");
        String previous;
        do {
            previous = value;
            value = TYPED_PREFIX.matcher(value).replaceFirst("").strip();
        } while (!value.equals(previous));
        return value;
    }

    /** ผู้รักษาการแทนของช่องนี้ ถ้าเปิดอยู่และบัญชียังใช้งานได้ */
    @Transactional(readOnly = true)
    public Optional<Acting> activeFor(String slotKey) {
        if (slotKey == null || !isActable(slotKey)) {
            return Optional.empty();
        }
        return repository.findBySlotKey(slotKey).flatMap(ActingSignerService::toActing);
    }

    /** ผู้รักษาการแทนที่เปิดอยู่ทั้งหมด แยกตามช่อง */
    @Transactional(readOnly = true)
    public Map<String, Acting> allActive() {
        return repository.findAllWithUsers().stream()
                .filter(a -> isActable(a.getSlotKey()))
                .map(ActingSignerService::toActing)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Acting::slotKey, Function.identity()));
    }

    private static Optional<Acting> toActing(ActingSigner a) {
        UserDtls user = a.getActingUser();
        if (!a.isActive() || user == null || !Boolean.TRUE.equals(user.getIsEnable())
                || a.getPositionTitle() == null || a.getPositionTitle().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Acting(a.getSlotKey(), user, PREFIX + a.getPositionTitle()));
    }

    /**
     * ตำแหน่งที่จำไว้กับขั้นลงนามที่กำลังสร้าง — มีค่าเฉพาะเมื่อผู้ลงนามคือผู้รักษาการแทนของช่องนี้ตอนนี้
     *
     * @return เช่น "รักษาการแทนคณบดีวิทยาลัยการคอมพิวเตอร์" หรือ null
     */
    public String actingPositionFor(String slotKey, Integer signerUserId) {
        if (signerUserId == null) {
            return null;
        }
        return activeFor(slotKey)
                .filter(acting -> signerUserId.equals(acting.user().getId()))
                .map(Acting::printedPosition)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Row> rows() {
        Map<String, ActingSigner> saved = repository.findAllWithUsers().stream()
                .collect(Collectors.toMap(ActingSigner::getSlotKey, Function.identity()));
        List<Row> rows = new ArrayList<>();
        for (Role role : ROLES) {
            ActingSigner a = saved.get(role.slotKey());
            if (a == null) {
                rows.add(new Row(role, false, null, null, suggestedPosition(role.slotKey(), null), true,
                        null, null));
                continue;
            }
            UserDtls user = a.getActingUser();
            Integer userId = user != null ? user.getId() : null;
            boolean hasPosition = a.getPositionTitle() != null && !a.getPositionTitle().isBlank();
            rows.add(new Row(role, a.isActive(),
                    userId,
                    user != null ? SignerNameResolver.printedName(user) : null,
                    hasPosition ? a.getPositionTitle() : suggestedPosition(role.slotKey(), userId),
                    !hasPosition,
                    a.getUpdatedAt(),
                    a.getUpdatedBy() != null ? a.getUpdatedBy().getName() : null));
        }
        return rows;
    }

    /**
     * บันทึกการรักษาการแทนของหนึ่งตำแหน่ง
     *
     * <p>ปิดใช้ได้เสมอแม้ข้อมูลไม่ครบ — ส่วนการเปิดใช้ต้องมีผู้รักษาการแทน ช่องตำแหน่งที่ว่าง
     * ระบบเขียนตำแหน่งเต็มให้ ({@link #suggestedPosition})
     *
     * @throws IllegalArgumentException พร้อมข้อความบอกแอดมินเมื่อข้อมูลใช้ไม่ได้
     */
    @Transactional
    public void save(String slotKey, boolean active, Integer userId, String positionTitle, UserDtls actor) {
        Role role = role(slotKey)
                .orElseThrow(() -> new IllegalArgumentException("ตำแหน่งนี้ตั้งผู้รักษาการแทนไม่ได้"));
        UserDtls user = userId == null ? null : userRepository.findById(userId).orElse(null);
        String position = normalizePosition(positionTitle);
        if (position.isEmpty() && user != null) {
            position = suggestedPosition(slotKey, user.getId());
        }

        if (active) {
            if (user == null) {
                throw new IllegalArgumentException("กรุณาเลือกผู้รักษาการแทน" + role.label());
            }
            if (!Boolean.TRUE.equals(user.getIsEnable())) {
                throw new IllegalArgumentException("บัญชีของ " + user.getName() + " ถูกปิดใช้งานอยู่");
            }
            if (position.isEmpty()) {
                throw new IllegalArgumentException("กรุณากรอกตำแหน่งเต็มที่รักษาการแทน" + role.label()
                        + " เช่น " + role.example());
            }
        }

        ActingSigner a = repository.findBySlotKey(slotKey).orElseGet(() -> new ActingSigner(slotKey));
        a.setActive(active);
        a.setActingUser(user);
        a.setPositionTitle(position.isEmpty() ? null : position);
        a.setUpdatedAt(LocalDateTime.now());
        a.setUpdatedBy(actor);
        repository.save(a);
    }
}
