package com.ecom.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.AdminFile;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.AcademicRequestRepository;
import com.ecom.academic.repository.AdminFileRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.Notification;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.repository.UserRepository;
import com.ecom.search.service.SearchQueryNormalizer;

@Service
public class GlobalSearchService {

    private final AcademicRequestRepository academicRequestRepository;
    private final PositionRequestRepository positionRequestRepository;
    private final AdminFileRepository adminFileRepository;
    private final UserRepository userRepository;
    private final StaffMemberRepository staffMemberRepository;
    private final NotificationRepository notificationRepository;

    public GlobalSearchService(
            AcademicRequestRepository academicRequestRepository,
            PositionRequestRepository positionRequestRepository,
            AdminFileRepository adminFileRepository,
            UserRepository userRepository,
            StaffMemberRepository staffMemberRepository,
            NotificationRepository notificationRepository) {
        this.academicRequestRepository = academicRequestRepository;
        this.positionRequestRepository = positionRequestRepository;
        this.adminFileRepository = adminFileRepository;
        this.userRepository = userRepository;
        this.staffMemberRepository = staffMemberRepository;
        this.notificationRepository = notificationRepository;
    }

    public static class SearchResultItem {
        private String category;
        private String title;
        private String subtitle;
        private String url;
        private String icon;
        private String badge;
        private String badgeClass;

        public SearchResultItem(String category, String title, String subtitle, String url, String icon, String badge, String badgeClass) {
            this.category = category;
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.icon = icon;
            this.badge = badge;
            this.badgeClass = badgeClass;
        }

        // Getters
        public String getCategory() { return category; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }
        public String getUrl() { return url; }
        public String getIcon() { return icon; }
        public String getBadge() { return badge; }
        public String getBadgeClass() { return badgeClass; }
    }

    /**
     * Searches everything this user is allowed to see.
     *
     * <p>Queries shorter than {@link SearchQueryNormalizer#MIN_QUERY_LENGTH}
     * return nothing rather than everything. Thai has no word boundaries, so a
     * single character is a substring of a large share of the corpus — {@code ศ}
     * alone appears in ศาสตราจารย์, เอกสาร, ประกาศ and การศึกษา — and matching on
     * one would drown the result list in noise.
     */
    public List<SearchResultItem> search(String query, UserDtls user) {
        List<SearchResultItem> results = new ArrayList<>();
        if (user == null) return results;

        String kw = SearchQueryNormalizer.normalize(query);
        if (kw == null) return results;

        String pattern = SearchQueryNormalizer.likePattern(query);
        boolean isAdmin = "ROLE_ADMIN".equals(user.getRole());

        if (isAdmin) {
            searchForAdmin(kw, pattern, user, results);
        } else {
            searchForUser(kw, pattern, user, results);
        }

        return results;
    }

