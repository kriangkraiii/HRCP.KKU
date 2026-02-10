package com.ecom.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;
import com.ecom.service.AdminLogService;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminLogServiceImpl implements AdminLogService {

	@Autowired
	private AdminLogRepository adminLogRepository;

	@Override
	public void logAction(String adminEmail, String adminName, String action, String details, String ipAddress) {
		AdminLog log = new AdminLog(adminEmail, adminName, action, details, ipAddress);
		adminLogRepository.save(log);
	}

	@Override
	public Page<AdminLog> getAllLogs(Integer pageNo, Integer pageSize) {
		Pageable pageable = PageRequest.of(pageNo, pageSize);
		return adminLogRepository.findAllByOrderByTimestampDesc(pageable);
	}

	@Override
	public List<AdminLog> getLogsByAdmin(String adminEmail) {
		return adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
	}

	@Override
	public List<AdminLog> getLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
		return adminLogRepository.findByDateRange(startDate, endDate);
	}

	@Override
	public List<AdminLog> getLogsByAction(String action) {
		return adminLogRepository.findByActionContaining(action);
	}
}
