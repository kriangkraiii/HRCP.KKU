package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.ActingSignerService;
import com.ecom.academic.service.DocumentWorkflowConfigService;
import com.ecom.academic.service.SignatureAnchorRegistry;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ผู้รักษาการแทน — ตั้งครั้งเดียวต่อตำแหน่ง มีผลกับซองที่ส่งหลังจากนั้น และพิมพ์ "รักษาการแทน" + ตำแหน่งเต็ม
 *
 * <p>ใช้เอกสารที่ 3 เฟส 2 (แบบรับรองจริยธรรม): ผู้ยื่นลงนามตอนที่ 1 คณบดีลงนามตอนที่ 2
 * ช่องตำแหน่งใต้ชื่อคณบดีคือ {@code dean_position}
 */
@DisplayName("ผู้รักษาการแทน — ผู้ลงนามเริ่มต้น ตำแหน่งที่พิมพ์ และซองที่ส่งไปแล้ว")
class ActingSignerFlowTest extends AbstractFlowTest {

    private static final int DOC_ETHICS = 3;
    private static final String DEAN_POSITION = "คณบดีวิทยาลัยการคอมพิวเตอร์";
    private static final String ACTING_POSITION = "รักษาการแทนคณบดีวิทยาลัยการคอมพิวเตอร์";
    /** ตัวค้นหาชื่อเติมตำแหน่งปกติของคนที่เลือกไว้ — ต้องถูกทับด้วยฐานะที่ลงนามจริง */
    private static final String FROZEN = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดี\","
            + "\"certification_date\":\"1 ตุลาคม 2569\",\"dean_position\":\"รองคณบดีฝ่ายวิชาการ\"}";

    @Autowired
    private ActingSignerService actingSigners;

    @Autowired
    private DocumentWorkflowConfigService workflowConfig;

    @Autowired
    private SignerNameResolver nameResolver;

    private final ObjectMapper mapper = new ObjectMapper();

