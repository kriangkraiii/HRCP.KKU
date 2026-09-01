package com.ecom.sso;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Decides whether an SSO-authenticated person may enter this system.
 *
 * <p>KKU SSO authenticates the whole university — students, every department,
 * every contractor with an account. Authentication is therefore not
 * authorisation here: entry is restricted to people this system already knows,
 * checked against the synced faculty directory plus an explicit extra
 * allowlist for administrators who are not teaching staff.
 *
 * <p>The allowlist is deny-by-default. An unknown address is refused rather
 * than provisioned, so a valid KKU login alone never grants access.
 */
@Service
public class SsoAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(SsoAccessPolicy.class);

    private final FsFacultyRepository facultyRepo;
    private final UserRepository userRepository;
    private final com.ecom.academic.repository.AcademicCommitteeMemberRepository committeeRepo;

    /** Addresses allowed in addition to the faculty directory (admins, support). */
    private final Set<String> extraAllowed;

    /**
     * When true, an account that already exists locally may sign in even if it
     * is not in the faculty directory. Off by default: a stale local row should
     * not become a way around the allowlist.
     */
    private final boolean allowExistingLocalUsers;

    public SsoAccessPolicy(FsFacultyRepository facultyRepo,
            UserRepository userRepository,
            String allowedEmails,
            boolean allowExistingLocalUsers) {
        this(facultyRepo, userRepository, null, allowedEmails, allowExistingLocalUsers);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SsoAccessPolicy(FsFacultyRepository facultyRepo,
            UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.ecom.academic.repository.AcademicCommitteeMemberRepository committeeRepo,
            @Value("${app.auth.sso.allowed-emails:}") String allowedEmails,
            @Value("${app.auth.sso.allow-existing-local-users:false}") boolean allowExistingLocalUsers) {
        this.facultyRepo = facultyRepo;
        this.userRepository = userRepository;
        this.committeeRepo = committeeRepo;
        this.allowExistingLocalUsers = allowExistingLocalUsers;
        this.extraAllowed = Arrays.stream(allowedEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!extraAllowed.isEmpty()) {
            log.info("SSO allowlist carries {} address(es) beyond the faculty directory", extraAllowed.size());
        }
    }

    /**
     * @param email address reported by KKU SSO
     * @return the decision, carrying the matched faculty record when there is one
     */
    public Decision evaluate(String email) {
        if (email == null || email.isBlank()) {
            return Decision.deny("ไม่ได้รับอีเมลจากระบบ SSO");
        }

        String normalized = email.trim().toLowerCase();

        // Checked ahead of everything else. Deactivation is an explicit local
        // decision — an administrator switching an account off expects that to
        // hold however the person signs in, and the faculty directory has no
        // knowledge of it. The password path is covered by CustomUser.isEnabled();
        // this branch builds its own Authentication and so has to ask here.
        UserDtls local = userRepository.findByEmailIgnoreCase(normalized);
        if (local == null) {
            local = userRepository.findByEmail(normalized);
        }
        Optional<FsFaculty> faculty = facultyRepo.findByEmailNormalized(normalized);

        // Every fact the decision below rests on, in one line. A refusal that says
        // only "not on the allowlist" leaves four possible reasons and no way to
        // tell them apart — and this runs as a Windows service, where anything
        // printed to stdout is discarded rather than written to the log file.
        log.info("SSO access check for {} — localAccount={} facultyRecord={} onAllowlist={} allowExistingLocalUsers={}",
                normalized,
                local != null ? "yes(role=" + local.getRole() + ", enabled=" + local.getIsEnable() + ")" : "no",
                faculty.isPresent() ? "yes(active=" + faculty.get().isActive() + ")" : "no",
                extraAllowed.contains(normalized),
                allowExistingLocalUsers);

        if (local != null && !Boolean.TRUE.equals(local.getIsEnable())) {
            log.warn("SSO login refused — the local account is deactivated: {}", normalized);
            return Decision.deny("บัญชีนี้ถูกปิดการใช้งานในระบบ กรุณาติดต่อผู้ดูแลระบบ");
        }

        if (faculty.isPresent()) {
            FsFaculty f = faculty.get();
            if (!f.isActive()) {
                log.warn("SSO login refused — faculty record is inactive");
                return Decision.deny("บัญชีนี้ถูกระงับการใช้งานในระบบต้นทาง");
            }
            log.info("SSO access granted — {} is in the faculty directory", normalized);
            return Decision.allow(f);
        }

        if (extraAllowed.contains(normalized)) {
            log.info("SSO access granted — {} is on app.auth.sso.allowed-emails", normalized);
            return Decision.allow(null);
        }

        // Allow active committee members registered in the system
        if (committeeRepo != null && committeeRepo.findByEmailIgnoreCaseAndIsActiveTrue(normalized).isPresent()) {
            log.info("SSO access granted — {} is an active committee member", normalized);
            return Decision.allow(null);
        }

        // A deactivated row was already turned away above, so reaching here with a
        // local account means it is one this deployment is willing to admit.
        if (allowExistingLocalUsers && local != null) {
            log.info("SSO access granted — {} already has an account here (role {})",
                    normalized, local.getRole());
            return Decision.allow(null);
        }

        log.warn("""
                SSO login refused for {} — none of the four ways in applied.
                วิธีเปิดสิทธิ์ เลือกอย่างใดอย่างหนึ่ง:
                  1. เพิ่มอีเมลใน app.auth.sso.allowed-emails (คั่นด้วย , แล้ว restart service)
                  2. สร้างบัญชีในระบบด้วยอีเมลนี้ และตั้ง app.auth.sso.allow-existing-local-users=true
                  3. ให้อีเมลนี้อยู่ในรายชื่อบุคลากร (fs_faculty) จากการซิงค์
                  4. เพิ่มเป็นกรรมการที่ยัง active ในระบบ""", normalized);
        return Decision.deny("บัญชีของคุณยังไม่ได้รับสิทธิ์เข้าใช้ระบบนี้ กรุณาติดต่อผู้ดูแลระบบเพื่อขอเปิดสิทธิ์");
    }

    /** Outcome of an allowlist check. */
    public record Decision(boolean allowed, FsFaculty faculty, String reason) {

        static Decision allow(FsFaculty faculty) {
            return new Decision(true, faculty, null);
        }

        static Decision deny(String reason) {
            return new Decision(false, null, reason);
        }
    }
}
