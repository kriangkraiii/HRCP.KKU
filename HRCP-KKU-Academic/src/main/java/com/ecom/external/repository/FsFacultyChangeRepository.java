package com.ecom.external.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.external.model.FsFacultyChange;

public interface FsFacultyChangeRepository extends JpaRepository<FsFacultyChange, Long> {

    List<FsFacultyChange> findByStatusOrderByDetectedAtDesc(String status);

    Page<FsFacultyChange> findByStatusOrderByReviewedAtDesc(String status, Pageable pageable);

    /**
     * The open change for one person, if any.
     *
     * <p>A re-sync must update the existing pending row rather than queue a
     * second one, otherwise a field that changes nightly would flood the review
     * page with near-duplicates.
     */
    Optional<FsFacultyChange> findFirstByFsUserIdAndStatus(Long fsUserId, String status);

    long countByStatus(String status);
}
