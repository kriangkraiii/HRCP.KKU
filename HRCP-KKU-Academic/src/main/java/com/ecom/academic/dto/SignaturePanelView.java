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
        SignerOptionDTO currentUserOption,
        SignatureRequest activeEnvelope,
        boolean signable) {

    /** True while the document is out for signature or already fully signed. */
    public boolean isLocked() {
        return activeEnvelope != null;
    }

    /** True when a fresh round can be started. */
    public boolean canSend() {
        return signable && activeEnvelope == null;
    }

    /** Role-matched candidates for one slot, never null. */
    public List<SignerOptionDTO> optionsFor(String slotKey) {
        return recommendedOptions.getOrDefault(slotKey, List.of());
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
        return new SignaturePanelView(List.of(), Map.of(), List.of(), null, null, false);
    }
}
