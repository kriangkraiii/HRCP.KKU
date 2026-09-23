package com.ecom.academic.dto;

import java.io.Serializable;

/**
 * DTO representing an active e-signature step awaiting a signer's action.
 */
public record PendingSignatureItem(
        Long stepId,
        Long signatureRequestId,
        String requestCode,
        String detailUrl,
        String documentTitle,
        String signerName,
        String signerRole,
        String requestedDateFormatted,
        long waitingDays,
        boolean isOverdue
) implements Serializable {

    public String waitingBadgeClass() {
        if (isOverdue || waitingDays >= 7) {
            return "badge-countdown-danger";
        } else if (waitingDays >= 3) {
            return "badge-countdown-warning";
        }
        return "badge-countdown-valid";
    }
}
