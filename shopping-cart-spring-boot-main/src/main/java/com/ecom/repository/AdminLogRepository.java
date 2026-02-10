package com.ecom.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.ecom.model.AdminLog;
import java.time.LocalDateTime;
import java.util.List;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

	Page<AdminLog> findAllByOrderByTimestampDesc(Pageable pageable);

	List<AdminLog> findByAdminEmailOrderByTimestampDesc(String adminEmail);

	@Query("SELECT a FROM AdminLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
	List<AdminLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate);

	@Query("SELECT a FROM AdminLog a WHERE a.action LIKE %:action% ORDER BY a.timestamp DESC")
	List<AdminLog> findByActionContaining(@Param("action") String action);
}
