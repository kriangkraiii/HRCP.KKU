package com.ecom.external.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ecom.external.model.JournalTier;

@Repository
public interface JournalTierRepository extends JpaRepository<JournalTier, Long> {

    Optional<JournalTier> findByIssnAndProviderAndYear(String issn, String provider, int year);

    List<JournalTier> findByIssnOrEissn(String issn, String eissn);

    @Query("""
            SELECT jt FROM JournalTier jt
            WHERE (jt.issn = :issn OR jt.eissn = :issn OR (jt.eissn IS NOT NULL AND jt.eissn = :eissn) OR (jt.issn IS NOT NULL AND jt.issn = :eissn))
              AND (:year IS NULL OR jt.year = :year)
            ORDER BY jt.year DESC
            """)
    List<JournalTier> findByIssnOrEissnAndYear(@Param("issn") String issn, @Param("eissn") String eissn, @Param("year") Integer year);

    @Query("""
            SELECT jt FROM JournalTier jt
            WHERE (jt.issn = :issn OR jt.eissn = :issn OR (jt.eissn IS NOT NULL AND jt.eissn = :eissn) OR (jt.issn IS NOT NULL AND jt.issn = :eissn))
            ORDER BY jt.year DESC
            """)
    List<JournalTier> findLatestByIssnOrEissn(@Param("issn") String issn, @Param("eissn") String eissn);
}
