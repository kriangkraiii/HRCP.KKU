package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ecom.academic.model.AcademicRevisionFile;

public interface AcademicRevisionFileRepository extends JpaRepository<AcademicRevisionFile, Long> {

    List<AcademicRevisionFile> findByRequestIdOrderByRoundDescIdAsc(Long requestId);

    @Query("select coalesce(max(f.round), 0) from AcademicRevisionFile f where f.request.id = :requestId")
    int findLatestRound(Long requestId);
}
