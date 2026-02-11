package com.ecom.service;

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

    public List<AdminLog> getLogsByAdmin(String adminEmail) {
        return adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
    }

    public List<AdminLog> searchByAction(String action) {
        return adminLogRepository.findByActionContaining(action);
    }
}
