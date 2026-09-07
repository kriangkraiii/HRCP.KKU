package com.ecom.academic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;

public interface PositionRequestRepository extends JpaRepository<PositionRequest, Long> {

    /**
     * Loads a request with its applicant already fetched.
     *
     * <p>For background work: a notification thread must not be handed an
     * entity belonging to the request thread's still-open session, so it
     * re-reads its own copy with everything it needs already attached.
     */
    @Query("SELECT r FROM PositionRequest r LEFT JOIN FETCH r.applicant WHERE r.id = :id")
    Optional<PositionRequest> findByIdWithApplicant(@Param("id") Long id);

    @Query("SELECT r FROM PositionRequest r WHERE r.applicant.id = :userId ORDER BY r.createdAt DESC")
    List<PositionRequest> findByApplicantId(@Param("userId") Integer userId);

    /**
     * คำร้องที่ยังเดินอยู่ของผู้ยื่นคนหนึ่ง เรียงจากใหม่ไปเก่า
     *
     * <p><b>คืนเป็น List ไม่ใช่ Optional โดยตั้งใจ</b> — กติกาของระบบตั้งใจให้มีได้
     * ครั้งละหนึ่งฉบับ และ {@code hasActiveRequest} คือด่านที่บังคับเรื่องนี้ แต่
     * "ควรมีหนึ่ง" กับ "มีหนึ่งเสมอ" ไม่ใช่เรื่องเดียวกัน ข้อมูลเดินไปอยู่ในสภาพ
     * สองฉบับได้จริงโดยไม่ต้องผ่าน {@code createRequest} เลย เช่น
     *
     * <ol>
     *   <li>คำร้อง A ถูกปฏิเสธ → {@code REJECTED} ซึ่งเป็นสถานะปลายทาง</li>
     *   <li>ด่านจึงตอบว่าไม่มีคำร้องค้าง ผู้ยื่นสร้างคำร้อง B ได้ตามปกติ</li>
     *   <li>เจ้าหน้าที่ย้อนสถานะ A ออกจาก {@code REJECTED} กลับมาเป็นสถานะที่ยังเดินอยู่</li>
     * </ol>
     *
     * <p>ตอนนั้นทั้ง A และ B ต่างก็ยังเดินอยู่ และถ้าเมธอดนี้คืน {@code Optional}
     * Spring Data จะโยน {@code IncorrectResultSizeDataAccessException} ทันที
     * ผลคือ <b>ทุกหน้าที่เรียกด่านนี้ตอบ HTTP 500 ถาวร</b> — แดชบอร์ดของผู้ยื่น
     * ทั้งสองเฟสและหน้าสร้างคำร้องใหม่ — และผู้ยื่นแก้เองไม่ได้เลย
     * ด่านที่มีไว้กันไม่ให้เกิดสภาพนี้ กลายเป็นสิ่งแรกที่พังเมื่อมันเกิดขึ้น
     *
     * <p>คืนทุกแถวแล้วให้ผู้เรียกตัดสินใจเอง จึงตอบคำถาม "มีคำร้องค้างอยู่ไหม"
     * ได้ถูกต้องทั้งตอนมีศูนย์ หนึ่ง หรือมากกว่านั้น
     */
    @Query("""
            SELECT r FROM PositionRequest r
            WHERE r.applicant.id = :userId AND r.currentStatus NOT IN :terminalStatuses
            ORDER BY r.createdAt DESC
            """)
    List<PositionRequest> findActiveByApplicantId(@Param("userId") Integer userId,
            @Param("terminalStatuses") List<PositionRequestStatus> terminalStatuses);

    /**
     * This applicant's requests that have actually spent the evaluation they
     * were built on, with that evaluation already fetched.
     *
     * <p>Scoped to one applicant deliberately, not asked faculty-wide. The rule
     * it feeds removes courses from what a person may choose, and a question
     * asked across everyone would let one applicant's request take a course away
     * from a colleague who happened to be evaluated on the same one.
     *
     * @param free statuses that consume nothing — {@code DRAFT}, which has not
     *             been submitted, and {@code REJECTED}, which has to give its
     *             course back
     */
    @Query("""
            SELECT r FROM PositionRequest r
            JOIN FETCH r.linkedEvaluation
            WHERE r.applicant.id = :userId
              AND r.currentStatus NOT IN :free
            ORDER BY r.createdAt ASC
            """)
    List<PositionRequest> findConsumingEvaluations(@Param("userId") Integer userId,
            @Param("free") List<PositionRequestStatus> free);

