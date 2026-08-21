package com.ecom.external.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.external.service.FacultyChangeReviewService;
import com.ecom.external.service.FsSyncService;

/**
 * Review screen for faculty edits coming from the Fund Management platform.
 *
 * <p>Sits under {@code /admin/**}, which the security configuration restricts to
 * {@code ROLE_ADMIN}; {@link PreAuthorize} states the same requirement locally so
 * the guarantee survives a URL change.
 */
@Controller
@RequestMapping("/admin/faculty-changes")
@PreAuthorize("hasRole('ADMIN')")
public class FacultyChangeAdminController {

    private static final String REDIRECT = "redirect:/admin/external-sync?tab=changes";

    private final FacultyChangeReviewService reviewService;
    private final FsFacultyChangeRepository changeRepo;
    private final FsSyncService syncService;

    public FacultyChangeAdminController(FacultyChangeReviewService reviewService,
            FsFacultyChangeRepository changeRepo,
            FsSyncService syncService) {
        this.reviewService = reviewService;
        this.changeRepo = changeRepo;
        this.syncService = syncService;
    }

    @GetMapping
    public String list() {
        return REDIRECT;
    }

    /** Last handful of decisions, newest first, across both outcomes. */
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

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
            @RequestParam(required = false) String note,
            Principal principal,
            RedirectAttributes redirect) {

        boolean applied = reviewService.approve(id, principal.getName(), note);
        if (applied) {
            redirect.addFlashAttribute("succMsg", "อนุมัติการเปลี่ยนแปลงเรียบร้อย ข้อมูลถูกอัปเดตแล้ว");
        } else {
            redirect.addFlashAttribute("errorMsg",
                    "ไม่สามารถอนุมัติได้ — รายการนี้อาจถูกตรวจสอบไปแล้ว");
        }
        return REDIRECT;
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
            @RequestParam(required = false) String note,
            Principal principal,
            RedirectAttributes redirect) {

        boolean done = reviewService.reject(id, principal.getName(), note);
        if (done) {
            redirect.addFlashAttribute("succMsg", "ไม่อนุมัติเรียบร้อย ข้อมูลเดิมในระบบยังคงอยู่");
        } else {
            redirect.addFlashAttribute("errorMsg",
                    "ไม่สามารถดำเนินการได้ — รายการนี้อาจถูกตรวจสอบไปแล้ว");
        }
        return REDIRECT;
    }

    @PostMapping("/approve-all")
    public String approveAll(Principal principal, RedirectAttributes redirect) {
        int applied = reviewService.approveAll(principal.getName());
        redirect.addFlashAttribute("succMsg", "อนุมัติแล้ว " + applied + " รายการ");
        return REDIRECT;
    }

    /**
     * Re-reads the directory so any upstream edit shows up immediately rather
     * than at the next nightly run. Staging only — nothing is applied here.
     */
    @PostMapping("/sync")
    public String syncNow(RedirectAttributes redirect) {
        FsSyncService.SyncResult result = syncService.syncUsers(true);
        long pending = reviewService.pendingCount();

        if (result.success()) {
            redirect.addFlashAttribute("succMsg",
                    "ตรวจสอบข้อมูลต้นทางเรียบร้อย — มีรายการรอยืนยัน " + pending + " รายการ");
        } else {
            redirect.addFlashAttribute("errorMsg",
                    "ดึงข้อมูลไม่สำเร็จ: " + result.message());
        }
        return REDIRECT;
    }
}
