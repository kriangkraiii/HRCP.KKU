package com.ecom.academic.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ecom.academic.model.SignatureModule;
import com.ecom.model.UserDtls;

/**
 * A flattened snapshot of what a notification needs to say.
 *
 * <p>Exists because notifications are sent {@code @Async}. Handing an entity to
 * another thread hands it a detached object with lazy associations that can no
 * longer be loaded — the notification then fails with a
 * {@code LazyInitializationException} on a background thread, where nobody sees
 * it and the message is simply never delivered. Everything is resolved on the
 * caller's thread, inside the transaction, and only plain values cross over.
 *
 * @param recipients who to tell; already-loaded accounts, not proxies
 */
public record SignatureNotice(
        Long envelopeId,
        SignatureModule module,
        Long requestId,
        String documentLabel,
        String verificationCode,
        String progressLabel,
        LocalDateTime dueAt,
        Long stepId,
        String roleLabel,
        String signerName,
        String declineReason,
        List<UserDtls> recipients) {

    /** A label that is always safe to print. */
    public String safeDocumentLabel() {
        return documentLabel == null || documentLabel.isBlank()
                ? "เอกสารของคำร้อง #" + requestId
                : documentLabel;
    }
}
