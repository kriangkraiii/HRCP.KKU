package com.ecom.academic.service;

import java.util.List;

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
 *   <li><b>{@code staffRole} and {@code staffType} are never written after the row
 *       is created.</b> They are the administrator's answers to questions the
 *       directory cannot answer; overwriting them nightly would silently undo real
 *       decisions.</li>
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
     * What an imported person starts as.
     *
     * <p>Deliberately the neutral one. The directory cannot tell us who the dean
     * is — {@code managePosition} upstream is free text — and guessing a role that
     * decides who signs an official document is worse than leaving it to be set.
     */
    private static final String DEFAULT_ROLE = "GENERAL";

    private final StaffMemberRepository staffRepo;
    private final FsFacultyRepository facultyRepo;

    public StaffDirectorySync(StaffMemberRepository staffRepo, FsFacultyRepository facultyRepo) {
        this.staffRepo = staffRepo;
        this.facultyRepo = facultyRepo;
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
                    staffRepo.save(adoptable);
                    adopted++;
                    continue;
                }

                // Someone who left before we ever imported them is not worth
                // adding just to mark them inactive.
                if (!faculty.isActive()) {
                    continue;
                }

                staffRepo.save(createFrom(faculty));
                created++;
                continue;
            }

            boolean changed = applyIdentity(existing, faculty);

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

        Result result = new Result(created, updated, adopted, deactivated);
        if (result.touchedAnything()) {
            log.info("Staff list synced from faculty directory: {}", result);
        }
        return result;
    }

    /**
     * A row for the same person that predates the import.
     *
     * <p>Matching on a name is too weak to create anything, but it is the only key
     * available for linking up a list that was typed in before this existed. It
     * runs once per person — after that the id does the work. An ambiguous match
     * (two people with the same name) is left alone rather than guessed at.
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
    public record Result(int created, int updated, int adopted, int deactivated) {

        public boolean touchedAnything() {
            return created > 0 || updated > 0 || adopted > 0 || deactivated > 0;
        }

        /** Thai summary for the sync page. */
        public String describe() {
            if (!touchedAnything()) {
                return "รายชื่อบุคลากรตรงกับต้นทางอยู่แล้ว";
            }
            StringBuilder sb = new StringBuilder("บุคลากร:");
            if (created > 0) {
                sb.append(" เพิ่มใหม่ ").append(created).append(" คน");
            }
            if (adopted > 0) {
                sb.append(" เชื่อมกับรายชื่อเดิม ").append(adopted).append(" คน");
            }
            if (updated > 0) {
                sb.append(" อัปเดต ").append(updated).append(" คน");
            }
            if (deactivated > 0) {
                sb.append(" ปิดใช้งาน ").append(deactivated).append(" คน");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return created + " created, " + adopted + " adopted, "
                    + updated + " updated, " + deactivated + " deactivated";
        }
    }
}
