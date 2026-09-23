package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecom.academic.dto.DocumentWorkflowGroupDTO;
import com.ecom.academic.dto.DocumentWorkflowSlotDTO;
import com.ecom.academic.model.DocumentWorkflowConfig;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.DocumentWorkflowConfigRepository;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ทดสอบระบบตั้งค่าผู้ลงนามและลำดับขั้นตอน (DocumentWorkflowConfigService)")
class DocumentWorkflowConfigServiceTest {

    @Mock
    private DocumentWorkflowConfigRepository repository;

    @Mock
    private DocumentSnapshotProvider snapshotProvider;

    @Mock
    private StaffMemberService staffMemberService;

    @Mock
    private UserRepository userRepository;

    private final SignatureAnchorRegistry registry = new SignatureAnchorRegistry();

    private DocumentWorkflowConfigService service;

    @BeforeEach
    void setUp() {
        service = new DocumentWorkflowConfigService(repository, registry, snapshotProvider, staffMemberService, userRepository);
    }

    @Test
    @DisplayName("เมื่อไม่มีข้อมูลในฐานข้อมูล ต้องคืนค่าเริ่มต้นจาก SignatureAnchorRegistry (Fallback)")
    void fallbackToRegistryWhenNoDbConfig() {
        when(repository.findByModuleAndDocumentType(SignatureModule.ACADEMIC, 2))
                .thenReturn(List.of());

        List<SignatureSlot> slots = service.effectiveSlotsFor(SignatureModule.ACADEMIC, 2);

        assertThat(slots).hasSize(2);
        assertThat(slots.get(0).slotKey()).isEqualTo("applicant");
        assertThat(slots.get(1).slotKey()).isEqualTo("hr");
    }

    @Test
    @DisplayName("เมื่อมีการตั้งค่าในฐานข้อมูล ต้องใช้ลำดับและสถานะเปิด/ปิดตามการตั้งค่า")
    void usesConfigFromDbWhenPresent() {
        DocumentWorkflowConfig cfg1 = new DocumentWorkflowConfig(
                SignatureModule.ACADEMIC, 1, "applicant", "ผู้ขอรับการประเมิน",
                "applicant_name", null, 2, true, null);

        DocumentWorkflowConfig cfg2 = new DocumentWorkflowConfig(
                SignatureModule.ACADEMIC, 1, "hr", "นักทรัพยากรบุคคล",
                "hr_staff_name", "HR", 1, true, null);

        // Disabling slot 3 for test
        DocumentWorkflowConfig cfg3 = new DocumentWorkflowConfig(
                SignatureModule.ACADEMIC, 1, "disabled_slot", "ตำแหน่งปิดใช้งาน",
                "placeholder", null, 3, false, null);

        when(repository.findByModuleAndDocumentType(SignatureModule.ACADEMIC, 1))
                .thenReturn(List.of(cfg1, cfg2, cfg3));

        List<SignatureSlot> slots = service.effectiveSlotsFor(SignatureModule.ACADEMIC, 1);

        assertThat(slots).hasSize(2);
        // hr was configured as step 1, applicant as step 2
        assertThat(slots.get(0).slotKey()).isEqualTo("hr");
        assertThat(slots.get(0).order()).isEqualTo(1);
        assertThat(slots.get(1).slotKey()).isEqualTo("applicant");
        assertThat(slots.get(1).order()).isEqualTo(2);
    }

    @Test
    @DisplayName("ช่องลงนามที่เทมเพลตเพิ่มทีหลังการตั้งค่า ยังอยู่ครบพร้อมคำถามและช่องวันที่")
    void slotsAddedAfterConfigurationAreKept() {
        // ตั้งค่าไว้ตอนเอกสารที่ 1 เฟส 2 มีแค่ผู้ยื่น ก่อนรวมส่วนของผู้บังคับบัญชาเข้ามา
        DocumentWorkflowConfig applicantOnly = new DocumentWorkflowConfig(
                SignatureModule.POSITION, 1, "applicant", "เจ้าของประวัติ",
                "applicant_name", null, 1, true, null);
        when(repository.findByModuleAndDocumentType(SignatureModule.POSITION, 1))
                .thenReturn(List.of(applicantOnly));

        List<SignatureSlot> slots = service.effectiveSlotsFor(SignatureModule.POSITION, 1);

        assertThat(slots).extracting(SignatureSlot::slotKey).containsExactly("applicant", "head", "dean");
        assertThat(slots.get(1).choice().fieldKey()).isEqualTo("qualification_status");
        assertThat(slots.get(2).choice().options()).containsExactly("เข้าข่าย", "ไม่เข้าข่าย");
        assertThat(slots.get(2).marks().signedDateFieldKey()).isEqualTo("dean_sign_date");
    }

    @Test
    @DisplayName("ช่องที่ตั้งค่าไว้แล้วยังได้ช่องวันที่ลงนามจากทะเบียน ไม่หายเพราะมีแถวตั้งค่า")
    void configuredSlotsKeepTheirMarks() {
        DocumentWorkflowConfig head = new DocumentWorkflowConfig(
                SignatureModule.POSITION, 1, "head", "หัวหน้าสาขาวิชา",
                "department_head_name", "HEAD", 2, true, null);
        when(repository.findByModuleAndDocumentType(SignatureModule.POSITION, 1))
                .thenReturn(List.of(head));

        SignatureSlot configured = service.effectiveSlotsFor(SignatureModule.POSITION, 1).stream()
                .filter(slot -> "head".equals(slot.slotKey()))
                .findFirst()
                .orElseThrow();

        assertThat(configured.marks().signedDateFieldKey()).isEqualTo("head_sign_date");
    }

    @Test
    @DisplayName("บันทึกการตั้งค่าลงฐานข้อมูลสำเร็จ")
    void savesConfigurationSuccessfully() {
        UserDtls signer = new UserDtls();
        signer.setId(99);
        signer.setName("ดร.สมชาย");

        when(userRepository.findById(99)).thenReturn(Optional.of(signer));
        when(repository.findByModuleAndDocumentTypeAndSlotKey(SignatureModule.ACADEMIC, 1, "hr"))
                .thenReturn(Optional.empty());

        DocumentWorkflowSlotDTO update = new DocumentWorkflowSlotDTO(
                SignatureModule.ACADEMIC, 1, "hr", "นักทรัพยากรบุคคล",
                "hr_staff_name", "HR", 1, true, 99, "ดร.สมชาย");

        service.saveConfigs(List.of(update));

        verify(repository).save(any(DocumentWorkflowConfig.class));
    }

    @Test
    @DisplayName("รีเซ็ตค่าเอกสารกลับสู่ค่าเริ่มต้น")
    void resetDocumentToDefaults() {
        service.resetToDefaults(SignatureModule.ACADEMIC, 1);
        verify(repository).deleteByModuleAndDocumentType(SignatureModule.ACADEMIC, 1);
    }
}
