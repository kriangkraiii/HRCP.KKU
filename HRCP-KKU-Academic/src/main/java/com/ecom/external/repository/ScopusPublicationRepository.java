package com.ecom.external.repository;

import java.util.Collection;
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
 *
 * <p>The free-text searches take a ready-made {@code pattern} — {@code %term%},
 * already lower-cased — rather than building it in the query with
 * {@code CONCAT('%', :q, '%')}. That form looks tidier and is a trap on
 * PostgreSQL: with no search term the parameter is null, both operands of
 * {@code ||} are then untyped, and the server resolves the concatenation to
 * {@code bytea}, failing with "function lower(bytea) does not exist" the moment
 * anyone opens the screen without typing anything. Comparing against a column
 * gives the parameter a type and the ambiguity disappears.
 */
public interface ScopusPublicationRepository extends JpaRepository<ScopusPublication, Long> {

    /** Upsert target for the sync — matches the {@code (fs_user_id, eid)} unique key. */
    Optional<ScopusPublication> findByFsUserIdAndEid(Long fsUserId, String eid);

    List<ScopusPublication> findByFsUserIdOrderByPublicationYearDescCitedByDesc(Long fsUserId);

    /**
     * The picker's query. Optional year window and free-text filter are folded in
     * so the database does the work rather than shipping every row to the JVM.
     * Hits {@code idx_scopus_pub_user_year}.
     *
     * @param excluded publications already submitted on a position request that
     *                 has left {@code DRAFT} (GAP-12). Filtered here rather than
     *                 after paging: dropping rows from a page the database already
     *                 counted would leave {@code total} disagreeing with what the
     *                 picker shows. Never empty — callers pass a list holding an
     *                 impossible id when nothing is spent, because {@code IN ()}
     *                 is not valid SQL.
     */
    @Query("""
            SELECT p FROM ScopusPublication p
            WHERE p.fsUserId = :fsUserId
              AND p.id NOT IN :excluded
              AND (:pattern IS NULL OR LOWER(p.title) LIKE :pattern
                                    OR LOWER(p.publicationName) LIKE :pattern
                                    OR LOWER(p.doi) LIKE :pattern)
              AND (:yearFrom IS NULL OR p.publicationYear >= :yearFrom)
              AND (:yearTo   IS NULL OR p.publicationYear <= :yearTo)
            ORDER BY p.publicationYear DESC, p.citedBy DESC
            """)
    Page<ScopusPublication> findOwnedBy(@Param("fsUserId") Long fsUserId,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo,
            @Param("pattern") String pattern,
            @Param("excluded") Collection<Long> excluded,
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
              AND (:pattern IS NULL OR LOWER(p.title) LIKE :pattern
                                    OR LOWER(p.authorNames) LIKE :pattern
                                    OR LOWER(p.publicationName) LIKE :pattern
                                    OR LOWER(p.abstractText) LIKE :pattern
                                    OR LOWER(p.authKeywords) LIKE :pattern)
              AND (:yearFrom IS NULL OR p.publicationYear >= :yearFrom)
              AND (:yearTo   IS NULL OR p.publicationYear <= :yearTo)
            ORDER BY p.publicationYear DESC, p.citedBy DESC
            """)
    Page<ScopusPublication> adminSearch(@Param("fsUserId") Long fsUserId,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo,
            @Param("pattern") String pattern,
            Pageable pageable);

    long countByFsUserId(Long fsUserId);

    // ---- Faculty-wide totals for the admin overview ----

    @Query("SELECT COALESCE(SUM(p.citedBy), 0) FROM ScopusPublication p")
    long sumAllCitations();

    /** How many professors have at least one publication on record. */
    @Query("SELECT COUNT(DISTINCT p.fsUserId) FROM ScopusPublication p")
    long countAuthorsWithPublications();

    /**
     * Who to offer in the "filter by professor" list.
     *
     * <p>Only people who actually have publications: a dropdown of every name in
     * the directory would be mostly dead ends.
     */
    @Query("SELECT DISTINCT p.fsUserId FROM ScopusPublication p")
    List<Long> findDistinctAuthorIds();

    /** Years that have any publication, newest first — populates the year filter. */
    @Query("SELECT DISTINCT p.publicationYear FROM ScopusPublication p "
            + "WHERE p.publicationYear IS NOT NULL ORDER BY p.publicationYear DESC")
    List<Integer> findDistinctYears();

    // ---- Multi-source harvesting and deduplication queries (V14) ----

    Optional<ScopusPublication> findByFsUserIdAndDoiIgnoreCase(Long fsUserId, String doi);

    Optional<ScopusPublication> findByFsUserIdAndDedupHash(Long fsUserId, String dedupHash);

    Optional<ScopusPublication> findByFsUserIdAndExternalId(Long fsUserId, String externalId);

    long countByDataSource(String dataSource);

    @Query("SELECT p.dataSource, COUNT(p) FROM ScopusPublication p GROUP BY p.dataSource")
    List<Object[]> countGroupByDataSource();

    /**
     * Level 2.5 Deduplication: PostgreSQL pg_trgm trigram similarity match on title
     * within the same author and publication year.
     */
    @Query(value = """
            SELECT id FROM scopus_publication
            WHERE fs_user_id = :fsUserId
              AND publication_year = :year
              AND similarity(LOWER(title), LOWER(:candidateTitle)) >= :threshold
            ORDER BY similarity(LOWER(title), LOWER(:candidateTitle)) DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Long> findFuzzyMatchId(@Param("fsUserId") Long fsUserId,
                                     @Param("candidateTitle") String candidateTitle,
                                     @Param("year") int year,
                                     @Param("threshold") double threshold);
}

