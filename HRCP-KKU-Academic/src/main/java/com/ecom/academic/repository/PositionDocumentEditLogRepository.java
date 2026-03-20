package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.PositionDocumentEditLog;
import com.ecom.academic.model.PositionRequest;

public interface PositionDocumentEditLogRepository extends JpaRepository<PositionDocumentEditLog, Long> {

    List<PositionDocumentEditLog> findByRequestOrderByEditedAtDesc(PositionRequest request);
}
