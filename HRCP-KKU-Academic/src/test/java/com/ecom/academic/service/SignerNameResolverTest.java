package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.service.SignatureAnchorRegistry.SignatureSlot;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * ชื่อผู้ลงนามบนเอกสารที่ยังเซ็นไม่ครบ
 *
 * <p>เดิมช่องของคนที่ยังไม่ได้เซ็นขึ้นเป็นวงเล็บว่าง "()" ทำให้ผู้ลงนามคนแรก
 * เห็นเอกสารโล่งๆ โดยไม่รู้ว่าจะถูกส่งต่อให้ใคร
 */
@DisplayName("ชื่อผู้ลงนามที่พิมพ์ลงเอกสาร")
class SignerNameResolverTest {

    private static final String DOC1_JSON =
            "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดีวิชาการ\"}";

    private DocumentWorkflowConfigService workflowConfigService;
    private SignatureStepRepository stepRepository;
    private StaffMemberService staffMemberService;
    private UserRepository userRepository;
    private SignerNameResolver resolver;

    private SignatureRequest envelope;

    @BeforeEach
    void setUp() {
        workflowConfigService = mock(DocumentWorkflowConfigService.class);
        stepRepository = mock(SignatureStepRepository.class);
        staffMemberService = mock(StaffMemberService.class);
        userRepository = mock(UserRepository.class);
        resolver = new SignerNameResolver(workflowConfigService, stepRepository, staffMemberService, userRepository);

        envelope = new SignatureRequest();
        envelope.setId(10L);
        envelope.setModule(SignatureModule.ACADEMIC);
        envelope.setDocumentType(1);
        envelope.setFrozenJson(DOC1_JSON);

        // เอกสารที่ 1: ผู้ยื่น แล้วจึงนักทรัพยากรบุคคล
        when(workflowConfigService.effectiveSlotsFor(SignatureModule.ACADEMIC, 1)).thenReturn(List.of(
                new SignatureSlot("applicant", "ผู้ขอรับการประเมิน", "applicant_name", null, 1),
                new SignatureSlot("hr", "นักทรัพยากรบุคคล", "hr_staff_name", "HR", 2)));
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 1))
                .thenReturn(Map.of("hr", 42));
        when(userRepository.findById(42)).thenReturn(Optional.of(user("นางสาว", "สมหญิง", "รักงาน")));
        when(stepRepository.findStepsWithSigner(10L)).thenReturn(List.of());
        when(staffMemberService.findByRoleWithAccountStatus(anyString())).thenReturn(List.of());
    }

    private UserDtls user(String title, String first, String last) {
        UserDtls u = new UserDtls();
        u.setTitle(title);
        u.setFirstName(first);
        u.setLastName(last);
        return u;
    }

    private SignatureStep step(String slotKey, String anchor, String nameSnapshot, SignatureStepStatus status) {
        SignatureStep s = new SignatureStep();
        s.setSlotKey(slotKey);
        s.setAnchorPlaceholder(anchor);
        s.setSignerNameSnapshot(nameSnapshot);
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("ช่องที่ยังไม่มีใครถูกมอบหมาย ใช้ผู้ลงนามเริ่มต้นจากการตั้งค่าลงนาม")
    void unassignedSlot_takesNameFromWorkflowConfig() {
        String filled = resolver.fillInto(envelope, DOC1_JSON);

        assertThat(filled).contains("\"hr_staff_name\":\"นางสาวสมหญิง รักงาน\"");
    }

    @Test
    @DisplayName("ไม่ทับชื่อที่กรอกมาแล้วในแบบฟอร์ม")
    void existingValues_areNeverOverwritten() {
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 1))
                .thenReturn(Map.of("applicant", 42, "hr", 42));

        String filled = resolver.fillInto(envelope, DOC1_JSON);

        assertThat(filled).contains("\"applicant_name\":\"สมชาย ใจดีวิชาการ\"");
        assertThat(filled).doesNotContain("\"applicant_name\":\"นางสาวสมหญิง รักงาน\"");
    }

    @Test
    @DisplayName("ถ้ามอบหมายผู้ลงนามจริงแล้ว ชื่อจากขั้นตอนลงนามชนะค่าเริ่มต้น")
    void assignedStep_winsOverConfiguredDefault() {
        SignatureStep hrStep = step("hr", "hr_staff_name", "ประเสริฐ ตั้งใจ", SignatureStepStatus.WAITING);
        hrStep.setSigner(user("นาย", "ประเสริฐ", "ตั้งใจ"));
        when(stepRepository.findStepsWithSigner(10L)).thenReturn(List.of(hrStep));

        String filled = resolver.fillInto(envelope, DOC1_JSON);

        assertThat(filled).contains("\"hr_staff_name\":\"นายประเสริฐ ตั้งใจ\"");
    }

    @Test
    @DisplayName("ขั้นตอนที่ถูกข้าม ไม่เอาชื่อมาพิมพ์")
    void skippedStep_isIgnored() {
        SignatureStep skipped = step("hr", "hr_staff_name", "ประเสริฐ ตั้งใจ", SignatureStepStatus.SKIPPED);
        when(stepRepository.findStepsWithSigner(10L)).thenReturn(List.of(skipped));

        String filled = resolver.fillInto(envelope, DOC1_JSON);

        assertThat(filled).contains("\"hr_staff_name\":\"นางสาวสมหญิง รักงาน\"");
    }

    @Test
    @DisplayName("ไม่มีผู้ลงนามเริ่มต้นและไม่มีขั้นตอน: ปล่อยเอกสารไว้อย่างเดิม")
    void nothingToFill_returnsOriginalJson() {
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 1)).thenReturn(Map.of());

        assertThat(resolver.fillInto(envelope, DOC1_JSON)).isEqualTo(DOC1_JSON);
    }

    @Test
    @DisplayName("JSON เสีย ต้องคืนของเดิม ไม่ทำให้เอกสารเปิดไม่ขึ้น")
    void brokenJson_isReturnedUnchanged() {
        String broken = "{not json";

        assertThat(resolver.fillInto(envelope, broken)).isEqualTo(broken);
    }

    @Test
    @DisplayName("ชื่อที่พิมพ์ลงเอกสารมีคำนำหน้าเสมอเมื่อบัญชีมีข้อมูล")
    void printedName_includesTitle() {
        assertThat(SignerNameResolver.printedName(user("ผศ.ดร.", "สมชาย", "ใจดี"))).isEqualTo("ผศ.ดร.สมชาย ใจดี");
        assertThat(SignerNameResolver.printedName(user(null, "สมชาย", "ใจดี"))).isEqualTo("สมชาย ใจดี");
        assertThat(SignerNameResolver.printedName(null)).isEmpty();
    }

    @Test
    @DisplayName("ไม่มีบัญชีผู้ใช้ ก็ยังใช้ชื่อจากทะเบียนเจ้าหน้าที่ได้")
    void staffDirectory_isUsedWhenNobodyHasAnAccount() {
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 1)).thenReturn(Map.of());
        com.ecom.academic.model.StaffMember staff = new com.ecom.academic.model.StaffMember();
        staff.setAcademicTitle("นางสาว");
        staff.setFirstName("บุปผา");
        staff.setLastName("ธุรการ");
        when(staffMemberService.findByRoleWithAccountStatus("HR")).thenReturn(List.of(staff));

        String filled = resolver.fillInto(envelope, DOC1_JSON);

        assertThat(filled).contains("hr_staff_name");
        assertThat(filled).contains("บุปผา");
    }

    @Test
    @DisplayName("ไม่แตะ frozenJson ที่เก็บไว้ในซอง — แฮชตรวจลายเซ็นต้องไม่เปลี่ยน")
    void frozenSnapshot_isNotModified() {
        resolver.fillInto(envelope, DOC1_JSON);

        assertThat(envelope.getFrozenJson()).isEqualTo(DOC1_JSON);
    }

    @Test
    @DisplayName("เอกสารคนละประเภทใช้การตั้งค่าของตัวเอง")
    void otherDocumentTypes_useTheirOwnConfiguration() {
        envelope.setDocumentType(3);
        when(workflowConfigService.effectiveSlotsFor(SignatureModule.ACADEMIC, 3)).thenReturn(List.of(
                new SignatureSlot("dean", "คณบดี", "dean_name", "DEAN", 1)));
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 3)).thenReturn(Map.of("dean", 7));
        when(userRepository.findById(7)).thenReturn(Optional.of(user("รศ.ดร.", "วิชัย", "บริหาร")));

        String filled = resolver.fillInto(envelope, "{}");

        assertThat(filled).contains("\"dean_name\":\"รศ.ดร.วิชัย บริหาร\"");
    }

    @Test
    @DisplayName("เอกสารที่ไม่มีจุดลงนาม ไม่ต้องเติมอะไร")
    void unsignableDocument_isLeftAlone() {
        envelope.setDocumentType(5);
        when(workflowConfigService.effectiveSlotsFor(SignatureModule.ACADEMIC, 5)).thenReturn(List.of());
        when(workflowConfigService.defaultSignerUserIds(SignatureModule.ACADEMIC, 5)).thenReturn(Map.of());
        when(stepRepository.findStepsWithSigner(anyLong())).thenReturn(List.of());

        assertThat(resolver.fillInto(envelope, "{}")).isEqualTo("{}");
    }
}
