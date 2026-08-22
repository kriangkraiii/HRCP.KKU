package com.ecom.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.repository.UserRepository;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ================= Send Notifications =================

    /**
     * ส่งการแจ้งเตือนไปยังผู้ใช้รายบุคคล
     */
    public Notification sendNotification(UserDtls recipient, UserDtls actor, String title, String message, String link, NotificationType type, Boolean isImportant) {
        if (recipient == null) {
            log.warn("Cannot send notification: recipient is null");
            return null;
        }
        try {
            Notification notification = new Notification(recipient, actor, title, message, link, type, isImportant);
            return notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to save notification for user {}: {}", recipient.getEmail(), e.getMessage());
            return null;
        }
    }

    /**
     * ส่งการแจ้งเตือนไปยังผู้ดูแลระบบ (Admin) ทุกคน
     */
    public void notifyAdmins(UserDtls actor, String title, String message, String link, NotificationType type, Boolean isImportant) {
        try {
            List<UserDtls> admins = userRepository.findByRole("ROLE_ADMIN");
            for (UserDtls admin : admins) {
                sendNotification(admin, actor, title, message, link, type, isImportant);
            }
        } catch (Exception e) {
            log.error("Failed to notify admins: {}", e.getMessage());
        }
    }

    // ================= Query Notifications =================

    @Transactional(readOnly = true)
    public Page<Notification> getNotifications(UserDtls user, String tab, String search, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        String currentTab = tab != null ? tab.trim().toLowerCase() : "all";
        boolean hasSearch = search != null && !search.trim().isEmpty();
        String kw = hasSearch ? search.trim() : "";

        if (hasSearch) {
            return switch (currentTab) {
                case "unread" -> notificationRepository.searchUnread(user, now, kw, pageable);
                case "starred" -> notificationRepository.searchStarred(user, kw, pageable);
                case "important" -> notificationRepository.searchImportant(user, kw, pageable);
                case "snoozed" -> notificationRepository.searchSnoozed(user, now, kw, pageable);
                case "trash" -> notificationRepository.searchTrash(user, kw, pageable);
                default -> notificationRepository.searchAllActive(user, now, kw, pageable);
            };
        } else {
            return switch (currentTab) {
                case "unread" -> notificationRepository.findUnread(user, now, pageable);
                case "starred" -> notificationRepository.findStarred(user, pageable);
                case "important" -> notificationRepository.findImportant(user, pageable);
                case "snoozed" -> notificationRepository.findSnoozed(user, now, pageable);
                case "trash" -> notificationRepository.findTrash(user, pageable);
                default -> notificationRepository.findAllActive(user, now, pageable);
            };
        }
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserDtls user) {
        if (user == null) return 0;
        return notificationRepository.countUnreadActive(user, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTabCounts(UserDtls user) {
        Map<String, Long> counts = new HashMap<>();
        if (user == null) return counts;

        List<Object[]> res = notificationRepository.getAggregatedTabCounts(user, LocalDateTime.now());
        if (res != null && !res.isEmpty() && res.get(0) != null) {
            Object[] row = res.get(0);
            counts.put("all", row[0] != null ? ((Number) row[0]).longValue() : 0L);
            counts.put("unread", row[1] != null ? ((Number) row[1]).longValue() : 0L);
            counts.put("starred", row[2] != null ? ((Number) row[2]).longValue() : 0L);
            counts.put("important", row[3] != null ? ((Number) row[3]).longValue() : 0L);
            counts.put("snoozed", row[4] != null ? ((Number) row[4]).longValue() : 0L);
            counts.put("trash", row[5] != null ? ((Number) row[5]).longValue() : 0L);
        } else {
            counts.put("all", 0L);
            counts.put("unread", 0L);
            counts.put("starred", 0L);
            counts.put("important", 0L);
            counts.put("snoozed", 0L);
            counts.put("trash", 0L);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecentNotifications(UserDtls user, int limit) {
        if (user == null) return List.of();
        Page<Notification> page = notificationRepository.findAllActive(user, LocalDateTime.now(), PageRequest.of(0, Math.max(1, limit)));
        return page.getContent();
    }

    @Transactional(readOnly = true)
    public Optional<Notification> findByIdAndRecipient(Long id, UserDtls user) {
        return notificationRepository.findById(id).filter(n -> n.getRecipient().getId().equals(user.getId()));
    }

    // ================= Actions =================

    public boolean markAsRead(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            n.setIsRead(true);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public boolean markAsUnread(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            n.setIsRead(false);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public void markAllAsRead(UserDtls user) {
        notificationRepository.markAllAsReadByRecipient(user);
    }

    public Boolean toggleStar(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            boolean newVal = !n.getIsStarred();
            n.setIsStarred(newVal);
            notificationRepository.save(n);
            return newVal;
        }
        return null;
    }

    public Boolean toggleImportant(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            boolean newVal = !n.getIsImportant();
            n.setIsImportant(newVal);
            notificationRepository.save(n);
            return newVal;
        }
        return null;
    }

    public boolean snooze(Long id, String duration, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isEmpty()) return false;

        Notification n = opt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until;

        switch (duration != null ? duration.toLowerCase() : "1h") {
            case "1h", "1hour" -> until = now.plusHours(1);
            case "3h", "3hours" -> until = now.plusHours(3);
            case "tomorrow" -> {
                // พรุ่งนี้ 09:00 น.
                LocalDate tomorrow = now.toLocalDate().plusDays(1);
                until = LocalDateTime.of(tomorrow, LocalTime.of(9, 0));
            }
            case "weekend", "nextweek" -> {
                // วันจันทร์ถัดไป 09:00 น.
                LocalDate nextMon = now.toLocalDate().plusDays((8 - now.getDayOfWeek().getValue()) % 7 == 0 ? 7 : (8 - now.getDayOfWeek().getValue()) % 7);
                until = LocalDateTime.of(nextMon, LocalTime.of(9, 0));
            }
            case "3d", "3days" -> until = now.plusDays(3);
            case "1w", "1week" -> until = now.plusWeeks(1);
            default -> until = now.plusHours(1);
        }

        n.setSnoozedUntil(until);
        notificationRepository.save(n);
        return true;
    }

    public boolean unsnooze(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            n.setSnoozedUntil(null);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public boolean softDelete(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            n.setIsDeleted(true);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public boolean restore(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            Notification n = opt.get();
            n.setIsDeleted(false);
            notificationRepository.save(n);
            return true;
        }
        return false;
    }

    public boolean permanentDelete(Long id, UserDtls user) {
        Optional<Notification> opt = findByIdAndRecipient(id, user);
        if (opt.isPresent()) {
            notificationRepository.delete(opt.get());
            return true;
        }
        return false;
    }

    public void emptyTrash(UserDtls user) {
        notificationRepository.emptyTrashByRecipient(user);
    }
}
