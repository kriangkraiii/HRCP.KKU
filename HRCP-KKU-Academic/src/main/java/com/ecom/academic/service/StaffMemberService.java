package com.ecom.academic.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.repository.UserRepository;

@Service
public class StaffMemberService {

    private final StaffMemberRepository staffMemberRepository;
    private final StaffDirectorySync staffDirectorySync;
    private final FsFacultyRepository facultyRepository;
    private final UserRepository userRepository;

    public StaffMemberService(
            StaffMemberRepository staffMemberRepository,
            StaffDirectorySync staffDirectorySync,
            FsFacultyRepository facultyRepository,
            UserRepository userRepository) {
        this.staffMemberRepository = staffMemberRepository;
        this.staffDirectorySync = staffDirectorySync;
        this.facultyRepository = facultyRepository;
        this.userRepository = userRepository;
    }

    /**
     * DTO summarizing an on-demand auto-link execution.
     */
    public record AutoLinkReport(int totalScanned, int newlyLinked, int rolesUpdated) {
        public boolean hasChanges() {
            return newlyLinked > 0 || rolesUpdated > 0;
        }

        public String describe() {
            if (!hasChanges()) {
                return "บัญชีบุคลากรทั้งหมดได้รับการผูกข้อมูลและตรวจสอบบทบาทเรียบร้อยแล้ว";
            }
            StringBuilder sb = new StringBuilder("ดำเนินการสำเร็จ:");
            if (newlyLinked > 0) {
                sb.append(" ผูกบัญชีผู้ใช้ใหม่ ").append(newlyLinked).append(" คน");
            }
            if (rolesUpdated > 0) {
                sb.append(" ปรับบทบาทผู้บริหาร ").append(rolesUpdated).append(" คน");
            }
            return sb.toString();
        }
    }

    /**
     * Scans all active staff members and auto-links them to matching UserDtls accounts
     * while also resolving executive roles if currently GENERAL.
     */
    @Transactional
    public AutoLinkReport autoLinkAllAccounts() {
        List<StaffMember> allStaff = staffMemberRepository.findByIsActiveTrueOrderByFirstNameAscLastNameAsc();
        int totalScanned = allStaff.size();
        int newlyLinked = 0;
        int rolesUpdated = 0;

        for (StaffMember staff : allStaff) {
            boolean changed = false;
            FsFaculty faculty = null;
            if (staff.getFsUserId() != null) {
                faculty = facultyRepository.findById(staff.getFsUserId()).orElse(null);
            }

            if (staff.getUser() == null) {
                if (staffDirectorySync.applyAccountLink(staff, faculty)) {
                    newlyLinked++;
                    changed = true;
                }
            }

            if (faculty != null && staffDirectorySync.applyRole(staff, faculty)) {
                rolesUpdated++;
                changed = true;
            }

            if (changed) {
                staffMemberRepository.save(staff);
            }
        }

        return new AutoLinkReport(totalScanned, newlyLinked, rolesUpdated);
    }

    public List<StaffMember> findAll() {
        return staffMemberRepository.findByIsActiveTrueOrderByFirstNameAscLastNameAsc();
    }

    /** {@link #findAll()} with login accounts attached, for views that show link status. */
    public List<StaffMember> findAllWithAccounts() {
        return staffMemberRepository.findAllActiveWithUser();
    }

    public Optional<StaffMember> findById(Long id) {
        return staffMemberRepository.findById(id);
    }

    public List<StaffMember> findByRole(String role) {
        return staffMemberRepository.findByStaffRoleAndIsActiveTrueOrderByFirstNameAscLastNameAsc(role);
    }

    public StaffMember save(StaffMember staffMember) {
        return staffMemberRepository.save(staffMember);
    }

    /**
     * Everyone already recorded under this name, active or not.
     *
     * <p>Used to warn before adding what is probably the same person again.
     * Inactive rows count: re-adding someone who was retired is exactly the case
     * worth catching, since the old row is still named in past documents.
     */
    public List<StaffMember> findByName(String firstName, String lastName) {
        if (firstName == null || lastName == null) {
            return List.of();
        }
        return staffMemberRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                firstName.trim(), lastName.trim());
    }

    public void softDelete(Long id) {
        staffMemberRepository.findById(id).ifPresent(staff -> {
            staff.setIsActive(false);
            staffMemberRepository.save(staff);
        });
    }

    /**
     * Holders of a role who can actually be sent a signature request.
     *
     * <p>Filtered to those with a linked login account. Someone without one can
     * still be printed into a document as a name, but assigning them a signing
     * step would produce a step no one can ever complete — the request would
     * simply stall with no way forward.
     */
    public List<StaffMember> findSignableByRole(String role) {
        return staffMemberRepository.findActiveByRoleWithUser(role).stream()
                .filter(StaffMember::isSignable)
                .toList();
    }

    /**
     * Everyone holding a role, whether or not they can sign.
     *
     * <p>The signer picker shows both: hiding accountless people entirely would
     * leave an administrator staring at a short list with no explanation of why
     * the person they expected is missing.
     */
    public List<StaffMember> findByRoleWithAccountStatus(String role) {
        return staffMemberRepository.findActiveByRoleWithUser(role);
    }

    /** The staff record for a login account, if that account is linked to one. */
    public Optional<StaffMember> findByUserId(Integer userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return staffMemberRepository.findByUserId(userId);
    }

    /**
     * Another staff row already claiming this account, if any.
     *
     * <p>Checked before saving so the administrator gets a message naming the
     * conflicting person, rather than a constraint-violation stack trace.
     */
    public Optional<StaffMember> findConflictingAccountOwner(Integer userId, Long excludingStaffId) {
        if (userId == null) {
            return Optional.empty();
        }
        return staffMemberRepository.findByUserIdAndIdNot(userId, excludingStaffId == null ? -1L : excludingStaffId);
    }

    /**
     * Role holders, falling back to the whole staff list when nobody holds it.
     *
     * <p>For Phase 2 forms, which pick signers from a {@code <select>} rather than
     * a free-text datalist. Every imported person starts as {@code GENERAL} — the
     * directory cannot say who the dean is — so a strict role filter would render
     * an empty dropdown and make the form impossible to complete on a fresh
     * install. Showing everyone is worse labelling but a working form; once an
     * administrator tags the real dean, the list narrows on its own.
     */
    public List<StaffMember> findByRoleOrAll(String role) {
        List<StaffMember> holders = findByRole(role);
        return holders.isEmpty() ? findAll() : holders;
    }

    public List<StaffMember> findDeans() {
        return findByRole("DEAN");
    }

    public List<StaffMember> findHeads() {
        return findByRole("HEAD");
    }

    public List<StaffMember> findCommittee() {
        return findByRole("COMMITTEE");
    }

    public List<StaffMember> findHR() {
        return findByRole("HR");
    }

    public List<StaffMember> findGeneral() {
        return findByRole("GENERAL");
    }
}
