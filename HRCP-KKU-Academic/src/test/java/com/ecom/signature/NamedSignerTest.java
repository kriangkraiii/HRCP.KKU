package com.ecom.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.StaffMember;
import com.ecom.academic.repository.StaffMemberRepository;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * เอกสารที่ 3 เฟส 1: ผู้ลงนามแต่ละตำแหน่งคือคนที่ชื่ออยู่ในแบบฟอร์ม — เลือกผู้ลงนามแยกจากชื่อในเอกสารไม่ได้
 */
@DisplayName("เอกสารที่ 3: ผู้ลงนามตามชื่อที่กรอกในแบบฟอร์ม")
class NamedSignerTest extends AbstractFlowTest {

    @Autowired
    private StaffMemberRepository staffMembers;

    private UserDtls officer;
    private UserDtls head;
    private UserDtls otherHead;
    private UserDtls assocDean;
    private UserDtls dean;
    private AcademicRequest request;
    private Map<UserDtls, String> names;

    @BeforeEach
    void staffDirectory() {
        officer = data.admin();
        head = data.user("head@" + TestDataFactory.DOMAIN, "สมศักดิ์", "หัวหน้าสาขา", "ROLE_USER");
        otherHead = data.user("head2@" + TestDataFactory.DOMAIN, "สมหมาย", "หัวหน้าอีกคน", "ROLE_USER");
        assocDean = data.user("assoc@" + TestDataFactory.DOMAIN, "วิชัย", "รองคณบดี", "ROLE_USER");
        dean = data.user("dean@" + TestDataFactory.DOMAIN, "ประสิทธิ์", "คณบดี", "ROLE_USER");
        names = new java.util.HashMap<>();
        names.put(head, staff(head, "HEAD", "รศ.ดร."));
        names.put(otherHead, staff(otherHead, "HEAD", "ผศ."));
        names.put(assocDean, staff(assocDean, "DEAN", "ศ.ดร."));
        names.put(dean, staff(dean, "DEAN", "ศ.ดร."));
        names.put(officer, staff(officer, "HR", null));
        request = data.evaluation(data.applicant(), RequestStatus.RECEIVED);
    }

    @AfterEach
    void removeStaff() {
        staffMembers.deleteAll();
    }

    private String staff(UserDtls user, String role, String title) {
        StaffMember s = new StaffMember();
        s.setFirstName(user.getFirstName());
        s.setLastName(user.getLastName());
        s.setAcademicTitle(title);
        s.setStaffRole(role);
        s.setIsActive(true);
        s.setUser(user);
        return staffMembers.save(s).getDisplayName();
    }

    private String doc3(String headName) {
        return "{\"department_head\":\"" + headName + "\",\"associate_dean_name\":\"" + names.get(assocDean)
                + "\",\"dean_name\":\"" + names.get(dean) + "\",\"hr_staff_name\":\"" + names.get(officer) + "\"}";
    }

    private SignatureWorkflowService.Result send(String json, UserDtls chosenHead) {
        return signatureWorkflow.createEnvelope(SignatureModule.ACADEMIC, request.getId(), 3, "เอกสารที่ 3", json,
                List.of(new SignerAssignment("head", chosenHead.getId()),
                        new SignerAssignment("associate_dean", assocDean.getId()),
                        new SignerAssignment("dean", dean.getId()),
                        new SignerAssignment("hr", officer.getId())),
                null, officer, ActorContext.none());
    }

    @Test
    @DisplayName("เลือกหัวหน้าสาขาเป็นอีกคน แต่ชื่อในเอกสารคือ รศ.ดร.สมศักดิ์ — ผู้ลงนามต้องเป็นคนตามชื่อในเอกสาร")
    void theSignerIsWhoeverTheFormNames() {
        var result = send(doc3("  " + names.get(head).replace(" ", "  ") + " "), otherHead);

        assertThat(result.ok()).as(result.error()).isTrue();
        Map<String, Integer> signers = signatureSteps.findBySignatureRequestIdOrderByStepOrderAsc(result.request().getId())
                .stream().collect(Collectors.toMap(SignatureStep::getSlotKey, s -> s.getSigner().getId()));
        assertThat(signers).containsEntry("head", head.getId()).containsEntry("associate_dean", assocDean.getId())
                .containsEntry("dean", dean.getId()).containsEntry("hr", officer.getId());
    }

    @Test
    @DisplayName("ชื่อในเอกสารไม่ตรงกับบุคลากรคนไหน — ส่งไม่ได้ พร้อมบอกว่าช่องไหน")
    void aNameThatMatchesNobodyIsRefused() {
        var result = send(doc3("อาจารย์ที่ไม่มีในระบบ"), head);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("อาจารย์ที่ไม่มีในระบบ").contains("หัวหน้าสาขาวิชา");
    }

    @Test
    @DisplayName("เอกสารของเจ้าหน้าที่ระหว่างเวียน — ไม่มีปุ่มส่งกลับให้ผู้ยื่น เหลือปุ่มยกเลิก และ POST ส่งกลับถูกปฏิเสธ")
    void aStaffDocumentHasNothingToSendBack() throws Exception {
        var sent = send(doc3(names.get(head)), head);
        assertThat(sent.ok()).as(sent.error()).isTrue();

        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/3")
                        .with(user(officer.getEmail()).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("modal-resign-").doesNotContain("ส่งกลับให้แก้ไขและลงนามใหม่")
                .contains("ยกเลิกการเวียนลงนาม").contains("เอกสารฉบับนี้เป็นของเจ้าหน้าที่");

        String redirect = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/admin/academic/request/" + request.getId() + "/document/3/request-resign")
                        .param("reason", "ทดสอบ")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .with(user(officer.getEmail()).roles("ADMIN")))
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).isEqualTo("/admin/academic/request/" + request.getId() + "/document/3");
        assertThat(signatureWorkflow.findEnvelope(sent.request().getId()).orElseThrow().getStatus())
                .as("ส่งกลับไม่ได้ ก็ต้องไม่ยกเลิกรอบลงนามด้วย")
                .isEqualTo(com.ecom.academic.model.SignatureRequestStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("แผงส่งลงนามของเอกสารที่ 3 — ไม่มีตัวเลือกผู้ลงนาม แสดงผู้ลงนามตามชื่อในเอกสารแทน")
    void thePanelHasNoSignerPicker() throws Exception {
        String html = mvc.perform(get("/admin/academic/request/" + request.getId() + "/document/3")
                        .with(user(officer.getEmail()).roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-named-signer=\"department_head\"").contains("data-named-signer=\"dean_name\"")
                .contains("NAMED_SIGNER_CANDIDATES").contains("/js/named_signer.js")
                .doesNotContain("<select name=\"signerUserIds\"");
    }
}
