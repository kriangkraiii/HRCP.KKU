package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * กรรมการสามคนเลือกจากบัญชีในระบบที่เอกสารที่ 3 แล้วลงนามเอกสารที่ 7 ครบทั้งสามคน (ประธานก่อน)
 * และประธานลงนามเอกสารที่ 8 — ดู docs/PLAN-committee-signers.md
 */
@DisplayName("กรรมการสามคน: เลือกที่เอกสารที่ 3 ลงนามเอกสารที่ 7 และ 8")
class CommitteeSignersTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicService;

    private UserDtls officer;
    private UserDtls chair;
    private UserDtls member2;
    private UserDtls member3;
    private AcademicRequest request;

    @BeforeEach
    void committee() {
        officer = data.admin();
        chair = data.user("chair@" + TestDataFactory.DOMAIN, "ประธาน", "กรรมการ", "ROLE_USER");
        member2 = data.user("member2@" + TestDataFactory.DOMAIN, "สอง", "กรรมการ", "ROLE_USER");
        member3 = data.user("member3@" + TestDataFactory.DOMAIN, "สาม", "กรรมการ", "ROLE_USER");
        request = data.evaluation(data.applicant(), RequestStatus.MEETING_SCHEDULED);
        data.academicDocument(request, 1,
                "{\"title\":\"ผู้ช่วยศาสตราจารย์\",\"applicant_name\":\"สมชาย ทดสอบยื่น\",\"chk2\":\"✓\"}");
    }

    private static String printed(UserDtls u) {
        return SignerNameResolver.printedName(u);
    }

    /** เอกสารที่ 3 ที่เลือกกรรมการด้วยตัวค้นหาชื่อ: ชื่อที่พิมพ์ลงเอกสาร + รหัสบัญชีในช่อง __signer */
    private void doc3Picks(UserDtls first, UserDtls second, UserDtls third) {
        StringBuilder json = new StringBuilder("{");
        UserDtls[] picked = { first, second, third };
        for (int i = 1; i <= 3; i++) {
            if (i > 1) {
                json.append(',');
            }
            json.append("\"committee_").append(i).append("_name\":\"").append(printed(picked[i - 1])).append("\",")
                    .append("\"committee_").append(i).append("_name__signer\":\"").append(picked[i - 1].getId())
                    .append('"');
        }
        data.academicDocument(request, 3, json.append('}').toString());
    }

    private static String json(java.util.Map<String, String> fields) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asOfficer() {
        return user(officer.getEmail()).roles(officer.getRole().replace("ROLE_", ""));
    }

    private void saveDraft(int type, String json) throws Exception {
        mvc.perform(post("/api/draft/academic/" + request.getId() + "/" + type)
                .with(asOfficer()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("เอกสารที่ 3: ช่องกรรมการทั้งสามเป็นตัวค้นหาชื่อ เพิ่มคนนอก มข. ได้เฉพาะคนที่ 2")
    void doc3OffersThePickerForAllThree() throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/3").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);

        for (int i = 1; i <= 3; i++) {
            var input = page.selectFirst("input[name=committee_" + i + "_name]");
            assertThat(input.hasAttr("data-person-picker")).as("คนที่ %d", i).isTrue();
            assertThat(input.attr("data-role")).isEqualTo("COMMITTEE");
            assertThat(input.attr("data-distinct-group")).isEqualTo("committee");
            assertThat(input.hasAttr("data-allow-external")).as("คนที่ %d", i).isEqualTo(i == 2);
            assertThat(input.hasAttr("list")).isFalse();
        }
    }

    @Test
    @DisplayName("เอกสารที่ 7: ชื่อและรหัสบัญชีของกรรมการมาจากเอกสารที่ 3 แบบอ่านอย่างเดียว")
    void doc7CarriesTheChosenAccounts() throws Exception {
        doc3Picks(chair, member2, member3);

        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/7").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Document page = Jsoup.parse(html);
        UserDtls[] picked = { chair, member2, member3 };
        for (int i = 1; i <= 3; i++) {
            var name = page.selectFirst("input[name=committee_" + i + "_name]");
            assertThat(name.val()).isEqualTo(printed(picked[i - 1]));
            assertThat(name.hasAttr("readonly")).isTrue();
            assertThat(name.hasAttr("data-person-picker")).isFalse();
            assertThat(page.selectFirst("input[name=committee_" + i + "_name__signer]").val())
                    .isEqualTo(String.valueOf(picked[i - 1].getId()));
        }
    }

    @Test
    @DisplayName("เอกสารที่ 7: ส่งรหัสบัญชีคนอื่นมาเอง ระบบยังบันทึกบัญชีที่เลือกไว้ในเอกสารที่ 3")
    void aTamperedSignerIsReplacedFromDoc3() throws Exception {
        doc3Picks(chair, member2, member3);

        saveDraft(7, "{\"committee_2_name\":\"" + printed(officer) + "\",\"committee_2_name__signer\":\""
                + officer.getId() + "\",\"sec_score_1\":\"4\"}");

        assertThat(academicService.getLatestDocumentData(request.getId(), 7))
                .containsEntry("committee_2_name", printed(member2))
                .containsEntry("committee_2_name__signer", String.valueOf(member2.getId()))
                .containsEntry("committee_1_name__signer", String.valueOf(chair.getId()))
                .containsEntry("committee_3_name__signer", String.valueOf(member3.getId()));
    }

    @Test
    @DisplayName("เอกสารที่ 4 บันทึกไว้ก่อนเปลี่ยนกรรมการในเอกสารที่ 3 — รหัสบัญชีคนใหม่ไม่ถูกจับคู่กับชื่อคนเดิม")
    void aStaleDoc4NameDoesNotBorrowTheNewAccount() throws Exception {
        data.academicDocument(request, 4, "{\"committee_2_name\":\"กรรมการคนเดิม\"}");
        doc3Picks(chair, member2, member3);

        saveDraft(7, "{\"sec_score_1\":\"4\"}");

        assertThat(academicService.getLatestDocumentData(request.getId(), 7))
                .containsEntry("committee_2_name", "กรรมการคนเดิม")
                .doesNotContainKey("committee_2_name__signer")
                .containsEntry("committee_1_name__signer", String.valueOf(chair.getId()));
    }

    @Test
    @DisplayName("เอกสารที่ 7 ส่งเวียน: กรรมการทั้งสามคนเป็นผู้ลงนาม ประธานก่อน แล้วคนที่ 2 และ 3 ตามลำดับ")
    void allThreeSignChairFirst() throws Exception {
        doc3Picks(chair, member2, member3);
        saveDraft(7, "{\"sec_score_1\":\"4\"}");

        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 7, "เอกสารที่ 7",
                json(academicService.getLatestDocumentData(request.getId(), 7)), List.of(), null, officer,
                ActorContext.none());

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(result.request().getId()))
                .extracting(SignatureStep::getSlotKey, s -> s.getSigner().getId())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("committee_chair", chair.getId()),
                        org.assertj.core.groups.Tuple.tuple("committee_member_2", member2.getId()),
                        org.assertj.core.groups.Tuple.tuple("committee_member_3", member3.getId()));
    }

    @Test
    @DisplayName("บัญชีเดียวถูกเลือกเป็นกรรมการสองตำแหน่ง — ส่งเวียนเอกสารที่ 7 ไม่ได้")
    void oneAccountCannotHoldTwoCommitteeSeats() throws Exception {
        doc3Picks(chair, member2, member2);
        saveDraft(7, "{\"sec_score_1\":\"4\"}");

        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 7, "เอกสารที่ 7",
                json(academicService.getLatestDocumentData(request.getId(), 7)), List.of(), null, officer,
                ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("มากกว่าหนึ่งตำแหน่ง");
    }

    @Test
    @DisplayName("เอกสารที่ 8: ผู้ลงนามคือประธานที่เลือกไว้ในเอกสารที่ 3 ไม่ใช่คนที่เลือกในแผงลงนาม")
    void doc8IsSignedByTheChosenChair() throws Exception {
        doc3Picks(chair, member2, member3);
        saveDraft(8, "{\"committee_president_name\":\"คนอื่น\"}");

        var saved = academicService.getLatestDocumentData(request.getId(), 8);
        assertThat(saved).containsEntry("committee_president_name", printed(chair))
                .containsEntry("committee_president_name__signer", String.valueOf(chair.getId()));

        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 8, "เอกสารที่ 8",
                json(saved),
                List.of(new com.ecom.academic.service.SignatureWorkflowService.SignerAssignment(
                        "committee_chair", member3.getId())),
                null, officer, ActorContext.none());
        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(result.request().getId()))
                .extracting(s -> s.getSigner().getId()).containsExactly(chair.getId());
    }

    /** เอกสารที่ 3 แบบเก่า: พิมพ์ชื่อเอง ไม่มีรหัสบัญชีคู่กัน */
    private void doc3Typed(String first, String second, String third) {
        data.academicDocument(request, 3, "{\"committee_1_name\":\"" + first + "\",\"committee_2_name\":\"" + second
                + "\",\"committee_3_name\":\"" + third + "\"}");
    }

    @Test
    @DisplayName("เอกสารที่ 3: กรรมการที่ไม่มีบัญชีในระบบ — ส่งเวียนคำสั่งแต่งตั้งไม่ได้")
    void doc3WithAnUnknownMemberCannotCirculate() {
        String json = "{\"committee_1_name\":\"" + printed(chair) + "\",\"committee_1_name__signer\":\""
                + chair.getId() + "\",\"committee_2_name\":\"ผู้ทรงคุณวุฒิที่ไม่มีบัญชี\",\"committee_3_name\":\""
                + printed(member3) + "\",\"committee_3_name__signer\":\"" + member3.getId() + "\"}";

        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 3, "เอกสารที่ 3",
                json, List.of(), null, officer, ActorContext.none());

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("กรรมการคนที่ 2").contains("ผู้ทรงคุณวุฒิที่ไม่มีบัญชี")
                .contains("ค้นหาแล้วเลือก");
    }

    @Test
    @DisplayName("คำร้องเก่า: ชื่อที่พิมพ์ไว้ตรงกับบัญชีเดียว — เอกสารที่ 7 ยังส่งเวียนได้ ผู้ลงนามคือบัญชีนั้น")
    void aTypedNameThatMatchesOneAccountStillWorks() throws Exception {
        doc3Typed(printed(chair), printed(member2), printed(member3));
        saveDraft(7, "{\"sec_score_1\":\"4\"}");

        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 7, "เอกสารที่ 7",
                json(academicService.getLatestDocumentData(request.getId(), 7)), List.of(), null, officer,
                ActorContext.none());

        assertThat(result.ok()).as(result.error()).isTrue();
        assertThat(signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(result.request().getId()))
                .extracting(s -> s.getSigner().getId())
                .containsExactly(chair.getId(), member2.getId(), member3.getId());
    }

    @Test
    @DisplayName("คำร้องเก่า: ชื่อที่ไม่มีบัญชี — หน้าเอกสารที่ 7 เตือนตั้งแต่เปิด และการส่งเวียนบอกให้แก้ที่เอกสารที่ 3")
    void aTypedNameWithoutAnAccountPointsBackToDoc3() throws Exception {
        doc3Typed(printed(chair), "รศ.ดร. ไม่มีบัญชี", printed(member3));

        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/7").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var warning = Jsoup.parse(html).selectFirst("[data-carried-signer-problems]");
        assertThat(warning).isNotNull();
        assertThat(warning.text()).contains("กรรมการคนที่ 2").contains("ไม่มีบัญชี");
        assertThat(warning.selectFirst("a[href$='/document/3']")).isNotNull();

        saveDraft(7, "{\"sec_score_1\":\"4\"}");
        var result = signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 7, "เอกสารที่ 7",
                json(academicService.getLatestDocumentData(request.getId(), 7)), List.of(), null, officer,
                ActorContext.none());
        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("ไม่มีบัญชี").contains("แก้รายชื่อที่เอกสารที่ 3");
    }

    @Test
    @DisplayName("กรรมการผูกบัญชีครบ — หน้าเอกสารที่ 7 ไม่มีคำเตือน")
    void noWarningWhenEveryMemberHasAnAccount() throws Exception {
        doc3Picks(chair, member2, member3);

        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/7").with(asOfficer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(Jsoup.parse(html).selectFirst("[data-carried-signer-problems]")).isNull();
    }

    @Test
    @DisplayName("เอกสารที่ 3: กดบันทึกโดยเลือกบัญชีเดียวกันเป็นกรรมการสองคน — ไม่บันทึก และบอกว่าคนไหนซ้ำ")
    void doc3SaveRefusesTheSamePersonTwice() throws Exception {
        String flash = mvc.perform(post("/admin/academic/request/" + request.getId() + "/document/3")
                .with(asOfficer()).with(csrf())
                .param("action", "draft")
                .param("committee_1_name", printed(chair)).param("committee_1_name__signer", String.valueOf(chair.getId()))
                .param("committee_2_name", printed(member2)).param("committee_2_name__signer", String.valueOf(member2.getId()))
                .param("committee_3_name", printed(chair)).param("committee_3_name__signer", String.valueOf(chair.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/admin/academic/request/" + request.getId() + "/document/3?error=committee_duplicate"))
                .andReturn().getFlashMap().get("errorMsg").toString();

        assertThat(flash).contains("กรรมการคนที่ 3").contains("กรรมการคนที่ 1");
        assertThat(academicService.getDocumentsByType(request.getId(), 3)).isEmpty();
    }

    @Test
    @DisplayName("เอกสารที่ 3: บันทึกร่างอัตโนมัติที่เลือกคนซ้ำ — ตอบ 422 พร้อมเหตุผล ไม่บันทึก")
    void doc3AutoDraftRefusesTheSamePersonTwice() throws Exception {
        String body = mvc.perform(post("/api/draft/academic/" + request.getId() + "/3")
                .with(asOfficer()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"committee_1_name\":\"" + printed(member2) + "\",\"committee_1_name__signer\":\""
                        + member2.getId() + "\",\"committee_2_name\":\"" + printed(member2)
                        + "\",\"committee_2_name__signer\":\"" + member2.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).contains("กรรมการคนที่ 2").contains("คนละคน");
        assertThat(academicService.getDocumentsByType(request.getId(), 3)).isEmpty();
    }

    @Test
    @DisplayName("ตรวจซ้ำทั้งรหัสบัญชีและชื่อ — ช่องเก่าที่พิมพ์ชื่อเองซ้ำกับช่องที่เลือกจากรายชื่อก็นับว่าซ้ำ")
    void duplicatesAreCaughtByIdOrByName() {
        String chairName = printed(chair);
        assertThat(com.ecom.academic.service.NamedAccountResolver.duplicateCommitteeSeat(java.util.Map.of(
                "committee_1_name", chairName, "committee_1_name__signer", String.valueOf(chair.getId()),
                "committee_2_name", chairName))).contains("กรรมการคนที่ 2");
        assertThat(com.ecom.academic.service.NamedAccountResolver.duplicateCommitteeSeat(java.util.Map.of(
                "committee_1_name", chairName, "committee_1_name__signer", String.valueOf(chair.getId()),
                "committee_2_name", printed(member2), "committee_2_name__signer", String.valueOf(member2.getId()),
                "committee_3_name", ""))).isNull();
    }
}
