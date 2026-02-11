package com.ecom.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;

@Service
public class AdminLogService {

    @Autowired
    private AdminLogRepository adminLogRepository;

    public void log(String adminEmail, String adminName, String action, String details, String ipAddress) {
        AdminLog log = new AdminLog(adminEmail, adminName, action, details, ipAddress);
        adminLogRepository.save(log);
    }

    public Page<AdminLog> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return adminLogRepository.findAllByOrderByTimestampDesc(pageable);
    }

    public Page<AdminLog> getAllLogs(int page, int size, String search, String action, String dateFrom, String dateTo) {
        Pageable pageable = PageRequest.of(page, size);

        // Parse dates
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;
        if (dateFrom != null && !dateFrom.isEmpty()) {
            startDate = LocalDate.parse(dateFrom).atStartOfDay();
        }
        if (dateTo != null && !dateTo.isEmpty()) {
            endDate = LocalDate.parse(dateTo).atTime(23, 59, 59);
        }

        // Normalize empty strings to null
        if (search != null && search.trim().isEmpty()) search = null;
        if (action != null && action.trim().isEmpty()) action = null;

        return adminLogRepository.findByFilters(search, action, startDate, endDate, pageable);
    }

    public List<AdminLog> getLogsByAdmin(String adminEmail) {
        return adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
    }

    public List<AdminLog> searchByAction(String action) {
        return adminLogRepository.findByActionContaining(action);
    }

    public long countByAction(String action) {
        return adminLogRepository.countByAction(action);
    }
}
