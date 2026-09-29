package com.ecom.academic.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.DigitalCertificateAudit;
import com.ecom.academic.model.DigitalCertificateAudit.Event;
import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.DigitalCertificateAuditRepository;
import com.ecom.model.UserDtls;

/**
 * Writes the certificate trail.
 *
 * <p>Its own transaction: a wrong PIN is recorded even when the caller's work is
 * refused, and a failure here can never roll back a signature.
 */
@Service
public class DigitalCertificateAuditService {

    private static final Logger log = LoggerFactory.getLogger(DigitalCertificateAuditService.class);

    private final DigitalCertificateAuditRepository repository;

    public DigitalCertificateAuditService(DigitalCertificateAuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UserDtls user, UserDigitalCertificate cert, Event event, String detail, String ipAddress) {
        try {
            repository.save(new DigitalCertificateAudit(
                    user != null ? user.getId() : null,
                    cert != null ? cert.getId() : null,
                    event, detail, ipAddress));
        } catch (RuntimeException e) {
            log.warn("Could not record certificate event {} for user {}: {}", event,
                    user != null ? user.getId() : null, e.toString());
        }
    }
}
