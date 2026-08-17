package com.ecom.external.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.external.model.FsFaculty;

public interface FsFacultyRepository extends JpaRepository<FsFaculty, Long> {

    /**
     * Resolves a local account to its upstream faculty record.
     *
     * <p>Both sides are normalised before comparison: the upstream feed contains
     * addresses with a trailing space, and local sign-up does not enforce case,
     * so a plain equality match loses records.
     */
    @Query("SELECT f FROM FsFaculty f WHERE LOWER(TRIM(f.email)) = LOWER(TRIM(:email))")
    Optional<FsFaculty> findByEmailNormalized(@Param("email") String email);

    /** Faculty who actually have a Scopus author id — the only ones worth pulling papers for. */
    @Query("SELECT f FROM FsFaculty f WHERE f.scopusId IS NOT NULL AND TRIM(f.scopusId) <> ''")
    List<FsFaculty> findAllWithScopusId();

    /** Cursor for the next incremental pull. */
    @Query("SELECT MAX(f.sourceUpdatedAt) FROM FsFaculty f")
    OffsetDateTime findMaxSourceUpdatedAt();

    long countByIsActive(String isActive);
}
