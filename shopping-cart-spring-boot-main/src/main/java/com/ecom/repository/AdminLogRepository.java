package com.ecom.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ecom.model.AdminLog;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

	Page<AdminLog> findAllByOrderByTimestampDesc(Pageable pageable);

	List<AdminLog> findByAdminEmailOrderByTimestampDesc(String adminEmail);

	@Query("SELECT a FROM AdminLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
	List<AdminLog> findByDateRange(@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate);

	@Query("SELECT a FROM AdminLog a WHERE a.action LIKE %:action% ORDER BY a.timestamp DESC")
	List<AdminLog> findByActionContaining(@Param("action") String action);

	long countByAction(String action);

	@Query("SELECT a FROM AdminLog a WHERE " +
			"(:search IS NULL OR (LOWER(a.adminName) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(a.adminEmail) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%')))) " +
			"AND (:action IS NULL OR a.action = :action) " +
			"AND (:startDate IS NULL OR a.timestamp >= :startDate) " +
			"AND (:endDate IS NULL OR a.timestamp <= :endDate) " +
			"ORDER BY a.timestamp DESC")
	Page<AdminLog> findByFilters(@Param("search") String search,
			@Param("action") String action,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate,
			Pageable pageable);

	@Query("SELECT a FROM AdminLog a WHERE " +
			"(:search IS NULL OR (LOWER(a.adminName) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(a.adminEmail) LIKE LOWER(CONCAT('%', :search, '%')) " +
			"OR LOWER(a.details) LIKE LOWER(CONCAT('%', :search, '%')))) " +
			"AND (:action IS NULL OR a.action = :action) " +
			"AND (:startDate IS NULL OR a.timestamp >= :startDate) " +
			"AND (:endDate IS NULL OR a.timestamp <= :endDate) " +
			"ORDER BY a.timestamp DESC")
	List<AdminLog> findByFiltersForExport(@Param("search") String search,
			@Param("action") String action,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate);
}
