package com.ecom.academic.dto;

import java.util.List;

import com.ecom.academic.model.SignatureModule;

public record DocumentWorkflowGroupDTO(
        SignatureModule module,
        int documentType,
        String documentTitle,
        List<DocumentWorkflowSlotDTO> slots) {
}
