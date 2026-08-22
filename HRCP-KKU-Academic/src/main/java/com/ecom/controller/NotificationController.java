package com.ecom.controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ecom.model.Notification;
import com.ecom.model.UserDtls;
import com.ecom.service.NotificationService;
import com.ecom.service.UserService;

@Controller
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    private UserDtls getUser(Principal principal) {
        if (principal == null) return null;
        return userService.getUserByEmail(principal.getName());
    }

    // ================= Web Pages =================

    @GetMapping({"/admin/notifications", "/user/notifications"})
    public String notificationPage(
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal,
            Model model) {

        UserDtls user = getUser(principal);
        if (user == null) return "redirect:/signin";

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Notification> notifications = notificationService.getNotifications(user, tab, search, pageable);
        Map<String, Long> tabCounts = notificationService.getTabCounts(user);

        model.addAttribute("notifications", notifications);
        model.addAttribute("currentTab", tab);
        model.addAttribute("search", search);
        model.addAttribute("tabCounts", tabCounts);
        model.addAttribute("isAdmin", "ROLE_ADMIN".equals(user.getRole()));

        return "academic/notifications";
    }

    /**
     * คลิกที่การแจ้งเตือนเพื่อเปิดไปยังหน้าที่เกี่ยวข้อง พร้อมทำเครื่องหมายอ่านแล้วอัตโนมัติ
     */
    @GetMapping("/notifications/open/{id}")
    public String openNotification(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        if (user == null) return "redirect:/signin";

        Optional<Notification> opt = notificationService.findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            notificationService.markAsRead(id, user);
            if (n.getLink() != null && !n.getLink().isBlank()) {
                return "redirect:" + n.getLink();
            }
        }
        return "ROLE_ADMIN".equals(user.getRole()) ? "redirect:/admin/notifications" : "redirect:/user/notifications";
    }

    // ================= REST AJAX APIs =================

    @GetMapping("/api/notifications/recent")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecentNotifications(
            @RequestParam(defaultValue = "5") int limit,
            Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        List<Notification> notifs = notificationService.getRecentNotifications(user, limit);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<Map<String, Object>> items = notifs.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("link", n.getLink() != null ? n.getLink() : "");
            m.put("type", n.getType() != null ? n.getType().name() : "SYSTEM");
            m.put("typeLabel", n.getType() != null ? n.getType().getThaiLabel() : "การแจ้งเตือน");
            m.put("iconClass", n.getType() != null ? n.getType().getIconClass() : "fas fa-bell text-primary");
            m.put("relativeTime", n.getRelativeTime());
            m.put("formattedDate", n.getCreatedAt() != null ? n.getCreatedAt().format(dtf) : "");
            m.put("isRead", n.getIsRead());
            m.put("isStarred", n.getIsStarred());
            m.put("isImportant", n.getIsImportant());
            m.put("actorName", n.getActor() != null ? n.getActor().getName() : "ระบบ");
            return m;
        }).collect(Collectors.toList());

        resp.put("success", true);
        resp.put("notifications", items);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/notifications/{id}/detail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getNotificationDetail(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        Optional<Notification> opt = notificationService.findByIdAndRecipient(id, user);
        if (opt.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "ไม่พบการแจ้งเตือน");
            return ResponseEntity.status(404).body(resp);
        }

        Notification n = opt.get();
        // Auto-mark as read on reading detail
        notificationService.markAsRead(id, user);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm น.", java.util.Locale.forLanguageTag("th-TH"));
        Map<String, Object> data = new HashMap<>();
        data.put("id", n.getId());
        data.put("title", n.getTitle());
        data.put("message", n.getMessage());
        data.put("link", n.getLink() != null ? n.getLink() : "");
        data.put("type", n.getType() != null ? n.getType().name() : "SYSTEM");
        data.put("typeLabel", n.getType() != null ? n.getType().getThaiLabel() : "การแจ้งเตือน");
        data.put("iconClass", n.getType() != null ? n.getType().getIconClass() : "fas fa-bell text-primary");
        data.put("relativeTime", n.getRelativeTime());
        data.put("formattedDate", n.getCreatedAt() != null ? n.getCreatedAt().format(dtf) : "");
        data.put("isRead", true);
        data.put("isStarred", n.getIsStarred());
        data.put("isImportant", n.getIsImportant());
        data.put("actorName", n.getActor() != null ? n.getActor().getName() : "ระบบ");
        data.put("isSnoozed", n.isSnoozed());

        resp.put("success", true);
        resp.put("notification", data);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/unread")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAsUnread(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.markAsUnread(id, user);
        resp.put("success", success);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.markAsRead(id, user);
        resp.put("success", success);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/read-all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAllAsRead(Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        notificationService.markAllAsRead(user);
        resp.put("success", true);
        resp.put("message", "ทำเครื่องหมายอ่านทั้งหมดแล้ว");
        resp.put("unreadCount", 0);
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/star")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStar(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        Boolean isStarred = notificationService.toggleStar(id, user);
        resp.put("success", isStarred != null);
        resp.put("isStarred", isStarred != null && isStarred);
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/important")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleImportant(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        Boolean isImportant = notificationService.toggleImportant(id, user);
        resp.put("success", isImportant != null);
        resp.put("isImportant", isImportant != null && isImportant);
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/snooze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> snooze(
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1h") String duration,
            Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.snooze(id, duration, user);
        resp.put("success", success);
        resp.put("message", "เลื่อนการแจ้งเตือนเรียบร้อยแล้ว");
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/unsnooze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unsnooze(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.unsnooze(id, user);
        resp.put("success", success);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> softDelete(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.softDelete(id, user);
        resp.put("success", success);
        resp.put("message", "ย้ายการแจ้งเตือนไปที่ถังขยะแล้ว");
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.restore(id, user);
        resp.put("success", success);
        resp.put("message", "กู้คืนการแจ้งเตือนแล้ว");
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/permanent-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDelete(@PathVariable("id") Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.permanentDelete(id, user);
        resp.put("success", success);
        resp.put("message", "ลบการแจ้งเตือนถาวรเรียบร้อยแล้ว");
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/empty-trash")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> emptyTrash(Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        notificationService.emptyTrash(user);
        resp.put("success", true);
        resp.put("message", "ล้างถังขยะทั้งหมดแล้ว");
        resp.put("tabCounts", notificationService.getTabCounts(user));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUnreadCount(Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        long count = notificationService.getUnreadCount(user);
        resp.put("unreadCount", count);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/api/notifications/tab-counts")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> getTabCounts(Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Long> counts = notificationService.getTabCounts(user);
        return ResponseEntity.ok(counts);
    }
}
