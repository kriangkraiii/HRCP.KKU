package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.dto.DocumentWorkflowSlotDTO;
import com.ecom.academic.model.SignatureModule;

/**
 * What an administrator configures under {@code /admin/settings/signers} is what
 * the workflow actually does.
 *
 * <p>The service used to answer some of these questions from the built-in
 * layout instead of the saved configuration. Nothing failed visibly when it
 * did — the document simply went to the wrong people, or the applicant was
 * told to sign somewhere the form no longer offered, with no error to explain
 * why they could not submit.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:signersettingsdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "server.ssl.enabled=false"
})
@DisplayName("ค่าที่ตั้งในหน้าผู้ลงนามและลำดับขั้นตอนต้องถูกใช้จริง")
class SignerSettingsAreRespectedTest {

    private static final SignatureModule MODULE = SignatureModule.ACADEMIC;
    private static final int DOC_TYPE = 1; // ค่าเริ่มต้นมีช่อง applicant + hr

    @Autowired
    private SignatureWorkflowService workflow;

    @Autowired
    private DocumentWorkflowConfigService configService;

    @Autowired
    private SignatureAnchorRegistry registry;

    @Autowired
    private com.ecom.academic.repository.DocumentWorkflowConfigRepository configRepository;

    @BeforeEach
    void startFromNoConfiguration() {
        // การตั้งค่าอยู่ในตารางที่ใช้ร่วมกันทั้งคลาส แต่ละเทสจึงต้องเริ่มจากศูนย์
        configRepository.deleteAll();
    }

    private DocumentWorkflowSlotDTO slot(String key, String label, String anchor, int order, boolean enabled) {
        return new DocumentWorkflowSlotDTO(MODULE, DOC_TYPE, key, label, anchor, null, order, enabled, null, null);
    }

    @Test
    @DisplayName("ยังไม่ตั้งค่า ใช้ผังเริ่มต้นของเอกสาร")
    void withoutConfigurationTheBuiltInLayoutIsUsed() {
        assertThat(registry.slotsFor(MODULE, DOC_TYPE))
                .extracting(s -> s.slotKey())
                .contains("applicant");

        assertThat(workflow.getUnsignedApplicantDocTypes(MODULE, 987654L, List.of(DOC_TYPE)))
                .as("เอกสารนี้มีช่องผู้ยื่นตามผังเริ่มต้น จึงต้องถูกทวงลายเซ็น")
                .containsExactly(DOC_TYPE);
    }

    @Test
    @DisplayName("ปิดช่องผู้ยื่นในหน้าตั้งค่า ต้องไม่ทวงลายเซ็นผู้ยื่นอีก")
    void disablingTheApplicantSlotStopsDemandingThatSignature() {
        configService.saveConfigs(List.of(
                slot("applicant", "ผู้ขอรับการประเมิน", "applicant_name", 1, false),
                slot("hr", "นักทรัพยากรบุคคล", "hr_staff_name", 2, true)));

        // ก่อนแก้: ยังอ่านผังเริ่มต้น จึงบังคับให้เซ็นในช่องที่ไม่มีอยู่แล้ว
        // ผู้ยื่นจะกดส่งคำร้องไม่ได้เลยและไม่มีทางแก้
        assertThat(workflow.getUnsignedApplicantDocTypes(MODULE, 987655L, List.of(DOC_TYPE)))
                .as("ช่องผู้ยื่นถูกปิดไปแล้ว จึงไม่ควรมีอะไรค้าง")
                .isEmpty();

        assertThat(workflow.areApplicantSignaturesComplete(MODULE, 987655L, List.of(DOC_TYPE))).isTrue();
    }
}
