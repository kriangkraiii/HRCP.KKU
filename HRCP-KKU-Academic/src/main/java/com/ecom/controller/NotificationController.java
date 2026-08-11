package com.ecom.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

    @PostMapping("/api/notifications/{id}/read")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.markAsRead(id, user);
        resp.put("success", success);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
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
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/star")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStar(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        Boolean isStarred = notificationService.toggleStar(id, user);
        resp.put("success", isStarred != null);
        resp.put("isStarred", isStarred != null && isStarred);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/important")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleImportant(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        Boolean isImportant = notificationService.toggleImportant(id, user);
        resp.put("success", isImportant != null);
        resp.put("isImportant", isImportant != null && isImportant);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/snooze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> snooze(
            @PathVariable Long id,
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
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/unsnooze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unsnooze(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.unsnooze(id, user);
        resp.put("success", success);
        resp.put("unreadCount", notificationService.getUnreadCount(user));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> softDelete(@PathVariable Long id, Principal principal) {
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
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/restore")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restore(@PathVariable Long id, Principal principal) {
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
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/api/notifications/{id}/permanent-delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> permanentDelete(@PathVariable Long id, Principal principal) {
        UserDtls user = getUser(principal);
        Map<String, Object> resp = new HashMap<>();
        if (user == null) {
            resp.put("success", false);
            return ResponseEntity.status(401).body(resp);
        }

        boolean success = notificationService.permanentDelete(id, user);
        resp.put("success", success);
        resp.put("message", "ลบการแจ้งเตือนถาวรเรียบร้อยแล้ว");
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
