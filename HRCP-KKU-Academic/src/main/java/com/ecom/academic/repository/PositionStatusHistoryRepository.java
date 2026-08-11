package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionStatusHistory;

public interface PositionStatusHistoryRepository extends JpaRepository<PositionStatusHistory, Long> {

    /**
     * Newest change first, matching
     * {@link RequestStatusHistoryRepository#findByRequestIdOrderByChangedAtDesc}.
     * The order is named rather than left to the caller's assumption — reading
     * the <em>last</em> element as "most recent" gives the submit entry instead.
     */
    @Query("SELECT h FROM PositionStatusHistory h WHERE h.request.id = :requestId ORDER BY h.changedAt DESC")
    List<PositionStatusHistory> findByRequestIdOrderByChangedAtDesc(@Param("requestId") Long requestId);
}
