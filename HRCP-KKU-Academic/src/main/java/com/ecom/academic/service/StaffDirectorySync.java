package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.service.EnglishNameSplitter;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Keeps the staff list used by documents in step with the synced faculty
 * directory, so nobody has to retype a name the system already knows.
 *
 * <p>The two tables answer different questions and that shapes every rule here.
 * {@code fs_faculty} is a mirror of an upstream system: it says who the lecturers
 * are, and this application may not argue with it. {@code staff_member} is a
 * local editorial decision: who signs which document, who chairs a committee.
 * The directory has no opinion on that.
 *
 * <p>So the split is:
 * <ul>
 *   <li><b>Identity</b> — name, academic title, department — is refreshed from the
 *       directory on every sync. A promotion from ผศ. to รศ. upstream should reach
 *       the documents without anyone editing anything.</li>
 *   <li><b>Auto-linking</b> — ties the staff row to an active {@link UserDtls} login
 *       account by matching e-mail address (or exact name fallback) so the person can
 *       immediately participate in e-signature workflows.</li>
 *   <li><b>Auto-role</b> — maps executive positions (e.g. คณบดี, รองคณบดี) to DEAN / HEAD
 *       roles automatically if the role hasn't been manually customized.</li>
 *   <li><b>Nothing is ever deleted.</b> Someone who leaves is marked inactive:
 *       documents already issued name these people, and deleting the row would
 *       break records that are supposed to be permanent.</li>
 *   <li><b>Hand-entered rows are untouched.</b> External committee members and
 *       anyone else not in the directory keep {@code fsUserId == null} and are
 *       skipped entirely.</li>
 * </ul>
 */
@Service
public class StaffDirectorySync {

    private static final Logger log = LoggerFactory.getLogger(StaffDirectorySync.class);

    /**
     * What an imported person starts as when no executive position is detected.
     */
    public static final String DEFAULT_ROLE = "GENERAL";

    private final StaffMemberRepository staffRepo;
    private final FsFacultyRepository facultyRepo;
    private final UserRepository userRepo;

    public StaffDirectorySync(
            StaffMemberRepository staffRepo,
            FsFacultyRepository facultyRepo,
            UserRepository userRepo) {
        this.staffRepo = staffRepo;
        this.facultyRepo = facultyRepo;
        this.userRepo = userRepo;
    }

    /**
     * Brings the staff list in line with the directory.
     *
     * <p>Runs in its own transaction: it is called at the tail of a sync that has
     * already committed its own work, and a failure here must not roll that back —
     * the faculty data is still correct even if this step fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result importFromDirectory() {
        int created = 0;
        int updated = 0;
        int adopted = 0;
        int deactivated = 0;
        int linked = 0;
        int rolesUpdated = 0;

        for (FsFaculty faculty : facultyRepo.findAll()) {
            if (faculty.getFsUserId() == null) {
                continue;
            }

            StaffMember existing = staffRepo.findByFsUserId(faculty.getFsUserId()).orElse(null);

            if (existing == null) {
                StaffMember adoptable = findHandEnteredMatch(faculty);
                if (adoptable != null) {
                    adoptable.setFsUserId(faculty.getFsUserId());
                    applyIdentity(adoptable, faculty);
                    if (applyAccountLink(adoptable, faculty)) {
                        linked++;
                    }
                    if (applyRole(adoptable, faculty)) {
                        rolesUpdated++;
                    }
                    staffRepo.save(adoptable);
                    adopted++;
                    continue;
                }

                // Someone who left before we ever imported them is not worth
                // adding just to mark them inactive.
                if (!faculty.isActive()) {
                    continue;
                }

                StaffMember newStaff = createFrom(faculty);
                if (newStaff.getUser() != null) {
                    linked++;
                }
                if (!DEFAULT_ROLE.equalsIgnoreCase(newStaff.getStaffRole())) {
                    rolesUpdated++;
                }
                staffRepo.save(newStaff);
                created++;
                continue;
            }

            boolean changed = applyIdentity(existing, faculty);

            if (applyAccountLink(existing, faculty)) {
                linked++;
                changed = true;
            }

            if (applyRole(existing, faculty)) {
                rolesUpdated++;
                changed = true;
            }

            boolean shouldBeActive = faculty.isActive();
            if (Boolean.TRUE.equals(existing.getIsActive()) && !shouldBeActive) {
                existing.setIsActive(false);
                deactivated++;
                changed = true;
            } else if (!Boolean.TRUE.equals(existing.getIsActive()) && shouldBeActive) {
                // Back on the payroll — the row was only parked, not retired.
                existing.setIsActive(true);
                changed = true;
            }

            if (changed) {
                staffRepo.save(existing);
                updated++;
            }
        }

        Result result = new Result(created, updated, adopted, deactivated, linked, rolesUpdated);
        if (result.touchedAnything()) {
            log.info("Staff list synced from faculty directory: {}", result);
        }
        return result;
    }

    /**
     * Maps management position string (from FS Directory / computing.kku.ac.th) to a StaffRole.
     */
    public static String resolveRoleFromPosition(String managePosition) {
        if (managePosition == null || managePosition.isBlank()) {
            return DEFAULT_ROLE;
        }
        String pos = managePosition.trim();
        // คณบดี (ที่ไม่ใช่รองคณบดี หรือผู้ช่วยคณบดี)
        if (pos.contains("คณบดี") && !pos.contains("รอง") && !pos.contains("ผู้ช่วย")) {
            return "DEAN";
        }
        // รองคณบดี, ผู้ช่วยคณบดี, หัวหน้าสาขา, ประธานหลักสูตร, ผู้อำนวยการ
        if (pos.contains("รองคณบดี") || pos.contains("ผู้ช่วยคณบดี") || pos.contains("หัวหน้า") || pos.contains("ประธาน") || pos.contains("ผู้อำนวยการ")) {
            return "HEAD";
        }
        if (pos.contains("กรรมการ")) {
            return "COMMITTEE";
        }
        if (pos.contains("เจ้าหน้าที่") || pos.contains("HR") || pos.contains("บุคคล")) {
            return "HR";
        }
        return DEFAULT_ROLE;
    }