    private UserDtls applicant;
    private UserDtls admin;
    private UserDtls associateDean;
    private PositionRequest request;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        admin = data.admin();
        associateDean = data.user("assoc-dean@example.invalid", "รองคณบดี", "ทดสอบ", "ROLE_ADMIN");
        request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(request, DOC_ETHICS, FROZEN);
    }

    private void actingDean(boolean active) {
        actingSigners.save("dean", active, associateDean.getId(), DEAN_POSITION, admin);
    }

    private SignatureRequest sendToDean(UserDtls dean) {
        var result = signatureWorkflow.createEnvelope(SignatureModule.POSITION, request.getId(), DOC_ETHICS,
                "แบบรับรองจริยธรรม", FROZEN,
                List.of(new SignerAssignment("applicant", applicant.getId()),
                        new SignerAssignment("dean", dean.getId())),
                null, admin, ActorContext.none());
        assertThat(result.error()).isNull();
        return result.request();
    }

    private SignatureStep deanStep(SignatureRequest envelope) {
        return signatureSteps.findStepsWithSigner(envelope.getId()).stream()
                .filter(s -> "dean".equals(s.getSlotKey()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> rendered(SignatureRequest envelope) throws Exception {
        return mapper.readValue(nameResolver.fillInto(envelope, envelope.getFrozenJson()),
                new TypeReference<Map<String, Object>>() {});
    }

    @Test
    @DisplayName("เปิดแล้ว — ผู้ลงนามเริ่มต้นของช่องคณบดีในทุกเอกสารเป็นผู้รักษาการแทน")
    void actingSignerBecomesTheDefault() {
        actingDean(true);

        assertThat(workflowConfig.defaultSignerUserIds(SignatureModule.POSITION, DOC_ETHICS))
                .containsEntry("dean", associateDean.getId());
        assertThat(workflowConfig.defaultSignerUserIds(SignatureModule.ACADEMIC, 9))
                .containsEntry("dean", associateDean.getId());
    }

    @Test
    @DisplayName("ซองที่ส่งให้ผู้รักษาการแทน — พิมพ์ \"รักษาการแทน\" + ตำแหน่งเต็ม ทับตำแหน่งปกติที่ฟอร์มเติมไว้")
    void actingStepPrintsTheFullActingPosition() throws Exception {
        actingDean(true);

        SignatureRequest envelope = sendToDean(associateDean);

        assertThat(deanStep(envelope).getActingPosition()).isEqualTo(ACTING_POSITION);
        assertThat(rendered(envelope))
                .containsEntry("dean_position", ACTING_POSITION)
                .containsEntry("dean_name", SignerNameResolver.printedName(associateDean));
    }

    @Test
    @DisplayName("ปิดทีหลัง — ซองที่ส่งไปแล้วยังพิมพ์ฐานะที่ลงนาม")
    void turningItOffDoesNotRewriteSentEnvelopes() throws Exception {
        actingDean(true);
        SignatureRequest envelope = sendToDean(associateDean);

        actingDean(false);

        assertThat(rendered(envelope)).containsEntry("dean_position", ACTING_POSITION);
        assertThat(actingSigners.activeFor("dean")).isEmpty();
    }

    @Test
    @DisplayName("ซองที่ส่งก่อนเปิด — ไม่ถูกย้อนแก้")
    void envelopesSentBeforeAreUntouched() throws Exception {
        SignatureRequest envelope = sendToDean(associateDean);

        actingDean(true);

        assertThat(deanStep(envelope).getActingPosition()).isNull();
        assertThat(rendered(envelope)).containsEntry("dean_position", "รองคณบดีฝ่ายวิชาการ");
    }

    @Test
    @DisplayName("ส่งให้คนอื่นที่ไม่ใช่ผู้รักษาการแทน — ไม่ได้ลงนามในฐานะรักษาการแทน")
    void someoneElseIsNotActing() {
        actingDean(true);
        UserDtls realDean = data.user("real-dean@example.invalid", "คณบดี", "ตัวจริง", "ROLE_ADMIN");

        SignatureRequest envelope = sendToDean(realDean);

        assertThat(deanStep(envelope).getActingPosition()).isNull();
    }

    @Test
    @DisplayName("ตำแหน่งที่พิมพ์ \"รักษาการแทน\" มาเอง ไม่ซ้ำคำ — และเปิดใช้ต้องเลือกผู้รักษาการแทน")
    void positionIsStoredWithoutTheTypedPrefix() {
        actingSigners.save("dean", true, associateDean.getId(), "  รักษาการแทน  คณบดีวิทยาลัยการคอมพิวเตอร์ ", admin);

        assertThat(actingSigners.activeFor("dean")).get()
                .extracting(ActingSignerService.Acting::printedPosition)
                .isEqualTo(ACTING_POSITION);
        assertThatThrownBy(() -> actingSigners.save("dean", true, null, DEAN_POSITION, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("กรุณาเลือกผู้รักษาการแทน");
        assertThatThrownBy(() -> actingSigners.save("applicant", true, associateDean.getId(), DEAN_POSITION, admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ไม่ได้กรอกตำแหน่ง — ระบบเขียนตำแหน่งเต็มให้ ไม่ต้องพิมพ์เอง")
    void blankPositionIsWrittenInFull() {
        actingSigners.save("dean", true, associateDean.getId(), "", admin);

        assertThat(actingSigners.activeFor("dean")).get()
                .extracting(ActingSignerService.Acting::printedPosition)
                .isEqualTo(ACTING_POSITION);
        assertThat(actingSigners.rows()).filteredOn(r -> "associate_dean".equals(r.role().slotKey()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.positionTitle()).isEqualTo("รองคณบดีวิทยาลัยการคอมพิวเตอร์");
                    assertThat(r.suggested()).isTrue();
                });
    }

    @Test
    @DisplayName("หน้าตั้งค่า — ตำแหน่งเต็มเขียนไว้ให้แล้ว เปิดใช้โดยไม่ต้องพิมพ์ตำแหน่งเอง")
    void settingsPageFillsThePositionIn() throws Exception {
        var asAdmin = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user(admin.getEmail()).roles("ADMIN");

        String page = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/admin/academic/settings/signers").with(asAdmin))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("ผู้รักษาการแทน")
                .contains("value=\"" + DEAN_POSITION + "\"");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/admin/academic/settings/signers/acting").with(asAdmin)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("slotKey", "dean")
                        .param("active", "true")
                        .param("actingUserId", String.valueOf(associateDean.getId()))
                        .param("positionTitle", ""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());

        assertThat(actingSigners.activeFor("dean")).get()
                .extracting(ActingSignerService.Acting::printedPosition)
                .isEqualTo(ACTING_POSITION);
    }

    @Test
    @DisplayName("หัวหน้าสาขาวิชา — เขียนชื่อสาขาเต็มจากสังกัดของผู้รักษาการแทน")
    void headPositionComesFromTheActingSignersProgramme() {
        assertThat(ActingSignerService.headOf("สาขาวิชาวิทยาการคอมพิวเตอร์"))
                .isEqualTo("หัวหน้าสาขาวิชาวิทยาการคอมพิวเตอร์");
        assertThat(ActingSignerService.headOf("ปัญญาประดิษฐ์"))
                .isEqualTo("หัวหน้าสาขาวิชาปัญญาประดิษฐ์");
        assertThat(ActingSignerService.headOf("ห้องปฏิบัติการระบบอัจฉริยะ")).isNull();
    }

    @Test
    @DisplayName("แบบ ก.พ.ว. มข. ๐๓ — บรรทัดตำแหน่งใต้ชื่อคณบดีมีช่องให้พิมพ์")
    void kpw03HasADeanPositionLine() {
        assertThat(SignatureAnchorRegistry.printedPositionFieldFor(SignatureModule.POSITION, 1, "dean_name"))
                .isEqualTo("dean_position_line");
        assertThat(SignatureAnchorRegistry.printedPositionFieldFor(SignatureModule.POSITION, 7, "dean_name"))
                .isEqualTo("dean_position");
    }

    @Test
    @DisplayName("ตัวค้นหาชื่อในช่องคณบดี — ผู้รักษาการแทนขึ้นก่อน พร้อมตำแหน่งรักษาการแทน")
    void pickerSuggestsTheActingSignerFirst() throws Exception {
        actingDean(true);

        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/people")
                        .param("role", "DEAN")
                        .param("module", "POSITION")
                        .param("documentType", "7")
                        .param("field", "dean_name")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(admin.getEmail()).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<Map<String, Object>> people = mapper.readValue(body, new TypeReference<>() {});

        assertThat(people).isNotEmpty();
        assertThat(people.get(0))
                .containsEntry("userId", associateDean.getId())
                .containsEntry("position", ACTING_POSITION)
                .containsEntry("acting", true);
    }
}
