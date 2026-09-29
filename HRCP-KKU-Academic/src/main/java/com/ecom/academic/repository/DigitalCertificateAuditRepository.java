package com.ecom.academic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.academic.model.DigitalCertificateAudit;

public interface DigitalCertificateAuditRepository extends JpaRepository<DigitalCertificateAudit, Long> {

    List<DigitalCertificateAudit> findByUserIdOrderByIdAsc(Integer userId);
}