    private void searchForUser(String kw, String pattern, UserDtls user, List<SearchResultItem> results) {
        // 1. Navigation & Quick Links (User Scope only)
        matchUserNavigation(kw, results);

        // 2. Academic Requests (Applicant Scope Only)
        List<AcademicRequest> acadRequests = academicRequestRepository.searchByApplicant(user.getId(), pattern);
        for (AcademicRequest req : acadRequests) {
            String title = "คำร้องขอประเมินการสอน (" + (req.getRequestCode() != null ? req.getRequestCode() : "#" + req.getId()) + ")";
            String subtitle = "สถานะ: " + (req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "-");
            String statusBadge = req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "";
            results.add(new SearchResultItem(
                    "คำร้องขอประเมินผลการสอน",
                    title,
                    subtitle,
                    "/user/academic/dashboard",
                    "fas fa-clipboard-check text-primary",
                    statusBadge,
                    "bg-primary"
            ));
        }

        // 3. Position Requests (Applicant Scope Only)
        List<PositionRequest> posRequests = positionRequestRepository.searchByApplicant(user.getId(), pattern);
        for (PositionRequest req : posRequests) {
            String title = "คำร้องขอตำแหน่ง (" + (req.getRequestCode() != null ? req.getRequestCode() : "#" + req.getId()) + ") " + (req.getTargetPosition() != null ? req.getTargetPosition() : "");
            String subtitle = "สาขา: " + (req.getMajor() != null ? req.getMajor() : "-") + " | สถานะ: " + (req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "-");
            String statusBadge = req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "";
            results.add(new SearchResultItem(
                    "คำร้องขอตำแหน่งทางวิชาการ",
                    title,
                    subtitle,
                    "/user/position/request/" + req.getId(),
                    "fas fa-university text-info",
                    statusBadge,
                    "bg-info"
            ));
        }

        // 4. User Personal Files — ถูกปิดไปทั้งฟีเจอร์
        //
        // คลังไฟล์ส่วนตัวของผู้ยื่นถูกยกเลิก ตาราง user_file ถูก drop แล้ว
        // จึงไม่มีอะไรให้ค้น เก็บโค้ดเดิมไว้เป็นคอมเมนต์
        //
        // List<UserFile> files = userFileRepository.searchByOwner(user.getId(), kw);
        // for (UserFile f : files) {
        //     results.add(new SearchResultItem(
        //             "ไฟล์ของฉัน",
        //             f.getOriginalFilename(),
        //             "ขนาด: " + formatFileSize(f.getFileSize()),
        //             "/user/academic/storage",
        //             "fas fa-file-alt text-secondary",
        //             "ไฟล์",
        //             "bg-secondary"
        //     ));
        // }

        // 5. User In-App Notifications
        List<Notification> notifs = notificationRepository.searchAllActive(user, java.time.LocalDateTime.now(), kw, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        for (Notification n : notifs) {
            results.add(new SearchResultItem(
                    "การแจ้งเตือน",
                    n.getTitle(),
                    n.getMessage(),
                    "/notifications/open/" + n.getId(),
                    "fas fa-bell text-warning",
                    "แจ้งเตือน",
                    "bg-warning text-dark"
            ));
        }
    }

    private void searchForAdmin(String kw, String pattern, UserDtls user, List<SearchResultItem> results) {
        // 1. Navigation & Quick Links (Admin Scope)
        matchAdminNavigation(kw, results);

        // 2. Academic Requests (All Applicants)
        List<AcademicRequest> acadRequests = academicRequestRepository.searchForAdmin(pattern);
        for (AcademicRequest req : acadRequests) {
            String applicantName = req.getApplicant() != null ? req.getApplicant().getName() : "-";
            String title = "คำร้องประเมิน (" + (req.getRequestCode() != null ? req.getRequestCode() : "#" + req.getId()) + ") - " + applicantName;
            String subtitle = "สถานะ: " + (req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "-");
            String statusBadge = req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "";
            results.add(new SearchResultItem(
                    "คำร้องประเมินผลการสอน",
                    title,
                    subtitle,
                    "/admin/academic/requests?type=evaluation",
                    "fas fa-clipboard-check text-primary",
                    statusBadge,
                    "bg-primary"
            ));
        }

        // 3. Position Requests (All Applicants)
        List<PositionRequest> posRequests = positionRequestRepository.searchForAdmin(pattern);
        for (PositionRequest req : posRequests) {
            String applicantName = req.getApplicant() != null ? req.getApplicant().getName() : "-";
            String title = "คำร้องขอตำแหน่ง (" + (req.getRequestCode() != null ? req.getRequestCode() : "#" + req.getId()) + ") - " + applicantName;
            String subtitle = "ตำแหน่ง: " + (req.getTargetPosition() != null ? req.getTargetPosition() : "-") + " | สถานะ: " + (req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "-");
            String statusBadge = req.getCurrentStatus() != null ? req.getCurrentStatus().getThaiLabel() : "";
            results.add(new SearchResultItem(
                    "คำร้องขอตำแหน่งทางวิชาการ",
                    title,
                    subtitle,
                    "/admin/academic/requests?type=position",
                    "fas fa-university text-info",
                    statusBadge,
                    "bg-info"
            ));
        }

        // 4. Staff Members
        List<StaffMember> staffList = staffMemberRepository.searchStaff(kw);
        for (StaffMember s : staffList) {
            String title = (s.getAcademicTitle() != null ? s.getAcademicTitle() + " " : "") + s.getFirstName() + " " + s.getLastName();
            String subtitle = "สังกัด: " + (s.getDepartment() != null ? s.getDepartment() : "-") + " | ประเภท: " + (s.getStaffType() != null ? s.getStaffType() : "-");
            results.add(new SearchResultItem(
                    "จัดการบุคลากร",
                    title,
                    subtitle,
                    "/admin/academic/staff",
                    "fas fa-user-tie text-success",
                    "บุคลากร",
                    "bg-success"
            ));
        }

        // 5. System Users
        List<UserDtls> users = userRepository.searchUsers(pattern);
        for (UserDtls u : users) {
            String roleThai = "ROLE_ADMIN".equals(u.getRole()) ? "ผู้ดูแลระบบ" : "ผู้ยื่นคำร้อง";
            results.add(new SearchResultItem(
                    "ผู้ใช้งานระบบ",
                    u.getName() + " (" + u.getEmail() + ")",
                    "สิทธิ์: " + roleThai + " | โทร: " + (u.getMobileNumber() != null ? u.getMobileNumber() : "-"),
                    "ROLE_ADMIN".equals(u.getRole()) ? "/admin/users?type=2" : "/admin/users?type=1",
                    "fas fa-users-cog text-secondary",
                    roleThai,
                    "bg-secondary"
            ));
        }

        // 6. Admin System Files
        List<AdminFile> files = adminFileRepository.searchAdminFiles(kw);
        for (AdminFile f : files) {
            results.add(new SearchResultItem(
                    "ไฟล์ระบบ",
                    f.getOriginalFilename(),
                    "ขนาด: " + formatFileSize(f.getFileSize()),
                    "/admin/file-manager",
                    "fas fa-folder-open text-warning",
                    "Storage",
                    "bg-warning text-dark"
            ));
        }

        // 7. Admin In-App Notifications
        List<Notification> notifs = notificationRepository.searchAllActive(user, java.time.LocalDateTime.now(), kw, org.springframework.data.domain.PageRequest.of(0, 5)).getContent();
        for (Notification n : notifs) {
            results.add(new SearchResultItem(
                    "การแจ้งเตือน",
                    n.getTitle(),
                    n.getMessage(),
                    "/notifications/open/" + n.getId(),
                    "fas fa-bell text-warning",
                    "แจ้งเตือน",
                    "bg-warning text-dark"
            ));
        }
    }

    private void matchUserNavigation(String kw, List<SearchResultItem> results) {
        if (containsAny(kw, "แดชบอร์ด", "หน้าหลัก", "dashboard", "home")) {
            results.add(new SearchResultItem("เมนูระบบ", "แดชบอร์ดงานวิชาการ", "ดูภาพรวมและสถานะคำร้องของฉัน", "/user/academic/dashboard", "fas fa-tachometer-alt text-primary", "เมนู", "bg-light text-dark border"));
        }
        if (containsAny(kw, "ประวัติ", "คำร้อง", "history", "รายการ")) {
            results.add(new SearchResultItem("เมนูระบบ", "ประวัติการยื่นคำร้อง", "ตรวจสอบรายการคำร้องทั้งหมดที่เคยยื่น", "/user/academic/history", "fas fa-history text-primary", "เมนู", "bg-light text-dark border"));
        }
        if (containsAny(kw, "ยื่นคำร้อง", "ขอประเมิน", "ประเมินการสอน", "new request", "สอน", "การสอน")) {
            results.add(new SearchResultItem("เมนูระบบ", "ยื่นคำร้องขอรับการประเมินการสอน", "แบบฟอร์มยื่นขอประเมินผลการสอนใหม่", "/user/academic/new-request", "fas fa-clipboard-check text-success", "บริการ", "bg-success"));
        }
        // "ศ" เปล่า ๆ เคยอยู่ในลิสต์นี้ และเพราะ containsAny เทียบด้วย contains
        // อักษรตัวเดียวนั้นจึง match ประกาศ, การศึกษา, ศาสตราจารย์ ฯลฯ เมนูนี้เลยโผล่
        // แทบทุกคำค้นภาษาไทย ลบทิ้งอย่างเดียวไม่พอ เพราะ min length ก็ไม่ช่วย —
        // "ประกาศ" ยาวหกตัวอักษรและยัง contains "ศ"
        //
        // คำย่อแบบไม่มีจุดก็เอามา contains ไม่ได้เหมือนกัน ไทยเขียนติดกันไม่เว้นวรรค
        // "การศึกษา" มี ร ต่อด้วย ศ ติดกันพอดี จึง contains "รศ" — คำที่ใช้บ่อยมาก
        // จึงเทียบแบบทั้งคำแทน คนพิมพ์คำย่อพวกนี้เป็นคำค้นทั้งคำอยู่แล้ว ไม่ได้ฝังกลางประโยค
        // ("ศ" ตัวเดียวไม่ต้องใส่ — สั้นกว่า MIN_QUERY_LENGTH จึงมาไม่ถึงตรงนี้ "ศ." ครอบให้แล้ว)
        if (equalsAny(kw, "ผศ", "รศ")
                || containsAny(kw, "ขอตำแหน่ง", "ตำแหน่ง", "กำหนดตำแหน่ง", "position",
                        "ผศ.", "รศ.", "ศ.", "ศาสตราจารย์")) {
            results.add(new SearchResultItem("เมนูระบบ", "ยื่นขอกำหนดตำแหน่งทางวิชาการ", "ยื่นขอ ผศ. / รศ. / ศ. (Phase 2)", "/user/position/dashboard", "fas fa-university text-info", "บริการ", "bg-info"));
        }
        if (containsAny(kw, "เอกสาร", "ข้อบังคับ", "แบบฟอร์ม", "doc", "document")) {
            results.add(new SearchResultItem("เมนูระบบ", "คลังเอกสาร / ข้อบังคับ", "ดาวน์โหลดแบบฟอร์มและข้อบังคับมหาวิทยาลัย", "/user/academic/documents", "fas fa-book-open text-primary", "เมนู", "bg-light text-dark border"));
        }
        // เมนูคลังไฟล์ส่วนตัวถูกยกเลิก ไม่มีหน้าปลายทางแล้ว
        // if (containsAny(kw, "สตอเรจ", "ไฟล์", "storage", "ไดรฟ์", "เก็บไฟล์")) {
        //     results.add(new SearchResultItem("เมนูระบบ", "ที่เก็บไฟล์ของฉัน (Storage)", "จัดการไฟล์และหลักฐานส่วนตัว", "/user/academic/storage", "fas fa-cloud text-primary", "เมนู", "bg-light text-dark border"));
        // }
        if (containsAny(kw, "คู่มือ", "guide", "วิธีใช้", "ช่วยเหลือ", "help")) {
            results.add(new SearchResultItem("เมนูระบบ", "คู่มือการใช้งานระบบ", "คำแนะนำขั้นตอนการยื่นคำร้องและการใช้งาน", "/user/academic/guide", "fas fa-book text-info", "คู่มือ", "bg-info"));
        }
        if (containsAny(kw, "แจ้งเตือน", "notification", "กล่องข้อความ", "เตือน")) {
            results.add(new SearchResultItem("เมนูระบบ", "ศูนย์การแจ้งเตือน", "ดูรายการแจ้งเตือนและสถานะคำร้องทั้งหมด", "/user/notifications", "fas fa-bell text-warning", "เมนู", "bg-warning text-dark"));
        }
        if (containsAny(kw, "ตั้งค่า", "setting", "โปรไฟล์", "รหัสผ่าน", "profile")) {
            results.add(new SearchResultItem("เมนูระบบ", "การตั้งค่าบัญชีและโปรไฟล์", "ปรับแต่งข้อมูลส่วนตัวและการแจ้งเตือน", "/user/academic/settings", "fas fa-cog text-secondary", "เมนู", "bg-secondary"));
        }
    }

    private void matchAdminNavigation(String kw, List<SearchResultItem> results) {
        if (containsAny(kw, "แดชบอร์ด", "หน้าหลัก", "dashboard", "home")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "แดชบอร์ดงานวิชาการ", "ภาพรวมคำร้องและการประเมินทั้งหมด", "/admin/academic/requests", "fas fa-tachometer-alt text-primary", "Admin", "bg-primary"));
        }
        if (containsAny(kw, "คำร้องประเมิน", "ประเมินการสอน", "evaluation")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "คำร้องประเมินผลการสอน", "จัดการและตรวจสอบคำร้องประเมินผลการสอน", "/admin/academic/requests?type=evaluation", "fas fa-clipboard-check text-primary", "Admin", "bg-primary"));
        }
        if (containsAny(kw, "คำร้องตำแหน่ง", "กำหนดตำแหน่ง", "position")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "คำร้องขอตำแหน่งทางวิชาการ", "จัดการคำร้องขอกำหนดตำแหน่ง (Phase 2)", "/admin/academic/requests?type=position", "fas fa-university text-info", "Admin", "bg-info"));
        }
        if (containsAny(kw, "บุคลากร", "กรรมการ", "staff", "อาจารย์")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "จัดการบุคลากร / กรรมการ", "รายชื่อบุคลากรและคณะกรรมการประเมิน", "/admin/academic/staff", "fas fa-users-cog text-success", "Admin", "bg-success"));
        }
        if (containsAny(kw, "ผู้ใช้", "ผู้ใช้งาน", "user")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "จัดการผู้ใช้งานทั่วไป", "ดูและจัดการบัญชีผู้ยื่นคำร้อง", "/admin/users?type=1", "fas fa-users text-secondary", "Admin", "bg-secondary"));
        }
        if (containsAny(kw, "ผู้ดูแล", "admin", "แอดมิน")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "จัดการผู้ดูแลระบบ (Admins)", "ดูรายชื่อผู้ดูแลระบบทั้งหมด", "/admin/users?type=2", "fas fa-user-shield text-danger", "Admin", "bg-danger"));
        }
        if (containsAny(kw, "เพิ่มผู้ใช้", "เพิ่มแอดมิน", "add user", "สมัคร")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "เพิ่มผู้ใช้ / แอดมินใหม่", "สร้างบัญชีผู้ใช้งานใหม่ในระบบ", "/admin/add-admin", "fas fa-user-plus text-success", "Admin", "bg-success"));
        }
        if (containsAny(kw, "ประวัติ", "กิจกรรม", "log", "activity")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "ประวัติกิจกรรมระบบ (Activity Logs)", "ตรวจสอบประวัติการเข้าใช้งานและการแก้ไข", "/admin/activity-logs", "fas fa-history text-secondary", "Admin", "bg-secondary"));
        }
        if (containsAny(kw, "ไฟล์", "จัดการไฟล์", "file", "storage")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "จัดการไฟล์ระบบ (File Manager)", "พื้นที่จัดเก็บไฟล์กลาง 10 GB", "/admin/file-manager", "fas fa-folder-open text-warning", "Admin", "bg-warning text-dark"));
        }
        if (containsAny(kw, "คู่มือ", "guide", "ช่วยเหลือ")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "คู่มือการใช้งานสำหรับแอดมิน", "ขั้นตอนการจัดการคำร้องและฟังก์ชันแอดมิน", "/admin/academic/guide", "fas fa-book text-info", "Admin", "bg-info"));
        }
        if (containsAny(kw, "แจ้งเตือน", "notification")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "ศูนย์การแจ้งเตือนแอดมิน", "จัดการข้อความแจ้งเตือนทั้งหมด", "/admin/notifications", "fas fa-bell text-warning", "Admin", "bg-warning text-dark"));
        }
        if (containsAny(kw, "ตั้งค่า", "setting")) {
            results.add(new SearchResultItem("เมนูแอดมิน", "การตั้งค่าระบบ", "ตั้งค่าระบบงานวิชาการและอีเมล", "/admin/academic/settings", "fas fa-cog text-secondary", "Admin", "bg-secondary"));
        }
    }

    private boolean containsAny(String input, String... terms) {
        for (String term : terms) {
            if (input.contains(term.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Matches a term only when it is the whole query.
     *
     * <p>For short Thai abbreviations, {@code contains} is the wrong test. Thai
     * runs words together with no spaces, so a two-character abbreviation turns
     * up inside ordinary words: การศึกษา has ร immediately followed by ศ and
     * therefore contains รศ. Whole-query matching is also how people use these —
     * they type ผศ or รศ on its own, not buried in a sentence.
     */
    private boolean equalsAny(String input, String... terms) {
        for (String term : terms) {
            if (input.equals(term.toLowerCase())) return true;
        }
        return false;
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
