package com.ecom.external.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.external.config.FsApiProperties;
import com.ecom.external.model.FsSyncState;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.FacultyChangeReviewService;
import com.ecom.external.service.CpDirectorySyncService;
import com.ecom.external.service.FsSyncService;
import com.ecom.external.service.ManualSyncGuard;

/**
 * Operations screen for the external-data mirror.
 *
 * <p>Exists because the scheduled jobs are not the only time an administrator
 * needs data: after a schema change, after an upstream correction, or simply to
 * see whether last night's run worked. The REST endpoints on
 * {@link ExternalSyncAdminController} do the same work but cannot be driven from
 * a browser, which left "pull now" effectively unavailable in practice.
 */
@Controller
@RequestMapping("/admin/external-sync")
@PreAuthorize("hasRole('ADMIN')")
public class ExternalSyncPageController {

    private static final String VIEW = "admin/external_sync";
    private static final String REDIRECT = "redirect:/admin/external-sync";

    private final FsSyncService syncService;
    private final FacultyChangeReviewService reviewService;
    private final ManualSyncGuard guard;
    private final FsFacultyRepository facultyRepo;
    private final ScopusPublicationRepository publicationRepo;
    private final FsApiProperties props;
    private final CpDirectorySyncService cpSyncService;

    private final String usersCron;
    private final String usersFullCron;
    private final String scopusCron;

    public ExternalSyncPageController(FsSyncService syncService,
            FacultyChangeReviewService reviewService,
            ManualSyncGuard guard,
            FsFacultyRepository facultyRepo,
            ScopusPublicationRepository publicationRepo,
            FsApiProperties props,
            CpDirectorySyncService cpSyncService,
            @Value("${fs.sync.users.cron:0 30 1 * * *}") String usersCron,
            @Value("${fs.sync.users.full-cron:0 0 3 * * SUN}") String usersFullCron,
            @Value("${fs.sync.scopus.cron:0 0 2 * * *}") String scopusCron) {
        this.syncService = syncService;
        this.reviewService = reviewService;
        this.guard = guard;
        this.facultyRepo = facultyRepo;
        this.publicationRepo = publicationRepo;
        this.props = props;
        this.cpSyncService = cpSyncService;
        this.usersCron = usersCron;
        this.usersFullCron = usersFullCron;
        this.scopusCron = scopusCron;
    }

    @GetMapping
    public String page(Model model) {
        Map<String, FsSyncState> jobs = syncService.currentState();

        model.addAttribute("facultyCount", facultyRepo.count());
        model.addAttribute("publicationCount", publicationRepo.count());
        model.addAttribute("facultyWithScopus", facultyRepo.findAllWithScopusId().size());
        model.addAttribute("pendingCount", reviewService.pendingCount());

        model.addAttribute("usersJob", jobs.get(FsSyncState.TYPE_USERS));
        model.addAttribute("scopusJob", jobs.get(FsSyncState.TYPE_SCOPUS));

        model.addAttribute("apiEnabled", props.isUsable());
        model.addAttribute("apiBaseUrl", props.getBaseUrl());
        model.addAttribute("schedule", schedule());

        // Seconds left on each cooldown, so the page can grey the button out
        // instead of letting an admin click into a rejection.
        model.addAttribute("usersCooldown", guard.remaining(FsSyncState.TYPE_USERS).toSeconds());
        model.addAttribute("scopusCooldown", guard.remaining(FsSyncState.TYPE_SCOPUS).toSeconds());
        return VIEW;
    }

