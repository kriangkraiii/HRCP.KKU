package com.ecom.academic.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.dto.DocumentWorkflowGroupDTO;
import com.ecom.academic.dto.DocumentWorkflowSlotDTO;
import com.ecom.academic.model.DocumentWorkflowConfig;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.DocumentWorkflowConfigRepository;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@Service
public class DocumentWorkflowConfigService {

    private static final Logger log = LoggerFactory.getLogger(DocumentWorkflowConfigService.class);

    private final DocumentWorkflowConfigRepository repository;
    private final SignatureAnchorRegistry registry;
    private final DocumentSnapshotProvider snapshotProvider;
    private final StaffMemberService staffMemberService;
    private final UserRepository userRepository;

    public DocumentWorkflowConfigService(
            DocumentWorkflowConfigRepository repository,
            SignatureAnchorRegistry registry,
            DocumentSnapshotProvider snapshotProvider,
            StaffMemberService staffMemberService,
            UserRepository userRepository) {
        this.repository = repository;
        this.registry = registry;
        this.snapshotProvider = snapshotProvider;
        this.staffMemberService = staffMemberService;
        this.userRepository = userRepository;
    }

    /**
     * Returns the active signature slots for a document, in configured step order.
     * Falls back to SignatureAnchorRegistry if no database config exists yet.
     */
    public List<SignatureSlot> effectiveSlotsFor(SignatureModule module, int documentType) {
        List<DocumentWorkflowConfig> configs = repository.findByModuleAndDocumentType(module, documentType);
        if (configs == null || configs.isEmpty()) {
            return registry.slotsFor(module, documentType);
        }

        return configs.stream()
                .filter(DocumentWorkflowConfig::isEnabled)
                .sorted(Comparator.comparingInt(DocumentWorkflowConfig::getStepOrder))
                .map(c -> new SignatureSlot(
                        c.getSlotKey(),
                        c.getRoleLabel(),
                        c.getAnchorPlaceholder(),
                        c.getDefaultStaffRole(),
                        c.getStepOrder()))
                .toList();
    }

    /**
     * Returns default signer user IDs for each slotKey of a document.
     * Prefers custom config from DB; falls back to automatic role matching from staff members.
     */
    public Map<String, Integer> defaultSignerUserIds(SignatureModule module, int documentType) {
        Map<String, Integer> defaults = new HashMap<>();
        List<DocumentWorkflowConfig> configs = repository.findByModuleAndDocumentType(module, documentType);
        Map<String, DocumentWorkflowConfig> configMap = new HashMap<>();
        if (configs != null) {
            for (DocumentWorkflowConfig c : configs) {
                configMap.put(c.getSlotKey(), c);
            }
        }

        List<SignatureSlot> slots = registry.slotsFor(module, documentType);
        for (SignatureSlot slot : slots) {
            DocumentWorkflowConfig cfg = configMap.get(slot.slotKey());
            if (cfg != null && cfg.getDefaultSigner() != null) {
                defaults.put(slot.slotKey(), cfg.getDefaultSigner().getId());
                continue;
            }

            // Fallback to role-based lookup
            if (slot.defaultStaffRole() != null) {
                List<StaffMember> members = staffMemberService.findByRoleWithAccountStatus(slot.defaultStaffRole());
                if (!members.isEmpty() && members.get(0).getUser() != null) {
                    defaults.put(slot.slotKey(), members.get(0).getUser().getId());
                }
            }
        }
        return defaults;
    }

    /**
     * Retrieves all document workflow configurations grouped by document.
     */
    public List<DocumentWorkflowGroupDTO> getGroupedConfigs(SignatureModule module) {
        List<Integer> docTypes = registry.signableDocumentTypes(module);
        List<DocumentWorkflowConfig> allConfigs = repository.findAllWithSigner();
        Map<String, DocumentWorkflowConfig> configMap = new HashMap<>();
        for (DocumentWorkflowConfig c : allConfigs) {
            if (c.getModule() == module) {
                configMap.put(c.getDocumentType() + "_" + c.getSlotKey(), c);
            }
        }

        List<DocumentWorkflowGroupDTO> groups = new ArrayList<>();
        for (int docType : docTypes) {
            String title = snapshotProvider.labelFor(module, docType);
            List<SignatureSlot> defaultSlots = registry.slotsFor(module, docType);
            List<DocumentWorkflowSlotDTO> slotDTOs = new ArrayList<>();

            for (SignatureSlot defaultSlot : defaultSlots) {
                String key = docType + "_" + defaultSlot.slotKey();
                DocumentWorkflowConfig cfg = configMap.get(key);

                if (cfg != null) {
                    slotDTOs.add(new DocumentWorkflowSlotDTO(
                            module,
                            docType,
                            cfg.getSlotKey(),
                            cfg.getRoleLabel(),
                            cfg.getAnchorPlaceholder(),
                            cfg.getDefaultStaffRole(),
                            cfg.getStepOrder(),
                            cfg.isEnabled(),
                            cfg.getDefaultSigner() != null ? cfg.getDefaultSigner().getId() : null,
                            cfg.getDefaultSigner() != null ? cfg.getDefaultSigner().getName() : null));
                } else {
                    // Pre-fill with default slot information
                    slotDTOs.add(new DocumentWorkflowSlotDTO(
                            module,
                            docType,
                            defaultSlot.slotKey(),
                            defaultSlot.roleLabel(),
                            defaultSlot.anchorPlaceholder(),
                            defaultSlot.defaultStaffRole(),
                            defaultSlot.order(),
                            true,
                            null,
                            null));
                }
            }

            slotDTOs.sort(Comparator.comparingInt(DocumentWorkflowSlotDTO::stepOrder));
            groups.add(new DocumentWorkflowGroupDTO(module, docType, title, slotDTOs));
        }

        return groups;
    }

    /**
     * Saves or updates a list of workflow slot configurations.
     */
    @Transactional
    public void saveConfigs(List<DocumentWorkflowSlotDTO> updates) {
        if (updates == null || updates.isEmpty()) return;

        for (DocumentWorkflowSlotDTO dto : updates) {
            DocumentWorkflowConfig config = repository
                    .findByModuleAndDocumentTypeAndSlotKey(dto.module(), dto.documentType(), dto.slotKey())
                    .orElseGet(DocumentWorkflowConfig::new);

            config.setModule(dto.module());
            config.setDocumentType(dto.documentType());
            config.setSlotKey(dto.slotKey());
            config.setRoleLabel(dto.roleLabel());
            config.setAnchorPlaceholder(dto.anchorPlaceholder());
            config.setDefaultStaffRole(dto.defaultStaffRole());
            config.setStepOrder(dto.stepOrder());
            config.setEnabled(dto.isEnabled());

            if (dto.defaultSignerUserId() != null) {
                UserDtls signer = userRepository.findById(dto.defaultSignerUserId()).orElse(null);
                config.setDefaultSigner(signer);
            } else {
                config.setDefaultSigner(null);
            }

            repository.save(config);
        }
        log.info("Saved {} document workflow slot configurations", updates.size());
    }

    /**
     * Resets a specific document's workflow configuration back to factory registry defaults.
     */
    @Transactional
    public void resetToDefaults(SignatureModule module, int documentType) {
        repository.deleteByModuleAndDocumentType(module, documentType);
        log.info("Reset workflow configuration for {} doc {}", module, documentType);
    }
}