    /**
     * แบบร่างของผู้ยื่น เรียงจากใหม่ไปเก่า
     *
     * <p>คืนเป็น List ด้วยเหตุผลเดียวกับ {@link #findActiveByApplicantId} — ระบบ
     * ตั้งใจให้มีแบบร่างได้ฉบับเดียว แต่ถ้าข้อมูลไปอยู่ในสภาพสองฉบับด้วยเหตุใดก็ตาม
     * เมธอดนี้ต้องตอบได้ ไม่ใช่ทำให้ทั้งหน้าตอบ 500
     */
    @Query("""
            SELECT r FROM PositionRequest r
            WHERE r.applicant.id = :userId AND r.currentStatus = 'DRAFT'
            ORDER BY r.createdAt DESC
            """)
    List<PositionRequest> findDraftByApplicantId(@Param("userId") Integer userId);

    @Query("SELECT r FROM PositionRequest r ORDER BY r.createdAt DESC")
    List<PositionRequest> findAllOrderByCreatedAtDesc();

    @Query("SELECT r FROM PositionRequest r WHERE r.currentStatus = :status ORDER BY r.createdAt DESC")
    List<PositionRequest> findByStatus(@Param("status") PositionRequestStatus status);

    @Query("SELECT COUNT(r) FROM PositionRequest r")
    long countAll();

    @Query("SELECT MAX(r.id) FROM PositionRequest r")
    Optional<Long> findMaxId();

    /**
     * Requests whose applicant matches by name or e-mail.
     *
     * <p>Every name column is listed because {@code applicant.name} is the
     * legacy combined column and SSO never writes it — searching it alone
     * returns nothing for anyone the directory provisioned. See
     * {@link com.ecom.repository.UserRepository#searchUsers}.
     *
     * @param pattern a {@code %term%} pattern from
     *                {@link com.ecom.search.service.SearchQueryNormalizer#likePattern}
     */
    @Query("""
            SELECT r FROM PositionRequest r
            WHERE LOWER(COALESCE(r.applicant.firstName, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastName, '')) LIKE :pattern
               OR LOWER(CONCAT(COALESCE(r.applicant.firstName, ''), ' ', COALESCE(r.applicant.lastName, ''))) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.name, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.email, '')) LIKE :pattern
            ORDER BY r.createdAt DESC
            """)
    List<PositionRequest> searchByNameOrEmail(@Param("pattern") String pattern);

    /** One applicant's own requests, matched on code, id, target position or major. */
    @Query("""
            SELECT r FROM PositionRequest r
            WHERE r.applicant.id = :userId
              AND (LOWER(COALESCE(r.requestCode, '')) LIKE :pattern
                OR LOWER(CAST(r.id AS string)) LIKE :pattern
                OR LOWER(COALESCE(r.targetPosition, '')) LIKE :pattern
                OR LOWER(COALESCE(r.major, '')) LIKE :pattern)
            ORDER BY r.createdAt DESC
            """)
    List<PositionRequest> searchByApplicant(@Param("userId") Integer userId, @Param("pattern") String pattern);

    /** Every request, matched on code, id, applicant names, target position or major. */
    @Query("""
            SELECT r FROM PositionRequest r
            WHERE LOWER(COALESCE(r.requestCode, '')) LIKE :pattern
               OR LOWER(CAST(r.id AS string)) LIKE :pattern
               OR LOWER(COALESCE(r.targetPosition, '')) LIKE :pattern
               OR LOWER(COALESCE(r.major, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstName, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastName, '')) LIKE :pattern
               OR LOWER(CONCAT(COALESCE(r.applicant.firstName, ''), ' ', COALESCE(r.applicant.lastName, ''))) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.firstNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.lastNameEn, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.name, '')) LIKE :pattern
               OR LOWER(COALESCE(r.applicant.email, '')) LIKE :pattern
            ORDER BY r.createdAt DESC
            """)
    List<PositionRequest> searchForAdmin(@Param("pattern") String pattern);
}
