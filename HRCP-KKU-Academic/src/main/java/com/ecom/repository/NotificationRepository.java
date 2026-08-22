package com.ecom.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecom.model.Notification;
import com.ecom.model.UserDtls;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientOrderByCreatedAtDesc(UserDtls recipient);

    // ================= Optimized Tab Counts (Single Query) =================

    @Query("SELECT " +
           "SUM(CASE WHEN n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN n.isDeleted = false AND n.isRead = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN n.isDeleted = false AND n.isStarred = true THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN n.isDeleted = false AND n.isImportant = true THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN n.isDeleted = false AND n.snoozedUntil > :now THEN 1L ELSE 0L END), " +
           "SUM(CASE WHEN n.isDeleted = true THEN 1L ELSE 0L END) " +
           "FROM Notification n WHERE n.recipient = :recipient")
    List<Object[]> getAggregatedTabCounts(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now)")
    long countUnreadActive(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now);

    // ================= Tab Queries (Without Search - With JOIN FETCH Actor) =================

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now)")
    Page<Notification> findAllActive(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now)")
    Page<Notification> findUnread(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isStarred = true AND n.isDeleted = false ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isStarred = true AND n.isDeleted = false")
    Page<Notification> findStarred(@Param("recipient") UserDtls recipient, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isImportant = true AND n.isDeleted = false ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isImportant = true AND n.isDeleted = false")
    Page<Notification> findImportant(@Param("recipient") UserDtls recipient, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.snoozedUntil > :now AND n.isDeleted = false ORDER BY n.snoozedUntil ASC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.snoozedUntil > :now AND n.isDeleted = false")
    Page<Notification> findSnoozed(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isDeleted = true ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isDeleted = true")
    Page<Notification> findTrash(@Param("recipient") UserDtls recipient, Pageable pageable);

    // ================= Tab Queries (With Search Keyword - With JOIN FETCH Actor) =================

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchAllActive(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, @Param("kw") String kw, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :now) AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchUnread(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, @Param("kw") String kw, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isStarred = true AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isStarred = true AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchStarred(@Param("recipient") UserDtls recipient, @Param("kw") String kw, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isImportant = true AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isImportant = true AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchImportant(@Param("recipient") UserDtls recipient, @Param("kw") String kw, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.snoozedUntil > :now AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.snoozedUntil ASC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.snoozedUntil > :now AND n.isDeleted = false AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchSnoozed(@Param("recipient") UserDtls recipient, @Param("now") LocalDateTime now, @Param("kw") String kw, Pageable pageable);

    @Query(value = "SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.recipient = :recipient AND n.isDeleted = true AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%'))) ORDER BY n.createdAt DESC",
           countQuery = "SELECT COUNT(n) FROM Notification n WHERE n.recipient = :recipient AND n.isDeleted = true AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :kw, '%')) OR LOWER(n.message) LIKE LOWER(CONCAT('%', :kw, '%')))")
    Page<Notification> searchTrash(@Param("recipient") UserDtls recipient, @Param("kw") String kw, Pageable pageable);

    // ================= Modifying Queries =================

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient = :recipient AND n.isRead = false AND n.isDeleted = false")
    void markAllAsReadByRecipient(@Param("recipient") UserDtls recipient);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.recipient = :recipient")
    void markAsRead(@Param("id") Long id, @Param("recipient") UserDtls recipient);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient = :recipient AND n.isDeleted = true")
    void emptyTrashByRecipient(@Param("recipient") UserDtls recipient);

    // ================= Data Retention Queries =================

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isDeleted = true AND n.createdAt < :cutoff")
    int deleteDeletedNotificationsBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteAncientNotificationsBefore(@Param("cutoff") LocalDateTime cutoff);
}
