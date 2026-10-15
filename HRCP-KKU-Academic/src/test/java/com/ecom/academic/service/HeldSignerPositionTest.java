package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.ActingSignerRepository;
import com.ecom.academic.repository.SignatureStepRepository;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * แบบ ก.พ.ว. มข. ๐๓ — บรรทัดตำแหน่งใต้ชื่อหัวหน้าสาขาและคณบดีต้องเป็นตำแหน่งจริงของผู้ลงนาม
 * ไม่ใช่ข้อความในแบบฟอร์มเปล่า "..ผู้บังคับบัญชาระดับหัวหน้าภาควิชาหรือเทียบเท่า.."
 */
@DisplayName("ตำแหน่งจริงของผู้ลงนามใต้ชื่อ")
class HeldSignerPositionTest {

    private StaffMemberRepository staffMembers;
    private ActingSignerService actingSigners;
    private SignatureStepRepository stepRepository;
    private SignerNameResolver resolver;
    private SignatureRequest envelope;

    @BeforeEach
    void setUp() {
        staffMembers = mock(StaffMemberRepository.class);
        actingSigners = new ActingSignerService(mock(ActingSignerRepository.class), mock(UserRepository.class),
                staffMembers);

        DocumentWorkflowConfigService workflowConfig = mock(DocumentWorkflowConfigService.class);
        stepRepository = mock(SignatureStepRepository.class);
        when(workflowConfig.effectiveSlotsFor(SignatureModule.POSITION, 1)).thenReturn(List.of());
        when(workflowConfig.defaultSignerUserIds(SignatureModule.POSITION, 1)).thenReturn(Map.of());
        resolver = new SignerNameResolver(workflowConfig, stepRepository, mock(StaffMemberService.class),
                mock(UserRepository.class));
        resolver.setActingSigners(actingSigners);

        envelope = new SignatureRequest();
        envelope.setId(20L);
        envelope.setModule(SignatureModule.POSITION);
        envelope.setDocumentType(1);
    }

    private static UserDtls user(int id, String positionTitle) {
        UserDtls u = new UserDtls();
        u.setId(id);
        u.setPositionTitle(positionTitle);
        return u;
    }

    private static SignatureStep step(String slotKey, String anchor, UserDtls signer) {
        SignatureStep s = new SignatureStep();
        s.setSlotKey(slotKey);
        s.setAnchorPlaceholder(anchor);
        s.setSigner(signer);
        s.setStatus(SignatureStepStatus.SIGNED);
        return s;
    }

    private void programmeOf(int userId, String department) {
        StaffMember staff = new StaffMember();
        staff.setDepartment(department);
        when(staffMembers.findByUserId(userId)).thenReturn(Optional.of(staff));
    }

    @Test
    @DisplayName("หัวหน้าสาขา — ตำแหน่งในบัญชีขึ้นต้นด้วย หัวหน้าสาขาวิชา ใช้ตามบัญชี")
    void headUsesOwnPositionTitle() {
        assertThat(actingSigners.heldPosition("head", user(1, "หัวหน้าสาขาวิชาปัญญาประดิษฐ์")))
                .isEqualTo("หัวหน้าสาขาวิชาปัญญาประดิษฐ์");
    }

    @Test
    @DisplayName("หัวหน้าสาขา — บัญชีไม่มีตำแหน่ง ใช้สาขาจากทะเบียนบุคลากร")
    void headFallsBackToProgramme() {
        programmeOf(2, "สาขาวิชาวิทยาการคอมพิวเตอร์");

        assertThat(actingSigners.heldPosition("head", user(2, "รองศาสตราจารย์")))
                .isEqualTo("หัวหน้าสาขาวิชาวิทยาการคอมพิวเตอร์");
    }

    @Test
    @DisplayName("หัวหน้าสาขา — ไม่รู้สาขา คืน null ให้แบบฟอร์มคงข้อความเดิม")
    void headWithoutProgrammeIsUnknown() {
        when(staffMembers.findByUserId(3)).thenReturn(Optional.empty());

        assertThat(actingSigners.heldPosition("head", user(3, null))).isNull();
    }

    @Test
    @DisplayName("คณบดี — บัญชีไม่มีตำแหน่ง ใช้ตำแหน่งเต็มตั้งต้น")
    void deanFallsBackToFullTitle() {
        assertThat(actingSigners.heldPosition("dean", user(4, null)))
                .isEqualTo("คณบดีวิทยาลัยการคอมพิวเตอร์");
    }

    @Test
    @DisplayName("เอกสาร ก.พ.ว. มข. ๐๓ — เติมตำแหน่งจริงทั้งใต้ชื่อหัวหน้าสาขาและคณบดี")
    void fillsBothPositionLines() {
        programmeOf(5, "วิทยาการคอมพิวเตอร์");
        when(stepRepository.findStepsWithSigner(20L)).thenReturn(List.of(
                step("head", "department_head_name", user(5, null)),
                step("dean", "dean_name", user(6, "คณบดีวิทยาลัยการคอมพิวเตอร์"))));

        String filled = resolver.fillInto(envelope, "{}");

        assertThat(filled)
                .contains("\"head_position_line\":\"หัวหน้าสาขาวิชาวิทยาการคอมพิวเตอร์\"")
                .contains("\"dean_position_line\":\"คณบดีวิทยาลัยการคอมพิวเตอร์\"");
    }

    @Test
    @DisplayName("ลงนามในฐานะรักษาการแทน — ตำแหน่งรักษาการแทนชนะตำแหน่งปกติ")
    void actingPositionWins() {
        SignatureStep dean = step("dean", "dean_name", user(7, "รองคณบดีวิทยาลัยการคอมพิวเตอร์"));
        dean.setActingPosition("รักษาการแทนคณบดีวิทยาลัยการคอมพิวเตอร์");
        when(stepRepository.findStepsWithSigner(20L)).thenReturn(List.of(dean));

        String filled = resolver.fillInto(envelope, "{}");

        assertThat(filled).contains("\"dean_position_line\":\"รักษาการแทนคณบดีวิทยาลัยการคอมพิวเตอร์\"");
    }
}
