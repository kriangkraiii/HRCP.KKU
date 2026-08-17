package com.ecom.external.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.external.config.CpWebProperties;
import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.service.CpWebClient.CpPerson;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.ProfileImageStorage;
import com.ecom.service.SystemAlertService;

/**
 * Fills in what the college website knows and the HR feed does not — chiefly
 * photographs, which no other source carries at all.
 *
 * <p><b>The Fund Management feed stays the authority.</b> It is the official HR
 * record; the website is a public page that is updated when someone remembers to.
 * So this only ever fills blanks: an empty department, a missing rank, an account
 * still on the placeholder avatar. Nothing already filled in is overwritten, and
 * nothing here creates a person — only the HR feed does that.
 *
 * <p>Matching is by e-mail address. It is the one field both sides carry for
 * everyone, and unlike a name it does not go stale on marriage or promotion.
 */
@Service
public class CpDirectorySyncService {

    private static final Logger log = LoggerFactory.getLogger(CpDirectorySyncService.class);

    static final String SCHEDULE_ZONE = "Asia/Bangkok";

    /** What an alert about this job calls it. */
    private static final String SOURCE = "ดึงรูปและข้อมูลจากเว็บไซต์คณะ";

    /** The avatar shown when an account has no photo of its own. */
    private static final String PLACEHOLDER_IMAGE = "default.png";

    private final CpWebProperties props;
    private final CpWebClient client;
    private final FsFacultyRepository facultyRepo;
    private final UserRepository userRepo;
    private final StaffMemberRepository staffRepo;
    private final ProfileImageStorage imageStorage;
    private final SystemAlertService alerts;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public CpDirectorySyncService(CpWebProperties props,
            CpWebClient client,
            FsFacultyRepository facultyRepo,
            UserRepository userRepo,
            StaffMemberRepository staffRepo,
            ProfileImageStorage imageStorage,
            SystemAlertService alerts) {
        this.props = props;
        this.client = client;
        this.facultyRepo = facultyRepo;
        this.userRepo = userRepo;
        this.staffRepo = staffRepo;
        this.imageStorage = imageStorage;
        this.alerts = alerts;
    }

    /**
     * Weekly, at a quiet hour and after the HR sync has run.
     *
     * <p>A public web page changes far more slowly than the HR feed, and each run
     * downloads dozens of photographs from someone else's server, so there is
     * nothing to gain from asking nightly.
     */
    @Scheduled(cron = "${cp.web.sync.cron:0 30 3 * * SUN}", zone = SCHEDULE_ZONE)
    public void scheduledSync() {
        sync();
    }

