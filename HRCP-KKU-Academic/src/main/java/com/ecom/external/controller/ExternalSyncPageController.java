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

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.repository.FsFacultyChangeRepository;

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
    private final FsFacultyChangeRepository changeRepo;
    private final ManualSyncGuard guard;
    private final FsFacultyRepository facultyRepo;
    private final ScopusPublicationRepository publicationRepo;
    private final FsApiProperties props;
    private final CpDirectorySyncService cpSyncService;
    private final com.ecom.external.harvest.PublicationHarvestService harvestService;
    private final com.ecom.external.service.KkuDocumentSyncService kkuDocSyncService;

    private final String usersCron;
    private final String usersFullCron;
    private final String scopusCron;
    private final String harvestCron;
    private final String cpWebCron;
    private final String imageCleanupCron;
    private final String retentionCron;
    private final String evalExpiryCron;
    private final String esignReminderCron;
    private final String kkuHrCron;

    public ExternalSyncPageController(FsSyncService syncService,
            FacultyChangeReviewService reviewService,
            FsFacultyChangeRepository changeRepo,
            ManualSyncGuard guard,
            FsFacultyRepository facultyRepo,
            ScopusPublicationRepository publicationRepo,
            FsApiProperties props,
            CpDirectorySyncService cpSyncService,
            com.ecom.external.harvest.PublicationHarvestService harvestService,
            com.ecom.external.service.KkuDocumentSyncService kkuDocSyncService,
            @Value("${fs.sync.users.cron:0 30 1 * * *}") String usersCron,
            @Value("${fs.sync.users.full-cron:0 0 3 * * SUN}") String usersFullCron,
            @Value("${fs.sync.scopus.cron:0 0 2 * * *}") String scopusCron,
            @Value("${harvest.cron:0 30 2 * * *}") String harvestCron,
            @Value("${cp.web.sync.cron:0 30 3 * * SUN}") String cpWebCron,
            @Value("${app.images.cleanup-cron:0 0 3 * * ?}") String imageCleanupCron,
            @Value("${app.retention.cron:0 30 3 * * ?}") String retentionCron,
            @Value("${evaluation.expiry.cron:0 0 8 * * *}") String evalExpiryCron,
            @Value("${app.esign.reminder-cron:0 30 8 * * *}") String esignReminderCron,
            @Value("${kku.hr.sync.cron:0 0 2 1 * ?}") String kkuHrCron) {
        this.syncService = syncService;
        this.reviewService = reviewService;
        this.changeRepo = changeRepo;
        this.guard = guard;
        this.facultyRepo = facultyRepo;
        this.publicationRepo = publicationRepo;
        this.props = props;
        this.cpSyncService = cpSyncService;
        this.harvestService = harvestService;
        this.kkuDocSyncService = kkuDocSyncService;
        this.usersCron = usersCron;
        this.usersFullCron = usersFullCron;
        this.scopusCron = scopusCron;
        this.harvestCron = harvestCron;
        this.cpWebCron = cpWebCron;
        this.imageCleanupCron = imageCleanupCron;
        this.retentionCron = retentionCron;
        this.evalExpiryCron = evalExpiryCron;
        this.esignReminderCron = esignReminderCron;
        this.kkuHrCron = kkuHrCron;
    }

    @GetMapping
    public String page(@RequestParam(value = "tab", required = false, defaultValue = "sync") String activeTab, Model model) {
        Map<String, FsSyncState> jobs = syncService.currentState();

        model.addAttribute("facultyCount", facultyRepo.count());
        model.addAttribute("publicationCount", publicationRepo.count());
        model.addAttribute("facultyWithScopus", facultyRepo.findAllWithScopusId().size());

        // Breakdown of publications by data source
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        sourceCounts.put("SCOPUS", 0L);
        sourceCounts.put("OPENALEX", 0L);
        sourceCounts.put("CROSSREF", 0L);
        sourceCounts.put("DBLP", 0L);
        sourceCounts.put("THAIJO", 0L);
        sourceCounts.put("KKU_IR", 0L);

        List<Object[]> groupedCounts = publicationRepo.countGroupByDataSource();
        for (Object[] r : groupedCounts) {
            if (r != null && r.length == 2 && r[0] != null && r[1] != null) {
                String ds = r[0].toString().toUpperCase();
                long count = ((Number) r[1]).longValue();
                sourceCounts.put(ds, count);
            }
        }
        model.addAttribute("sourceCounts", sourceCounts);

        List<FsFacultyChange> pending = reviewService.pending();
        model.addAttribute("changes", pending);
        model.addAttribute("diffs", reviewService.readDiffs(pending));
        model.addAttribute("pendingCount", pending.size());
        model.addAttribute("recent", recentDecisions());
        model.addAttribute("activeTab", activeTab);

        model.addAttribute("usersJob", jobs.get(FsSyncState.TYPE_USERS));
        model.addAttribute("scopusJob", jobs.get(FsSyncState.TYPE_SCOPUS));
        model.addAttribute("jobs", jobs);
        model.addAttribute("harvestRunning", harvestService.isRunning());

        // List of all sync jobs for status dashboard table
        List<SyncJobDisplay> syncJobList = List.of(
                new SyncJobDisplay("users", "ข้อมูลอาจารย์ (Faculty Directory)", "fas fa-users", "text-primary", "บุคลากร", jobs.get(FsSyncState.TYPE_USERS)),
                new SyncJobDisplay("college_web", "รูปและข้อมูลเว็บคณะ (computing.kku.ac.th)", "fas fa-image", "text-info", "บุคลากร", jobs.get("college_web")),
                new SyncJobDisplay("scopus", "ผลงาน Scopus เดิม", "fas fa-book", "text-success", "งานวิจัย", jobs.get(FsSyncState.TYPE_SCOPUS)),
                new SyncJobDisplay("openalex", "OpenAlex (Global Works API)", "fas fa-globe", "text-info", "งานวิจัย", jobs.get("openalex")),
                new SyncJobDisplay("crossref", "Crossref (DOI Registry)", "fas fa-crosshairs", "text-warning", "งานวิจัย", jobs.get("crossref")),
                new SyncJobDisplay("dblp", "DBLP (Computer Science Bibliography)", "fas fa-laptop-code", "text-primary", "งานวิจัย", jobs.get("dblp")),
                new SyncJobDisplay("thaijo", "ThaiJO (วารสารวิชาการไทย OAI-PMH)", "fas fa-file-lines", "text-danger", "งานวิจัย", jobs.get("thaijo")),
                new SyncJobDisplay("kkuir", "KKU IR (คลังสถาบัน มข. DSpace)", "fas fa-building-columns", "text-secondary", "งานวิจัย", jobs.get("kkuir")),
                new SyncJobDisplay("kku_regulations", "คลังข้อบังคับ & ประกาศ มข. (hr2.kku.ac.th)", "fas fa-landmark", "text-warning", "เอกสาร/ระเบียบ", kkuDocSyncService.getSyncState())
        );
        model.addAttribute("syncJobList", syncJobList);

        long okCount = syncJobList.stream().filter(j -> j.state() != null && "OK".equalsIgnoreCase(j.state().getLastStatus())).count();
        long failedCount = syncJobList.stream().filter(j -> j.state() != null && "FAILED".equalsIgnoreCase(j.state().getLastStatus())).count();
        long runningCount = syncJobList.stream().filter(j -> j.state() != null && "RUNNING".equalsIgnoreCase(j.state().getLastStatus())).count();

        model.addAttribute("okCount", okCount);
        model.addAttribute("failedCount", failedCount);
        model.addAttribute("runningCount", runningCount);
        model.addAttribute("totalJobs", syncJobList.size());

        model.addAttribute("apiEnabled", props.isUsable());
        model.addAttribute("apiBaseUrl", props.getBaseUrl());
        model.addAttribute("scheduleCategories", scheduleCategories());

        // Seconds left on each cooldown, so the page can grey the button out
        // instead of letting an admin click into a rejection.
        model.addAttribute("usersCooldown", guard.remaining(FsSyncState.TYPE_USERS).toSeconds());
        model.addAttribute("scopusCooldown", guard.remaining(FsSyncState.TYPE_SCOPUS).toSeconds());
        return VIEW;
    }

    public record SyncJobDisplay(
            String id,
            String name,
            String icon,
            String colorClass,
            String category,
            FsSyncState state
    ) {}

    public record ScheduleCategory(
            String title,
            String icon,
            String colorClass,
            List<ScheduleItem> items
    ) {}

    public record ScheduleItem(
            String name,
            String description,
            String cron,
            String frequency,
            String nextRun,
            String icon,
            String badgeClass
    ) {}

    private List<FsFacultyChange> recentDecisions() {
        PageRequest topTen = PageRequest.of(0, 10);
        List<FsFacultyChange> approved = changeRepo
                .findByStatusOrderByReviewedAtDesc(FsFacultyChange.STATUS_APPROVED, topTen).getContent();
        List<FsFacultyChange> rejected = changeRepo
                .findByStatusOrderByReviewedAtDesc(FsFacultyChange.STATUS_REJECTED, topTen).getContent();

        return java.util.stream.Stream.concat(approved.stream(), rejected.stream())
                .sorted((a, b) -> {
                    if (a.getReviewedAt() == null || b.getReviewedAt() == null) {
                        return 0;
                    }
                    return b.getReviewedAt().compareTo(a.getReviewedAt());
                })
                .limit(10)
                .toList();
    }

    /** Detailed schedule of all 9 background cron jobs across the system grouped by category. */
    private List<ScheduleCategory> scheduleCategories() {
        return List.of(
                new ScheduleCategory(
                        "การเชื่อมต่อภายนอก & งานวิจัย (External Integrations & Research Data)",
                        "fas fa-network-wired",
                        "text-primary",
                        List.of(
                                new ScheduleItem(
                                        "ข้อมูลอาจารย์ (ดึงเฉพาะที่เปลี่ยน / Incremental)",
                                        "ดึงข้อมูลอาจารย์ที่อัปเดตจากระบบ Fund Management มาเทียบกับฐานข้อมูล หากพบการแก้ไขจะพักไว้ให้ Admin อนุมัติ",
                                        usersCron,
                                        "ทุกวัน เวลา 01:30 น.",
                                        describeNext(usersCron),
                                        "fas fa-users",
                                        "bg-primary"
                                ),
                                new ScheduleItem(
                                        "ผลงานวิจัย Scopus เดิม",
                                        "ดึงผลงานวิจัยของอาจารย์ทุกคนจาก Scopus API และจับคู่ Scopus ID",
                                        scopusCron,
                                        "ทุกวัน เวลา 02:00 น.",
                                        describeNext(scopusCron),
                                        "fas fa-book",
                                        "bg-success"
                                ),
                                new ScheduleItem(
                                        "งานวิจัย 5 แหล่งใหม่ (OpenAlex, Crossref, DBLP, ThaiJO, KKU IR)",
                                        "ดึงผลงานจาก 5 แหล่งพร้อมกันแบบ Parallel ผ่าน Virtual Threads, ทำ Deduplication 2.5 ชั้น และตรวจระดับวารสาร SJR/TCI",
                                        harvestCron,
                                        "ทุกวัน เวลา 02:30 น.",
                                        describeNext(harvestCron),
                                        "fas fa-globe",
                                        "bg-info text-dark"
                                ),
                                new ScheduleItem(
                                        "ข้อมูลอาจารย์ (ตรวจซ้ำทั้งหมด / Full Re-sync)",
                                        "ดึงข้อมูลอาจารย์ทุกคนแบบ Full Pass เพื่อตรวจสอบความถูกต้องและซ่อมแซมข้อมูลที่อาจค้าง",
                                        usersFullCron,
                                        "ทุกวันอาทิตย์ เวลา 03:00 น.",
                                        describeNext(usersFullCron),
                                        "fas fa-user-check",
                                        "bg-primary"
                                ),
                                new ScheduleItem(
                                        "รูปและข้อมูลจากเว็บไซต์คณะ (computing.kku.ac.th)",
                                        "Sync ทำเนียบบุคลากร รูปโปรไฟล์ และข้อมูลตำแหน่งจากเว็บไซต์วิทยาลัยการคอมพิวเตอร์",
                                        cpWebCron,
                                        "ทุกวันอาทิตย์ เวลา 03:30 น.",
                                        describeNext(cpWebCron),
                                        "fas fa-image",
                                        "bg-secondary"
                                ),
                                new ScheduleItem(
                                        "คลังข้อบังคับ & ประกาศ กองทรัพยากรบุคคล มข.",
                                        "ดึงและอัปเดตข้อบังคับ/ประกาศ/คำจำกัดความผลงานทางวิชาการใหม่จาก hr2.kku.ac.th อัตโนมัติ",
                                        kkuHrCron,
                                        "ทุกเดือน วันที่ 1 เวลา 02:00 น.",
                                        describeNext(kkuHrCron),
                                        "fas fa-landmark",
                                        "bg-warning text-dark"
                                )
                        )
                ),
                new ScheduleCategory(
                        "บำรุงรักษาระบบ & นโยบายความปลอดภัย (System Maintenance & Compliance)",
                        "fas fa-shield-halved",
                        "text-warning",
                        List.of(
                                new ScheduleItem(
                                        "กวาดล้างรูปภาพขยะ (Orphan Images Cleanup)",
                                        "ตรวจสอบและลบไฟล์รูปภาพชั่วคราวและรูปที่ไม่มีการอ้างอิงในระบบ เพื่อคืนพื้นที่จัดเก็บ Disk",
                                        imageCleanupCron,
                                        "ทุกวัน เวลา 03:00 น.",
                                        describeNext(imageCleanupCron),
                                        "fas fa-broom",
                                        "bg-warning text-dark"
                                ),
                                new ScheduleItem(
                                        "จัดเก็บและล้าง Log ตาม พ.ร.บ.คอมพิวเตอร์ (Data Retention)",
                                        "จัดเก็บ Archival Log และหมุนเวียนลบ Log ที่มีอายุเกิน 90 วัน ตามข้อกำหนด พ.ร.บ.คอมพิวเตอร์",
                                        retentionCron,
                                        "ทุกวัน เวลา 03:30 น.",
                                        describeNext(retentionCron),
                                        "fas fa-shield-alt",
                                        "bg-dark"
                                )
                        )
                ),
                new ScheduleCategory(
                        "การแจ้งเตือน & ระบบงานวิชาการ (Academic Workflows & Reminders)",
                        "fas fa-bell",
                        "text-danger",
                        List.of(
                                new ScheduleItem(
                                        "ตรวจสอบคำขอประเมินตำแหน่งที่ใกล้หมดอายุ (Evaluation Expiry)",
                                        "ตรวจสอบคำขอประเมินตำแหน่งทางวิชาการที่ใกล้ครบกำหนด และแจ้งเตือนกรรมการผู้ประเมิน",
                                        evalExpiryCron,
                                        "ทุกวัน เวลา 08:00 น.",
                                        describeNext(evalExpiryCron),
                                        "fas fa-hourglass-half",
                                        "bg-danger"
                                ),
                                new ScheduleItem(
                                        "แจ้งเตือนการลงนาม E-Signature ที่ค้างอยู่ (E-Sign Reminders)",
                                        "ส่งอีเมลแจ้งเตือนผู้ลงนามเอกสารอิเล็กทรอนิกส์ที่ยังไม่ดำเนินการเกิน 3 วัน",
                                        esignReminderCron,
                                        "ทุกวัน เวลา 08:30 น.",
                                        describeNext(esignReminderCron),
                                        "fas fa-signature",
                                        "bg-primary"
                                )
                        )
                )
        );
    }

    private String describeNext(String cron) {
        try {
            LocalDateTime next = CronExpression.parse(cron).next(LocalDateTime.now());
            if (next == null) {
                return "-";
            }
            return String.format("%02d/%02d/%d %02d:%02d น.",
                    next.getDayOfMonth(), next.getMonthValue(), next.getYear() + 543,
                    next.getHour(), next.getMinute());
        } catch (Exception e) {
            return "-";
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

    /**
     * Harvests all 5 external publication sources concurrently.
     */
    @PostMapping("/harvest-all")
    public String harvestAll(RedirectAttributes redirect) {
        if (harvestService.isRunning()) {
            redirect.addFlashAttribute("warnMsg", "ระบบกำลังดึงข้อมูลงานวิจัยอยู่แล้วในขณะนี้ กรุณารอสักครู่");
            return REDIRECT;
        }

        List<com.ecom.external.harvest.model.HarvestResult> results = harvestService.harvestAll();
        long totalWorks = results.stream().mapToLong(r -> r.publications().size()).sum();
        long successCount = results.stream().filter(com.ecom.external.harvest.model.HarvestResult::success).count();

        redirect.addFlashAttribute("succMsg", String.format(
                "ดึงงานวิจัยจาก 5 แหล่งเสร็จสิ้น — สำเร็จ %d/%d แหล่งข้อมูล รวม %d รายการ",
                successCount, results.size(), totalWorks));
        return REDIRECT;
    }

    /**
     * Asynchronous REST trigger for real-time progress modal.
     */
    @org.springframework.web.bind.annotation.PostMapping("/harvest/start")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> harvestStartAsync() {
        if (harvestService.isRunning()) {
            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "status", "ALREADY_RUNNING",
                    "message", "ระบบกำลังดึงข้อมูลงานวิจัยอยู่แล้วในขณะนี้"
            ));
        }

        harvestService.harvestAllAsync();
        return org.springframework.http.ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "message", "เริ่มต้นการดึงข้อมูลจาก 5 แหล่งข้อมูลเรียบร้อยแล้ว"
        ));
    }

    /**
     * Asynchronous REST trigger for single source.
     */
    @org.springframework.web.bind.annotation.PostMapping("/harvest/source-async/{source}")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<Map<String, Object>> harvestSourceAsync(
            @org.springframework.web.bind.annotation.PathVariable("source") String source) {
        if (harvestService.isRunning()) {
            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "status", "ALREADY_RUNNING",
                    "message", "ระบบกำลังดึงข้อมูลงานวิจัยอยู่แล้วในขณะนี้"
            ));
        }

        harvestService.harvestSourceAsync(source);
        return org.springframework.http.ResponseEntity.ok(Map.of(
                "status", "STARTED",
                "source", source,
                "message", "เริ่มต้นการดึงข้อมูลจาก " + source + " เรียบร้อยแล้ว"
        ));
    }

    /**
     * Real-time accurate % progress snapshot endpoint for polling.
     */
    @org.springframework.web.bind.annotation.GetMapping("/harvest/progress")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<com.ecom.external.harvest.HarvestProgressTracker.ProgressSnapshot> harvestProgress() {
        return org.springframework.http.ResponseEntity.ok(harvestService.getProgressTracker().snapshot());
    }

    /**
     * Harvests a single specific external source synchronously.
     */
    @PostMapping("/harvest/{source}")
    public String harvestSource(@org.springframework.web.bind.annotation.PathVariable("source") String source,
                                RedirectAttributes redirect) {
        if (harvestService.isRunning()) {
            redirect.addFlashAttribute("warnMsg", "ระบบกำลังดึงข้อมูลงานวิจัยอยู่แล้วในขณะนี้ กรุณารอสักครู่");
            return REDIRECT;
        }

        com.ecom.external.harvest.model.HarvestResult result = harvestService.harvestSource(source);
        if (result.success()) {
            redirect.addFlashAttribute("succMsg", String.format(
                    "ดึงงานวิจัยจาก %s สำเร็จ — ได้รับ %d รายการ (ใช้ %d คำขอ, %.1f วินาที)",
                    result.sourceName(), result.publications().size(), result.requestsMade(),
                    result.durationMs() / 1000.0));
        } else {
            redirect.addFlashAttribute("errorMsg", String.format(
                    "ดึงงานวิจัยจาก %s ไม่สำเร็จ: %s", result.sourceName(), result.message()));
        }
        return REDIRECT;
    }

    /**
     * Manually triggers immediate synchronization of KKU HR Regulations & Announcements.
     */
    @PostMapping("/kku-docs/run")
    public String runKkuDocsSync(RedirectAttributes redirect) {
        com.ecom.external.service.KkuDocumentSyncService.SyncResult result = kkuDocSyncService.syncNow();
        if (result.isSuccess()) {
            redirect.addFlashAttribute("succMsg", result.getMessage());
        } else {
            redirect.addFlashAttribute("errorMsg", result.getMessage());
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
