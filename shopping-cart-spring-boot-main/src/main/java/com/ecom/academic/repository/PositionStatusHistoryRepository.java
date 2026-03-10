package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.academic.model.PositionStatusHistory;

public interface PositionStatusHistoryRepository extends JpaRepository<PositionStatusHistory, Long> {

    @Query("SELECT h FROM PositionStatusHistory h WHERE h.request.id = :requestId ORDER BY h.changedAt DESC")
    List<PositionStatusHistory> findByRequestId(@Param("requestId") Long requestId);
}
