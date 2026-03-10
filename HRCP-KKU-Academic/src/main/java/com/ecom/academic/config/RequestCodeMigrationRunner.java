package com.ecom.academic.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.repository.AcademicRequestRepository;

/**
 * One-time migration: backfill requestCode for existing records.
 * Safe to run multiple times — only updates records with null requestCode.
 */
@Component
@Order(1)
public class RequestCodeMigrationRunner implements CommandLineRunner {

    private final AcademicRequestRepository requestRepository;

    public RequestCodeMigrationRunner(AcademicRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Override
    public void run(String... args) {
        List<AcademicRequest> requests = requestRepository.findAll();
        int updated = 0;
        for (AcademicRequest request : requests) {
            if (request.getRequestCode() == null || request.getRequestCode().isEmpty()) {
                request.generateRequestCode();
                requestRepository.save(request);
                updated++;
            }
        }
        if (updated > 0) {
            System.out.println("[Migration] Backfilled requestCode for " + updated + " existing request(s).");
        }
    }
}
