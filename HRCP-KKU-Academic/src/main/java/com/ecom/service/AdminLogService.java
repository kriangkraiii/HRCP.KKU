package com.ecom.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;

import java.util.ArrayList;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

@Service
public class AdminLogService {

    private final AdminLogRepository adminLogRepository;

    public AdminLogService(AdminLogRepository adminLogRepository) {
        this.adminLogRepository = adminLogRepository;
    }

    @Async("auditLogExecutor")
    public void log(String adminEmail, String adminName, String action, String details, String ipAddress) {
        AdminLog log = new AdminLog(adminEmail, adminName, action, details, ipAddress);
        adminLogRepository.save(log);
    }

    @Async("auditLogExecutor")
    public void logWithDetails(String email, String name, String action, String details, String ip, String resource,
            String userAgent) {
        AdminLog log = new AdminLog(email, name, action, details, ip, resource, userAgent);
        adminLogRepository.save(log);
    }

    public Page<AdminLog> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return adminLogRepository.findAll(pageable);
    }

    public Page<AdminLog> getAllLogs(int page, int size, String search, String action, String dateFrom, String dateTo) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Specification<AdminLog> spec = createFilterSpecification(search, action, dateFrom, dateTo);
        return adminLogRepository.findAll(spec, pageable);
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

    public List<AdminLog> getAllLogsForExport(String search, String action, String dateFrom, String dateTo) {
        Specification<AdminLog> spec = createFilterSpecification(search, action, dateFrom, dateTo);
        return adminLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "timestamp"));
    }

    private Specification<AdminLog> createFilterSpecification(String search, String action, String dateFrom, String dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                String keyword = "%" + search.trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("adminName")), keyword);
                Predicate emailLike = cb.like(cb.lower(root.get("adminEmail")), keyword);
                Predicate detailsLike = cb.like(cb.lower(root.get("details")), keyword);
                predicates.add(cb.or(nameLike, emailLike, detailsLike));
            }

            if (action != null && !action.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }

            if (dateFrom != null && !dateFrom.isEmpty()) {
                LocalDateTime startDate = LocalDate.parse(dateFrom).atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), startDate));
            }

            if (dateTo != null && !dateTo.isEmpty()) {
                LocalDateTime endDate = LocalDate.parse(dateTo).atTime(23, 59, 59);
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