    /**
     * Finds a matching UserDtls account using Email (primary) or Name (fallback).
     */
    public UserDtls findMatchingUser(FsFaculty faculty, StaffMember staff) {
        // 1. Match by email (most authoritative)
        if (faculty != null && !isBlank(faculty.getEmail())) {
            String email = faculty.getEmail().trim().toLowerCase();
            UserDtls byEmail = userRepo.findByEmail(email);
            if (byEmail != null) {
                return byEmail;
            }
        }

        // 2. Match by Thai Name
        String fname = staff != null && !isBlank(staff.getFirstName()) ? staff.getFirstName().trim() : (faculty != null ? faculty.getFirstName() : null);
        String lname = staff != null && !isBlank(staff.getLastName()) ? staff.getLastName().trim() : (faculty != null ? faculty.getLastName() : null);
        if (!isBlank(fname) && !isBlank(lname)) {
            List<UserDtls> byName = userRepo.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(fname.trim(), lname.trim());
            if (byName.size() == 1) {
                return byName.get(0);
            }
        }

        // 3. Match by English Name
        String fnameEn = staff != null && !isBlank(staff.getFirstNameEn()) ? staff.getFirstNameEn().trim() : null;
        String lnameEn = staff != null && !isBlank(staff.getLastNameEn()) ? staff.getLastNameEn().trim() : null;
        if (!isBlank(fnameEn) && !isBlank(lnameEn)) {
            List<UserDtls> byNameEn = userRepo.findByFirstNameEnIgnoreCaseAndLastNameEnIgnoreCase(fnameEn, lnameEn);
            if (byNameEn.size() == 1) {
                return byNameEn.get(0);
            }
        }

        return null;
    }

    /**
     * Automatically links staff member to a matching UserDtls account if not already linked.
     */
    public boolean applyAccountLink(StaffMember staff, FsFaculty faculty) {
        if (staff.getUser() != null) {
            return false; // Already linked, preserve existing link
        }
        UserDtls matched = findMatchingUser(faculty, staff);
        if (matched != null) {
            // Check if already claimed by another staff row
            Optional<StaffMember> conflict = staffRepo.findByUserIdAndIdNot(matched.getId(), staff.getId() == null ? -1L : staff.getId());
            if (conflict.isEmpty()) {
                staff.setUser(matched);
                log.info("Auto-linked staff member {} to user account {}", staff.getDisplayName(), matched.getEmail());
                return true;
            } else {
                log.warn("Account {} is already linked to staff member {} (skipping auto-link for {})",
                        matched.getEmail(), conflict.get().getDisplayName(), staff.getDisplayName());
            }
        }
        return false;
    }

    /**
     * Automatically maps executive position to role if not manually set.
     */
    public boolean applyRole(StaffMember staff, FsFaculty faculty) {
        if (faculty == null || isBlank(faculty.getManagePosition())) {
            return false;
        }
        if (staff.getStaffRole() == null || DEFAULT_ROLE.equalsIgnoreCase(staff.getStaffRole())) {
            String resolvedRole = resolveRoleFromPosition(faculty.getManagePosition());
            if (!DEFAULT_ROLE.equalsIgnoreCase(resolvedRole) && !resolvedRole.equalsIgnoreCase(staff.getStaffRole())) {
                staff.setStaffRole(resolvedRole);
                log.info("Auto-assigned role {} to staff member {} based on position '{}'",
                        resolvedRole, staff.getDisplayName(), faculty.getManagePosition());
                return true;
            }
        }
        return false;
    }

