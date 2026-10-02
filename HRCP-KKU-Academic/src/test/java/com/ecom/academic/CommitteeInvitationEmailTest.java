package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicAttachment;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.CommitteeDocumentMailer;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignerNameResolver;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.RecordingMailSender;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * หนังสือเชิญเป็นกรรมการ (เอกสารที่ 5) ส่งถึงกรรมการแต่ละท่านทางอีเมลเมื่อลงนามครบ
 * พร้อมทางเปิดดูเอกสารของผู้ยื่นในระบบ ซึ่งเปิดได้เฉพาะกรรมการที่ได้รับแต่งตั้งในคำร้องนั้น
 */
@DisplayName("เอกสารที่ 5: ส่งหนังสือเชิญพร้อมเอกสารของผู้ยื่นถึงกรรมการ")
class CommitteeInvitationEmailTest extends AbstractFlowTest {

    private static final String SUBJECT = "ขอเชิญเป็นกรรมการผู้ทรงคุณวุฒิ";
    private static final String LINK_URL = "https://drive.example.invalid/teaching-plan";
    /** อีเมลออกหลังสร้างหนังสือที่ลงนามแล้ว (ผ่าน LibreOffice) จึงช้ากว่าการแจ้งเตือนทั่วไป */
    private static final long RENDER_TIMEOUT_MS = 60_000;

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private CommitteeDocumentMailer committeeMailer;

    private UserDtls applicant;
    private UserDtls officer;
    private UserDtls dean;
    private UserDtls[] committee;
    private AcademicRequest request;

    @BeforeEach
    void appointCommittee() {
        applicant = data.applicant();
        officer = data.admin();
        dean = data.user("dean@example.invalid", "คณบดี", "วิทยาลัยฯ", "ROLE_ADMIN");
        committee = data.committee();
        request = data.evaluation(applicant, RequestStatus.SUB_COMMITTEE_APPOINTED);
        data.academicDocument(request, 3, data.appointmentOrder());
        data.academicDocument(request, 4, data.appointmentOrder());
    }

    private void attach(String name, String path, String type) {
        AcademicAttachment a = new AcademicAttachment();
        a.setRequest(request);
        a.setOriginalFilename(name);
        a.setStoredFilePath(path);
        a.setFileType(type);
        a.setFileSize(1024L);
        a.setChecklistItem(1);
        academicService.saveAttachment(a);
    }

