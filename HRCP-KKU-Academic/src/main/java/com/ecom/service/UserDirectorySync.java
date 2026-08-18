package com.ecom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.service.EnglishNameSplitter;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Creates login accounts for the lecturers the HR feed brings in.
 *
 * <p>Without this the directory sync stops one step short of being useful: the
 * names are in the system but there is no account to assign a request to, notify,
 * or grant a role until the person happens to sign in for the first time.
 *
 * <p>What it will not do is decide anything about an account that already exists.
 * Roles, whether an account is enabled, and passwords are local decisions, and an
 * upstream directory has no business overruling them — an administrator whose
 * address also appears in the faculty list must not be demoted by a nightly job.
 * Only blank fields are filled.
 */
@Service
public class UserDirectorySync {

    private static final Logger log = LoggerFactory.getLogger(UserDirectorySync.class);

    private final UserRepository userRepository;
    private final FsFacultyRepository facultyRepo;
    private final UserService userService;

    public UserDirectorySync(UserRepository userRepository,
            FsFacultyRepository facultyRepo,
            UserService userService) {
        this.userRepository = userRepository;
        this.facultyRepo = facultyRepo;
        this.userService = userService;
    }

    /** Creates what is missing and fills what is blank. */
    public Result createMissingAccounts() {
        int created = 0;
        int filled = 0;
        int skipped = 0;

        for (FsFaculty faculty : facultyRepo.findAll()) {
            String email = normalizedEmail(faculty);
            if (email == null) {
                // No address means no way to sign in and no key to match on.
                skipped++;
                continue;
            }

            UserDtls existing = userRepository.findByEmail(email);
            if (existing != null) {
                if (fillBlanks(existing, faculty)) {
                    userRepository.save(existing);
                    filled++;
                }
                continue;
            }

            // Someone who has already left is not worth an account they will
            // never use.
            if (!faculty.isActive()) {
                skipped++;
                continue;
            }

            try {
                createFrom(faculty, email);
                created++;
            } catch (DataIntegrityViolationException e) {
                // The unique constraint on the address caught a duplicate this
                // loop could not see — a concurrent sign-in, or two directory rows
                // sharing an address. Their account exists either way.
                log.info("Account for a directory entry already existed; left as it is");
                skipped++;
            }
        }

        Result result = new Result(created, filled, skipped);
        if (created > 0 || filled > 0) {
            log.info("User accounts synced from faculty directory: {}", result);
        }
        return result;
    }

    /**
     * Builds the account and hands it to {@link UserService#saveUser} to be
     * created.
     *
     * <p>That method already is "create an ordinary user account": it sets the
     * role, the enabled and unlocked state, a random placeholder password, the
     * first-login flag and the applicant id. Repeating any of it here would mean
     * two places deciding what a new account looks like, and the applicant id in
     * particular must be issued in one place or two accounts can be handed the
     * same number.
     *
     * <p>The person never uses the placeholder password. They arrive through KKU
     * SSO, which recognises the account by its address; the first-login flow that
     * sets a real password stays available for anyone who needs it.
     */
    private UserDtls createFrom(FsFaculty faculty, String email) {
        UserDtls user = new UserDtls();
        user.setEmail(email);
        user.setEmailNotificationEnabled(true);
        applyIdentity(user, faculty);
        return userService.saveUser(user);
    }

    /**
     * Copies directory values into empty fields only.
     *
     * @return true when something was actually filled
     */
    private boolean fillBlanks(UserDtls user, FsFaculty faculty) {
        boolean changed = false;

        if (isBlank(user.getFirstName()) && !isBlank(faculty.getFirstName())) {
            user.setFirstName(faculty.getFirstName().trim());
            changed = true;
        }
        if (isBlank(user.getLastName()) && !isBlank(faculty.getLastName())) {
            user.setLastName(faculty.getLastName().trim());
            changed = true;
        }
        if (isBlank(user.getTitle()) && !isBlank(faculty.getPrefix())) {
            user.setTitle(faculty.getPrefix().trim());
            changed = true;
        }
        if (isBlank(user.getAcademicPosition()) && !isBlank(faculty.getPositionTitle())) {
            user.setAcademicPosition(faculty.getPositionTitle().trim());
            changed = true;
        }
        if (isBlank(user.getAcademicPositionEn()) && !isBlank(faculty.getPositionEn())) {
            user.setAcademicPositionEn(faculty.getPositionEn().trim());
            changed = true;
        }
        if (isBlank(user.getMobileNumber()) && !isBlank(faculty.getTel())) {
            user.setMobileNumber(faculty.getTel().trim());
            changed = true;
        }

        // The directory has no separate English given/family name, only the
        // combined name_en, so the split is derived. Still fill-blank only: a
        // correction someone made by hand outranks a heuristic.
        EnglishNameSplitter.Parts english = EnglishNameSplitter.split(faculty.getNameEn());
        if (isBlank(user.getFirstNameEn()) && !isBlank(english.firstName())) {
            user.setFirstNameEn(english.firstName());
            changed = true;
        }
        if (isBlank(user.getLastNameEn()) && !isBlank(english.lastName())) {
            user.setLastNameEn(english.lastName());
            changed = true;
        }

        return changed;
    }

    private void applyIdentity(UserDtls user, FsFaculty faculty) {
        if (!isBlank(faculty.getFirstName())) {
            user.setFirstName(faculty.getFirstName().trim());
        }
        if (!isBlank(faculty.getLastName())) {
            user.setLastName(faculty.getLastName().trim());
        }
        if (!isBlank(faculty.getPrefix())) {
            user.setTitle(faculty.getPrefix().trim());
        }
        if (!isBlank(faculty.getPositionTitle())) {
            user.setAcademicPosition(faculty.getPositionTitle().trim());
        }
        if (!isBlank(faculty.getPositionEn())) {
            user.setAcademicPositionEn(faculty.getPositionEn().trim());
        }
        if (!isBlank(faculty.getTel())) {
            user.setMobileNumber(faculty.getTel().trim());
        }

        // Derived from the combined name_en — the directory carries no split
        // English name of its own.
        EnglishNameSplitter.Parts english = EnglishNameSplitter.split(faculty.getNameEn());
        if (!isBlank(english.firstName())) {
            user.setFirstNameEn(english.firstName());
        }
        if (!isBlank(english.lastName())) {
            user.setLastNameEn(english.lastName());
        }
    }

    private String normalizedEmail(FsFaculty faculty) {
        String email = faculty.getEmail();
        return isBlank(email) ? null : email.trim().toLowerCase();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** What one run did. */
    public record Result(int created, int filled, int skipped) {

        /** Thai summary for the sync page. */
        public String describe() {
            if (created == 0 && filled == 0) {
                return "บัญชีผู้ใช้ครบตามรายชื่ออาจารย์แล้ว";
            }
            StringBuilder sb = new StringBuilder("บัญชีผู้ใช้:");
            if (created > 0) {
                sb.append(" สร้างใหม่ ").append(created).append(" บัญชี");
            }
            if (filled > 0) {
                sb.append(" เติมข้อมูลที่ว่าง ").append(filled).append(" บัญชี");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return created + " created, " + filled + " filled, " + skipped + " skipped";
        }
    }
}
