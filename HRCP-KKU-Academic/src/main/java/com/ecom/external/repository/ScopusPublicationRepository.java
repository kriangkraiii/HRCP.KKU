package com.ecom.external.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.external.model.ScopusPublication;

/**
 * Reads are deliberately owner-scoped: every finder here takes an
 * {@code fsUserId}, so there is no method a controller could call that returns
 * another professor's publications by accident. Admin-wide views go through the
 * explicitly named {@code adminSearch} method instead.
 */
public interface ScopusPublicationRepository extends JpaRepository<ScopusPublication, Long> {

    /** Upsert target for the sync — matches the {@code (fs_user_id, eid)} unique key. */
    Optional<ScopusPublication> findByFsUserIdAndEid(Long fsUserId, String eid);

    List<ScopusPublication> findByFsUserIdOrderByPublicationYearDescCitedByDesc(Long fsUserId);

    /**
     * The picker's query. Optional year window and free-text filter are folded in
     * so the database does the work rather than shipping every row to the JVM.
     * Hits {@code idx_scopus_pub_user_year}.
     */
    @Query("""
            SELECT p FROM ScopusPublication p
            WHERE p.fsUserId = :fsUserId
              AND (:yearFrom IS NULL OR p.publicationYear >= :yearFrom)
              AND (:yearTo   IS NULL OR p.publicationYear <= :yearTo)
              AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(p.publicationName) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(p.doi) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.publicationYear DESC, p.citedBy DESC
            """)
    Page<ScopusPublication> findOwnedBy(@Param("fsUserId") Long fsUserId,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo,
            @Param("q") String q,
            Pageable pageable);

    /**
     * Aggregates the three numbers the position forms ask for, in one pass:
     * paper count, total citations and the h-index input set.
     */
    @Query("SELECT COUNT(p) FROM ScopusPublication p WHERE p.fsUserId = :fsUserId")
    long countOwnedBy(@Param("fsUserId") Long fsUserId);

    @Query("SELECT COALESCE(SUM(p.citedBy), 0) FROM ScopusPublication p WHERE p.fsUserId = :fsUserId")
    long sumCitationsOwnedBy(@Param("fsUserId") Long fsUserId);

    /** Citation counts, highest first — h-index is computed from this list. */
    @Query("SELECT COALESCE(p.citedBy, 0) FROM ScopusPublication p "
            + "WHERE p.fsUserId = :fsUserId ORDER BY p.citedBy DESC")
    List<Integer> findCitationCountsOwnedBy(@Param("fsUserId") Long fsUserId);

    /** Single-row fetch that still carries the owner check — never look up by id alone. */
    Optional<ScopusPublication> findByIdAndFsUserId(Long id, Long fsUserId);

    /**
     * Faculty-wide search. Named so that calling it is a deliberate act;
     * the controller layer gates it behind {@code ROLE_ADMIN}.
     */
    @Query("""
            SELECT p FROM ScopusPublication p
            WHERE (:fsUserId IS NULL OR p.fsUserId = :fsUserId)
              AND (:q IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(p.authorNames) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY p.publicationYear DESC, p.citedBy DESC
            """)
    Page<ScopusPublication> adminSearch(@Param("fsUserId") Long fsUserId,
            @Param("q") String q,
            Pageable pageable);

    long countByFsUserId(Long fsUserId);
}
