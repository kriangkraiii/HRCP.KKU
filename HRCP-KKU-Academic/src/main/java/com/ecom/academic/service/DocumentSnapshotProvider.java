package com.ecom.academic.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.SignatureModule;

/**
 * Reads a document's saved form data and label, whichever module it belongs to.
 *
 * <p>The two request flows store documents in separate tables with separate
 * services. The signing feature does not care which — it needs the JSON to
 * freeze and a name to show — so that difference is resolved here instead of
 * being repeated at every call site.
 */
@Service
public class DocumentSnapshotProvider {

    private static final Logger log = LoggerFactory.getLogger(DocumentSnapshotProvider.class);

    private final AcademicRequestService academicRequestService;
    private final PositionRequestService positionRequestService;

    public DocumentSnapshotProvider(AcademicRequestService academicRequestService,
            PositionRequestService positionRequestService) {
        this.academicRequestService = academicRequestService;
        this.positionRequestService = positionRequestService;
    }

    /**
     * Whether this account is the applicant behind a request.
     *
     * <p>Applicants send their own documents for signature — several forms are
     * signed by the applicant alone — so the signing endpoints need a way to
     * authorise that without granting them anything on other people's requests.
     *
     * @return false when the request does not exist or belongs to someone else
     */
    public boolean isApplicantOf(SignatureModule module, Long requestId, Integer userId) {
        if (requestId == null || userId == null) {
            return false;
        }
        try {
            if (module == SignatureModule.ACADEMIC) {
                return academicRequestService.findById(requestId)
                        .map(r -> r.getApplicant() != null && userId.equals(r.getApplicant().getId()))
                        .orElse(false);
            }
            return positionRequestService.findById(requestId)
                    .map(r -> r.getApplicant() != null && userId.equals(r.getApplicant().getId()))
                    .orElse(false);
        } catch (Exception e) {
            // Deny on error: an ownership check that fails open is worse than
            // one that occasionally refuses a legitimate request.
            log.warn("Could not resolve ownership of {} request {}: {}", module, requestId, e.toString());
            return false;
        }
    }

    /** The document's Thai title, or a generic fallback. */
    public String labelFor(SignatureModule module, int documentType) {
        try {
            String label = module == SignatureModule.ACADEMIC
                    ? AcademicRequestService.getDocLabel(documentType)
                    : positionRequestService.getDocLabel(documentType);
            if (label != null && !label.isBlank()) {
                return label;
            }
        } catch (Exception e) {
            log.warn("Could not resolve label for {} doc {}: {}", module, documentType, e.toString());
        }
        return "เอกสารที่ " + documentType;
    }

    /**
     * The most recently saved form data for a document.
     *
     * @return the stored JSON, or null when the document has never been saved —
     *         which is what stops an empty document being sent for signature
     */
    public String currentJsonFor(SignatureModule module, Long requestId, int documentType) {
        try {
            if (module == SignatureModule.ACADEMIC) {
                List<AcademicDocument> documents =
                        academicRequestService.getDocumentsByType(requestId, documentType);
                return documents.isEmpty() ? null : documents.get(0).getJsonData();
            }
            List<PositionDocument> documents =
                    positionRequestService.getDocumentsByType(requestId, documentType);
            return documents.isEmpty() ? null : documents.get(0).getJsonData();
        } catch (Exception e) {
            log.warn("Could not read saved data for {} request {} doc {}: {}",
                    module, requestId, documentType, e.toString());
            return null;
        }
    }
}