    /** @return what the run changed, for the log and the admin screen */
    public Result sync() {
        if (!props.isUsable()) {
            log.debug("College directory sync skipped — disabled in configuration");
            return Result.skipped("ปิดการเชื่อมต่อเว็บไซต์คณะไว้");
        }
        if (!running.compareAndSet(false, true)) {
            return Result.skipped("กำลังทำงานอยู่");
        }

        try {
            List<CpPerson> people = client.fetchAll();
            if (people.isEmpty()) {
                return Result.skipped("ไม่ได้รับข้อมูลจากเว็บไซต์คณะ");
            }

            int photos = 0;
            int details = 0;
            int unmatched = 0;

            for (CpPerson person : people) {
                Applied applied = apply(person);
                if (!applied.matched()) {
                    unmatched++;
                    continue;
                }
                if (applied.photo()) {
                    photos++;
                }
                if (applied.details()) {
                    details++;
                }
            }

            Result result = new Result(people.size(), photos, details, unmatched, null);
            log.info("College directory sync finished: {}", result);
            alerts.success(SOURCE, result.describe());
            return result;

        } catch (Exception e) {
            log.error("College directory sync failed: {}", e.toString(), e);
            alerts.failure(SOURCE, "อ่านข้อมูลจากเว็บไซต์คณะไม่สำเร็จ: " + e.getMessage());
            return Result.failed(e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * Applies one directory entry wherever it matches.
     *
     * <p>Each save stands alone rather than the whole run sharing one transaction,
     * so one unusable record costs that record and not the other seventy-five.
     * That comes from the repository's own transaction per call — an
     * {@code @Transactional} here would be annotation-shaped decoration, since a
     * method called from inside its own class never passes through the proxy that
     * would apply it.
     */
    private Applied apply(CpPerson person) {
        String email = person.email();

        UserDtls user = userRepo.findByEmail(email);
        Optional<FsFaculty> faculty = facultyRepo.findByEmailNormalized(email);

        if (user == null && faculty.isEmpty()) {
            // Someone on the college site who is not in this system: administrative
            // staff, a student assistant, an emeritus listing. Nothing to attach to.
            return Applied.noMatch();
        }

        boolean photo = false;
        boolean details = false;

        if (user != null) {
            photo = applyPhoto(user, person);
            details = applyUserDetails(user, person);
            if (photo || details) {
                userRepo.save(user);
            }
        }

        // The staff list is what documents read from, so a blank department there
        // is worth filling from whatever source has one.
        if (faculty.isPresent() && applyToStaffList(faculty.get().getFsUserId(), person)) {
            details = true;
        }

        return new Applied(true, photo, details);
    }

    /**
     * Gives an account a photograph, but only if it has none.
     *
     * <p>A picture someone chose for themselves outranks the one on the college
     * page — replacing it every Sunday would be a bug that looks like vandalism.
     */
    private boolean applyPhoto(UserDtls user, CpPerson person) {
        if (person.imagePath() == null) {
            return false;
        }
        String current = user.getProfileImage();
        boolean hasValidPhotoOnDisk = current != null && !current.isBlank()
                && !PLACEHOLDER_IMAGE.equalsIgnoreCase(current)
                && imageStorage.exists(current);
        if (hasValidPhotoOnDisk) {
            return false;
        }

        String url = props.imageUrl(person.imagePath());
        Optional<byte[]> bytes = client.fetchImage(url);
        if (bytes.isEmpty()) {
            return false;
        }

        String stored = imageStorage.storeFromBytes(bytes.get(), fileNameFor(person));
        if (stored == null) {
            return false;
        }

        user.setProfileImage(stored);
        return true;
    }

    /** Fills gaps on the account. Anything already set is left as it is. */
    private boolean applyUserDetails(UserDtls user, CpPerson person) {
        boolean changed = false;

        if (isBlank(user.getFirstName()) && !isBlank(person.firstName())) {
            user.setFirstName(person.firstName());
            changed = true;
        }
        if (isBlank(user.getLastName()) && !isBlank(person.lastName())) {
            user.setLastName(person.lastName());
            changed = true;
        }
        // Short form for the name badge — "ผศ.ดร." rather than the full words.
        if (isBlank(user.getTitle()) && !isBlank(person.academicRankShort())) {
            user.setTitle(person.academicRankShort());
            changed = true;
        }
        if (isBlank(user.getAcademicPosition()) && !isBlank(person.academicRank())) {
            user.setAcademicPosition(person.academicRank());
            changed = true;
        }

        return changed;
    }

    private boolean applyToStaffList(Long fsUserId, CpPerson person) {
        if (fsUserId == null || isBlank(person.programme())) {
            return false;
        }
        Optional<StaffMember> staff = staffRepo.findByFsUserId(fsUserId);
        if (staff.isEmpty() || !isBlank(staff.get().getDepartment())) {
            return false;
        }
        staff.get().setDepartment(person.programme());
        staffRepo.save(staff.get());
        return true;
    }

    /**
     * A stable filename per person, so re-running replaces the photo rather than
     * filling the upload directory with copies.
     */
    private String fileNameFor(CpPerson person) {
        String extension = extensionOf(person.imagePath());
        String key = person.slug() != null ? person.slug() : person.email();
        return "cp-" + key.replaceAll("[^a-zA-Z0-9._-]", "_") + "." + extension;
    }

    private String extensionOf(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "jpg";
        }
        String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        // Anything unexpected is stored under a name the image validator accepts
        // or rejects on its own terms, not on the strength of the remote path.
        return extension.length() > 4 ? "jpg" : extension;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * What one directory entry changed.
     *
     * <p>{@code matched} is separate from the other two on purpose: "nobody here
     * by that address" and "found them, nothing needed changing" are different
     * outcomes and only the first is worth reporting.
     */
    private record Applied(boolean matched, boolean photo, boolean details) {

        static Applied noMatch() {
            return new Applied(false, false, false);
        }
    }

    /** Outcome of a whole run. */
    public record Result(int read, int photos, int details, int unmatched, String error) {

        static Result skipped(String reason) {
            return new Result(0, 0, 0, 0, reason);
        }

        static Result failed(String message) {
            return new Result(0, 0, 0, 0, message == null ? "ไม่ทราบสาเหตุ" : message);
        }

        public boolean success() {
            return error == null;
        }

        /** Thai summary for the sync page. */
        public String describe() {
            if (error != null) {
                return error;
            }
            return "อ่านจากเว็บคณะ " + read + " คน — เพิ่มรูป " + photos + " คน, "
                    + "เติมข้อมูล " + details + " คน, ไม่พบในระบบ " + unmatched + " คน";
        }

        @Override
        public String toString() {
            return read + " read, " + photos + " photos, " + details + " details filled, "
                    + unmatched + " unmatched";
        }
    }
}
