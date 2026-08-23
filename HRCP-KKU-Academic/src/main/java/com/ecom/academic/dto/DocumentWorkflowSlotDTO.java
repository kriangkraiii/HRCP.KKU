package com.ecom.academic.dto;

import com.ecom.academic.model.SignatureModule;

public record DocumentWorkflowSlotDTO(
        SignatureModule module,
        int documentType,
        String slotKey,
        String roleLabel,
        String anchorPlaceholder,
        String defaultStaffRole,
        int stepOrder,
        boolean isEnabled,
        Integer defaultSignerUserId,
        String defaultSignerName) {
}