    /** Human-readable next-run times, so "did it run?" has an answer on the page. */
    private Map<String, String> schedule() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("ข้อมูลอาจารย์ (เฉพาะที่เปลี่ยน)", describe(usersCron));
        out.put("ข้อมูลอาจารย์ (ตรวจซ้ำทั้งหมด)", describe(usersFullCron));
        out.put("ผลงาน Scopus", describe(scopusCron));
        return out;
    }

    private String describe(String cron) {
        try {
            LocalDateTime next = CronExpression.parse(cron).next(LocalDateTime.now());
            if (next == null) {
                return cron;
            }
            return String.format("%s — ครั้งถัดไป %02d/%02d/%d %02d:%02d น.",
                    cron, next.getDayOfMonth(), next.getMonthValue(), next.getYear() + 543,
                    next.getHour(), next.getMinute());
        } catch (Exception e) {
            return cron;
        }
    }

    /**
     * Re-reads the faculty directory. Differences are staged for approval rather
     * than applied, so pressing this cannot corrupt anything — the cooldown is
     * about upstream request budget, not about data safety.
     */
    @PostMapping("/users")
    public String syncUsers(RedirectAttributes redirect) {
        Duration wait = guard.claim(FsSyncState.TYPE_USERS);
        if (!wait.isZero()) {
            redirect.addFlashAttribute("warnMsg", cooldownMessage("ตรวจข้อมูลอาจารย์", wait));
            return REDIRECT;
        }

        FsSyncService.SyncResult result = syncService.syncUsers(true);

        if (result.success()) {
            long pending = reviewService.pendingCount();
            redirect.addFlashAttribute("succMsg", pending > 0
                    ? "ตรวจสอบข้อมูลอาจารย์เรียบร้อย — มี " + pending + " รายการรอการยืนยัน"
                    : "ตรวจสอบข้อมูลอาจารย์เรียบร้อย — ข้อมูลตรงกับต้นทางแล้ว");
        } else if (result.skipped()) {
            // Not an error: another run already had it. Do not burn the cooldown.
            guard.release(FsSyncState.TYPE_USERS);
            redirect.addFlashAttribute("warnMsg", "มีการดึงข้อมูลอยู่แล้วในขณะนี้ กรุณารอสักครู่");
        } else {
            guard.release(FsSyncState.TYPE_USERS);
            redirect.addFlashAttribute("errorMsg", "ดึงข้อมูลอาจารย์ไม่สำเร็จ: " + result.message());
        }
        return REDIRECT;
    }

    /**
     * Reads the college website and fills in what it knows.
     *
     * <p>No cooldown of its own: it talks to a different server than the HR feed,
     * so it spends none of that request budget.
     *
     * <p>Thai names, the rank and the photo only ever fill blanks, so pressing
     * this twice changes nothing. The English name and rank are the exception —
     * the website is the only source that separates an English given and family
     * name, so those are written over whatever is there, including the value the
     * HR feed's name-splitting guess produced. A hand edit to the English fields
     * therefore does not survive the next press.
     */
    @PostMapping("/college-web")
    public String syncCollegeWeb(RedirectAttributes redirect) {
        CpDirectorySyncService.Result result = cpSyncService.sync();

        if (result.success()) {
            redirect.addFlashAttribute("succMsg", result.describe());
        } else {
            redirect.addFlashAttribute("warnMsg", "ดึงข้อมูลจากเว็บไซต์คณะไม่สำเร็จ: " + result.describe());
        }
        return REDIRECT;
    }

    /**
     * Re-reads publications. These apply straight away: they are the professor's
     * own bibliography, not a field that rewrites an existing document.
     *
     * <p>Carries the longer cooldown — one run costs five upstream requests.
     */
    @PostMapping("/scopus")
    public String syncScopus(RedirectAttributes redirect) {
        Duration wait = guard.claim(FsSyncState.TYPE_SCOPUS);
        if (!wait.isZero()) {
            redirect.addFlashAttribute("warnMsg", cooldownMessage("ดึงผลงาน Scopus", wait));
            return REDIRECT;
        }

        FsSyncService.SyncResult result = syncService.syncPublications();

        if (result.success()) {
            redirect.addFlashAttribute("succMsg", String.format(
                    "ดึงผลงาน Scopus เรียบร้อย — %d รายการ (ใช้ %d คำขอ, %.1f วินาที)",
                    result.rows(), result.requests(), result.durationMs() / 1000.0));
        } else if (result.skipped()) {
            guard.release(FsSyncState.TYPE_SCOPUS);
            redirect.addFlashAttribute("warnMsg", "มีการดึงผลงานอยู่แล้วในขณะนี้ กรุณารอให้เสร็จก่อน");
        } else {
            guard.release(FsSyncState.TYPE_SCOPUS);
            redirect.addFlashAttribute("errorMsg", "ดึงผลงานไม่สำเร็จ: " + result.message());
        }
        return REDIRECT;
    }

    private String cooldownMessage(String action, Duration wait) {
        long seconds = Math.max(wait.toSeconds(), 1);
        String left = seconds >= 60
                ? (seconds / 60) + " นาที " + (seconds % 60) + " วินาที"
                : seconds + " วินาที";
        return "เพิ่งดึงข้อมูลไปเมื่อสักครู่ — กรุณารออีก " + left + " จึงจะสั่ง “" + action + "” ได้อีกครั้ง "
                + "(ระบบจำกัดไว้เพื่อไม่ให้เกินโควตาของระบบต้นทาง)";
    }
}
