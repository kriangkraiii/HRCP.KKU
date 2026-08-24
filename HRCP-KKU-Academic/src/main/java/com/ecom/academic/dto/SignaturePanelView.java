package com.ecom.academic.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;

/**
 * Everything the "ส่งไปลงนาม" panel needs for one document.
 *
 * <p>Assembled by the controller so the template contains no lookups: which
 * signature positions this document has, who could fill each of them, and
 * whether a round is already in progress.
 *
 * <p>Candidates come in two groups. {@code recommendedOptions} holds the people
 * actually tagged with the slot's role — the usual answer. {@code otherOptions}
 * holds everyone else who has a login, because the role tags are an
 * administrator's own bookkeeping and are often incomplete: restricting the
 * dropdown to them would make it impossible to send a document to a real person
 * the system simply has not been told about yet. That includes administrators,
 * who frequently need to sign in their own right.
 *
 * @param slots              signature positions, in signing order
 * @param recommendedOptions role-matched candidates keyed by {@code slotKey}
 * @param otherOptions       every other person who could be asked to sign
 * @param currentUserOption  the signed-in person, for "ลงนามเอง"; null if they
 *                           have no account usable as a signer
 * @param activeEnvelope     the round currently circulating, or null
 * @param signable           whether this document has any signature position
 */
public record SignaturePanelView(
        List<SignatureSlot> slots,
        Map<String, List<SignerOptionDTO>> recommendedOptions,
        List<SignerOptionDTO> otherOptions,
        Map<String, Integer> defaultSignerUserIds,
        SignerOptionDTO applicantOption,
        SignerOptionDTO currentUserOption,
        SignatureRequest activeEnvelope,
        boolean signable,
        boolean isAdminViewer) {

    public SignaturePanelView(
            List<SignatureSlot> slots,
            Map<String, List<SignerOptionDTO>> recommendedOptions,
            List<SignerOptionDTO> otherOptions,
            Map<String, Integer> defaultSignerUserIds,
            SignerOptionDTO applicantOption,
            SignerOptionDTO currentUserOption,
            SignatureRequest activeEnvelope,
            boolean signable) {
        this(slots, recommendedOptions, otherOptions, defaultSignerUserIds, applicantOption, currentUserOption, activeEnvelope, signable, false);
    }

    /** True while the document is out for signature or already fully signed. */
    public boolean isLocked() {
        return activeEnvelope != null;
    }

    /** True when a fresh round can be started. */
    public boolean canSend() {
        return signable && activeEnvelope == null;
    }

    /** True if the applicant has already signed their slot in the active round. */
    public boolean isApplicantSigned() {
        if (activeEnvelope == null || activeEnvelope.getSteps() == null) {
            return false;
        }
        return activeEnvelope.getSteps().stream()
                .filter(s -> "applicant".equalsIgnoreCase(s.getSlotKey()))
                .anyMatch(s -> s.getStatus() == com.ecom.academic.model.SignatureStepStatus.SIGNED);
    }

    /** True if this document includes an applicant slot. */
    public boolean hasApplicantSlot() {
        if (slots == null) return false;
        return slots.stream().anyMatch(s -> "applicant".equalsIgnoreCase(s.slotKey()));
    }

    /** True if this document includes staff, head, dean or committee slots. */
    public boolean hasNonApplicantSlots() {
        if (slots == null) return false;
        return slots.stream().anyMatch(s -> !"applicant".equalsIgnoreCase(s.slotKey()));
    }

    /**
     * Slots that still have nobody assigned in the current round.
     *
     * <p>A round only ever contains the positions that were filled in when it
     * was started; the rest are simply absent. Those are what an administrator
     * fills in later when forwarding the document onward.
     */
    public List<SignatureSlot> unfilledSlots() {
        if (slots == null) {
            return List.of();
        }
        if (activeEnvelope == null || activeEnvelope.getSteps() == null) {
            return List.of();
        }
        List<SignatureSlot> rest = new ArrayList<>();
        for (SignatureSlot slot : slots) {
            boolean taken = activeEnvelope.getSteps().stream()
                    .anyMatch(step -> slot.slotKey().equalsIgnoreCase(step.getSlotKey())
                            && step.getStatus() != com.ecom.academic.model.SignatureStepStatus.SKIPPED);
            if (!taken) {
                rest.add(slot);
            }
        }
        return rest;
    }

    /** True when some position on this document has still not been sent to anyone. */
    public boolean hasUnfilledSlots() {
        return !unfilledSlots().isEmpty();
    }

    /**
     * True when an administrator may still add signers to this round.
     *
     * <p>Includes rounds already marked complete: a document whose applicant
     * signature is done is exactly the one waiting to be forwarded to the head
     * of department and the dean.
     */
    public boolean isForwardable() {
        if (activeEnvelope == null || activeEnvelope.getStatus() == null) {
            return false;
        }
        boolean stillOpen = activeEnvelope.getStatus().isOpen()
                || activeEnvelope.getStatus() == com.ecom.academic.model.SignatureRequestStatus.COMPLETED;
        return stillOpen && hasUnfilledSlots();
    }

    /**
     * True when signers are lined up but staff have not released the document.
     *
     * <p>Nothing is wrong; the document is deliberately parked until somebody
     * has read it. Saying so matters because a held round looks exactly like a
     * broken one from the outside.
     */
    public boolean isAwaitingStaffRelease() {
        if (activeEnvelope == null || activeEnvelope.getSteps() == null
                || !activeEnvelope.getStatus().isOpen()
                || activeEnvelope.isCirculationStarted()) {
            return false;
        }
        return activeEnvelope.getSteps().stream()
                .anyMatch(s -> s.getStatus() == com.ecom.academic.model.SignatureStepStatus.WAITING
                        && !"applicant".equalsIgnoreCase(s.getSlotKey()));
    }

    /**
     * True when staff can release this document right now.
     *
     * <p>Signers are already chosen, so all that is needed is the decision that
     * the document is correct — no need to fill the picker in again.
     */
    public boolean isStartable() {
        return isAwaitingStaffRelease();
    }

    /** Role-matched candidates for one slot, never null. */
    public List<SignerOptionDTO> optionsFor(String slotKey) {
        return recommendedOptions.getOrDefault(slotKey, List.of());
    }

    /** Default configured signer user ID for a slot, if any. */
    public Integer defaultSignerFor(String slotKey) {
        return defaultSignerUserIds != null ? defaultSignerUserIds.get(slotKey) : null;
    }

    /**
     * Everyone else who could sign this slot.
     *
     * <p>Excludes anyone already listed as recommended for it, so the dropdown
     * never shows the same person twice.
     */
    public List<SignerOptionDTO> otherOptionsFor(String slotKey) {
        List<SignerOptionDTO> recommended = optionsFor(slotKey);
        List<SignerOptionDTO> rest = new ArrayList<>();
        for (SignerOptionDTO option : otherOptions) {
            boolean alreadyShown = recommended.stream()
                    .anyMatch(r -> r.userId() != null && r.userId().equals(option.userId()));
            if (!alreadyShown) {
                rest.add(option);
            }
        }
        return rest;
    }

    /** An empty panel, for documents with no signature block. */
    public static SignaturePanelView unsignable() {
        return new SignaturePanelView(List.of(), Map.of(), List.of(), Map.of(), null, null, null, false, false);
    }
}