    private String doc5(String... names) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("memo_no", "อว 660301.26.4/ว.1");
        for (int i = 0; i < names.length; i++) {
            fields.put("committee_name_" + (i + 1), names[i]);
        }
        return new ObjectMapper().writeValueAsString(fields);
    }

    private SignatureRequest signDoc5(String json) {
        data.academicDocument(request, 5, json);
        SignatureRequest envelope = circulate(SignatureModule.ACADEMIC, request.getId(), 5, json, officer,
                List.of(new SignerAssignment("dean", dean.getId())));
        signEveryStep(envelope, officer);
        return envelope;
    }

    private String[] committeeNames() {
        String[] names = new String[committee.length];
        for (int i = 0; i < committee.length; i++) {
            names[i] = SignerNameResolver.printedName(committee[i]);
        }
        return names;
    }

    private List<RecordingMailSender.Sent> invitationsTo(UserDtls member) {
        return mail().to(member.getEmail()).stream().filter(m -> m.subjectContains(SUBJECT)).toList();
    }

    @Nested
    @DisplayName("อีเมลถึงกรรมการ")
    class Email {

        @Test
        @DisplayName("ลงนามครบ — กรรมการแต่ละท่านได้อีเมลแนบหนังสือฉบับของตัวเอง พร้อมลิงก์ไปดูเอกสารของผู้ยื่น")
        void eachMemberGetsTheirOwnLetterAndTheWayToTheFiles() throws Exception {
            attach("แผนการสอน.pdf", "academic/" + request.getId() + "/attachments/plan.pdf", "PDF");
            attach("สื่อการสอน", LINK_URL, "LINK");

            signDoc5(doc5(committeeNames()));

            awaitCondition("อีเมลหนังสือเชิญถึงกรรมการครบสามท่าน", RENDER_TIMEOUT_MS,
                    () -> java.util.Arrays.stream(committee).allMatch(m -> !invitationsTo(m).isEmpty()));

            for (int i = 0; i < committee.length; i++) {
                List<RecordingMailSender.Sent> sent = invitationsTo(committee[i]);
                assertThat(sent).as("กรรมการคนที่ %d ได้อีเมลฉบับเดียว", i + 1).hasSize(1);
                RecordingMailSender.Sent mail = sent.get(0);
                assertThat(mail.to()).as("ส่งแยกท่านละฉบับ ไม่ CC กัน").hasSize(1);
                assertThat(mail.body())
                        .contains(committeeNames()[i])
                        .contains(CommitteeDocumentMailer.filesPath(request.getId()))
                        .contains("แผนการสอน.pdf")
                        .contains(LINK_URL);
                assertThat(mail.attachments())
                        .as("แนบหนังสือฉบับที่ส่งถึงกรรมการท่านนี้")
                        .singleElement().asString().contains("ฉบับที่_" + (i + 1));
            }

            assertThat(academicService.getEditHistory(request.getId()))
                    .as("มีบันทึกว่าส่งอีเมลถึงกรรมการแล้ว")
                    .anyMatch(e -> e.getAction() == com.ecom.academic.model.AcademicDocumentEditLog.EditAction.COMMITTEE_EMAILED);
        }

        @Test
        @DisplayName("คำร้องเก่าที่ผู้ยื่นไม่ได้แนบเอกสาร — ยังส่งหนังสือเชิญ และบอกกรรมการให้ติดต่อเจ้าหน้าที่")
        void stillSendsWhenTheApplicantAttachedNothing() throws Exception {
            signDoc5(doc5(committeeNames()));

            awaitCondition("อีเมลหนังสือเชิญถึงกรรมการคนที่ 1", RENDER_TIMEOUT_MS, () -> !invitationsTo(committee[0]).isEmpty());
            assertThat(invitationsTo(committee[0]).get(0).body()).contains("ผู้ยื่นไม่ได้แนบเอกสารประกอบไว้ในระบบ");
        }

        @Test
        @DisplayName("ชื่อในหนังสือผูกกับบัญชีไม่ได้ — ไม่ส่งถึงใครเลย และบอกเหตุผล")
        void sendsToNobodyWhenAMemberCannotBeFound() throws Exception {
            String[] names = committeeNames();
            names[2] = "ผู้ที่ไม่มี บัญชีในระบบ";
            SignatureRequest envelope = signDoc5(doc5(names));
            settle();
            mail().clear();

            CommitteeDocumentMailer.Outcome outcome = committeeMailer.send(envelope.getId(), officer);

            assertThat(outcome.sent()).isFalse();
            assertThat(outcome.message()).contains("ฉบับที่ 3").contains("ไม่พบบัญชีชื่อนี้ในระบบ");
            assertThat(java.util.Arrays.stream(committee).flatMap(m -> invitationsTo(m).stream())).isEmpty();
        }
    }

    @Nested
    @DisplayName("หน้าเอกสารของผู้ยื่นสำหรับกรรมการ")
    class FilesPage {

        @Test
        @DisplayName("กรรมการที่ได้รับแต่งตั้งเปิดดูรายการเอกสารและเปิดลิงก์ได้")
        void appointedMembersCanOpenTheFiles() throws Exception {
            attach("สื่อการสอน", LINK_URL, "LINK");
            Long attachmentId = academicService.getAttachments(request.getId()).get(0).getId();

            String page = mvc.perform(get(CommitteeDocumentMailer.filesPath(request.getId()))
                    .with(user(committee[1].getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains("สื่อการสอน");

            mvc.perform(get(CommitteeDocumentMailer.filesPath(request.getId()) + "/attachment/" + attachmentId)
                    .with(user(committee[1].getEmail()).roles("USER")))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", LINK_URL));
        }

        @Test
        @DisplayName("คนที่ไม่ใช่กรรมการของคำร้องนี้เปิดไม่ได้ — แม้เป็นผู้ยื่นเอง")
        void outsidersCannotOpenTheFiles() throws Exception {
            attach("สื่อการสอน", LINK_URL, "LINK");
            Long attachmentId = academicService.getAttachments(request.getId()).get(0).getId();
            UserDtls outsider = data.otherApplicant();

            for (UserDtls who : new UserDtls[] { outsider, applicant }) {
                mvc.perform(get(CommitteeDocumentMailer.filesPath(request.getId()))
                        .with(user(who.getEmail()).roles("USER")))
                        .andExpect(redirectedUrl("/esign/inbox"));
                mvc.perform(get(CommitteeDocumentMailer.filesPath(request.getId()) + "/attachment/" + attachmentId)
                        .with(user(who.getEmail()).roles("USER")))
                        .andExpect(status().isNotFound());
            }
        }
    }
}
