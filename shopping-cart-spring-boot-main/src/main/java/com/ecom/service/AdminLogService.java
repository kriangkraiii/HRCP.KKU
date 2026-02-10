package com.ecom.service;

import org.springframework.data.domain.Page;
import com.ecom.model.AdminLog;
import java.time.LocalDateTime;
import java.util.List;

public interface AdminLogService {

	void logAction(String adminEmail, String adminName, String action, String details, String ipAddress);

	Page<AdminLog> getAllLogs(Integer pageNo, Integer pageSize);

	List<AdminLog> getLogsByAdmin(String adminEmail);

	List<AdminLog> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

	List<AdminLog> getLogsByAction(String action);
}
