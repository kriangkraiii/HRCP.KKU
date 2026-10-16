package com.ecom.academic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;

public interface PositionRequestPublicationRepository
        extends JpaRepository<PositionRequestPublication, Long> {

    List<PositionRequestPublication> findByRequestIdAndDocumentType(Long requestId, Integer documentType);

    List<PositionRequestPublication> findByRequestId(Long requestId);

    @Modifying
    @Transactional
    void deleteByRequestIdAndDocumentType(Long requestId, Integer documentType);

    /**
     * The publications this applicant has already spent.
     *
     * <p>Scoped to one applicant on purpose, not queried faculty-wide. The link
     * rows come from a form, and a form can be tampered with: someone could
     * submit another professor's publication id and, on a global query, make that
     * professor's own work vanish from their picker. Scoping the answer to the
     * asker means a forged id can only ever cost the person who forged it.
     *
     * @param free the statuses that do not consume anything — only {@code DRAFT},
     *             because nothing has been submitted yet
     */
    @Query("""
            SELECT l.publicationId FROM PositionRequestPublication l
            WHERE l.request.applicant.id = :applicantId
              AND l.request.currentStatus NOT IN :free
            """)
    List<Long> findSpentPublicationIds(@Param("applicantId") Integer applicantId,
            @Param("free") Collection<PositionRequestStatus> free);

    // ---- รวมผลงานซ้ำ (PublicationMergeService) ----

    /** คำร้องที่ลิงก์ไว้กับทั้งสองแถวอยู่แล้ว — ลบลิงก์ของแถวที่ถูกรวม ไม่อย่างนั้นการย้ายชน unique (request_id, publication_id) */
    @Modifying
    @Query(value = """
            DELETE FROM position_request_publication
            WHERE publication_id = :loser
              AND request_id IN (SELECT l.request_id FROM position_request_publication l WHERE l.publication_id = :survivor)
            """, nativeQuery = true)
    int dropLinksAlreadyOn(@Param("loser") Long loser, @Param("survivor") Long survivor);

    /** ลิงก์ที่เหลือย้ายไปแถวที่เหลือ — ผลงานที่ใช้ไปแล้วยังนับว่าใช้แล้ว */
    @Modifying
    @Query(value = "UPDATE position_request_publication SET publication_id = :survivor WHERE publication_id = :loser",
            nativeQuery = true)
    int moveLinks(@Param("loser") Long loser, @Param("survivor") Long survivor);
}