    /**
     * A row for the same person that predates the import.
     */
    private StaffMember findHandEnteredMatch(FsFaculty faculty) {
        if (isBlank(faculty.getFirstName()) || isBlank(faculty.getLastName())) {
            return null;
        }
        List<StaffMember> matches = staffRepo.findByFsUserIdIsNullAndFirstNameIgnoreCaseAndLastNameIgnoreCase(
                faculty.getFirstName().trim(), faculty.getLastName().trim());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private StaffMember createFrom(FsFaculty faculty) {
        StaffMember staff = new StaffMember();
        staff.setFsUserId(faculty.getFsUserId());
        staff.setStaffRole(DEFAULT_ROLE);
        staff.setIsActive(true);
        applyIdentity(staff, faculty);
        applyRole(staff, faculty);
        applyAccountLink(staff, faculty);
        return staff;
    }

    /**
     * Copies the fields the directory owns.
     *
     * @return true when something actually changed, so an unchanged row is not
     *         rewritten on every nightly run
     */
    private boolean applyIdentity(StaffMember staff, FsFaculty faculty) {
        boolean changed = false;

        if (notBlankAndDiffers(staff.getFirstName(), faculty.getFirstName())) {
            staff.setFirstName(faculty.getFirstName().trim());
            changed = true;
        }
        if (notBlankAndDiffers(staff.getLastName(), faculty.getLastName())) {
            staff.setLastName(faculty.getLastName().trim());
            changed = true;
        }
        if (notBlankAndDiffers(staff.getAcademicTitle(), faculty.getPositionTitle())) {
            staff.setAcademicTitle(faculty.getPositionTitle().trim());
            changed = true;
        }
        if (notBlankAndDiffers(staff.getAcademicTitleEn(), faculty.getPositionEn())) {
            staff.setAcademicTitleEn(faculty.getPositionEn().trim());
            changed = true;
        }

        // The upstream record has no split English name, only the combined
        // name_en, so it is derived. Rewritten on every run like the Thai name
        // above — a hand edit here does not survive the next sync.
        EnglishNameSplitter.Parts english = EnglishNameSplitter.split(faculty.getNameEn());
        if (notBlankAndDiffers(staff.getFirstNameEn(), english.firstName())) {
            staff.setFirstNameEn(english.firstName());
            changed = true;
        }
        if (notBlankAndDiffers(staff.getLastNameEn(), english.lastName())) {
            staff.setLastNameEn(english.lastName());
            changed = true;
        }
        // The upstream record has no department as such; the lab is the closest
        // thing it carries, and only fills a blank rather than replacing whatever
        // an administrator wrote.
        if (isBlank(staff.getDepartment()) && !isBlank(faculty.getLabName())) {
            staff.setDepartment(faculty.getLabName().trim());
            changed = true;
        }

        return changed;
    }

    private boolean notBlankAndDiffers(String current, String incoming) {
        return !isBlank(incoming) && !incoming.trim().equals(current);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** What one run did, for the log and the admin screen. */
    public record Result(int created, int updated, int adopted, int deactivated, int linked, int rolesUpdated) {

        public boolean touchedAnything() {
            return created > 0 || updated > 0 || adopted > 0 || deactivated > 0 || linked > 0 || rolesUpdated > 0;
        }

        /** Thai summary for the sync page. */
        public String describe() {
            if (!touchedAnything()) {
                return "รายชื่อบุคลากรตรงกับต้นทางและผูกบัญชีเรียบร้อยแล้ว";
            }
            StringBuilder sb = new StringBuilder("บุคลากร:");
            if (created > 0) {
                sb.append(" เพิ่มใหม่ ").append(created).append(" คน");
            }
            if (adopted > 0) {
                sb.append(" เชื่อมกับรายชื่อเดิม ").append(adopted).append(" คน");
            }
            if (linked > 0) {
                sb.append(" ผูกบัญชีผู้ใช้ ").append(linked).append(" คน");
            }
            if (rolesUpdated > 0) {
                sb.append(" อัปเดตบทบาทบริหาร ").append(rolesUpdated).append(" คน");
            }
            if (updated > 0) {
                sb.append(" อัปเดตข้อมูล ").append(updated).append(" คน");
            }
            if (deactivated > 0) {
                sb.append(" ปิดใช้งาน ").append(deactivated).append(" คน");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return created + " created, " + adopted + " adopted, "
                    + updated + " updated, " + deactivated + " deactivated, "
                    + linked + " linked, " + rolesUpdated + " rolesUpdated";
        }
    }
}

