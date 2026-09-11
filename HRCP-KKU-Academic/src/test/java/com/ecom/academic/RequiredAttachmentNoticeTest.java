package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * GAP-37 — เอกสารที่ 1 บันทึกไม่ได้จนกว่าจะมีไฟล์แนบ และหน้าจอต้องบอกเรื่องนี้
 *
 * <p>The rule itself is right: ข้อ 1 of the flow lists seven categories of
 * supporting document, so a submission with none attached is incomplete. What
 * was missing is that nothing on the page said so until after the fact — the
 * attachment section opened with "ท่านสามารถแนบไฟล์" ("you may attach"), which
 * reads as optional, and the applicant learned otherwise by filling in the whole
 * form, pressing save, and being bounced back with a query string.
 *
 * <p>Three separate things now carry the message, and this class holds each of
 * them: the requirement stated before the applicant starts, the server refusing
 * the save, and the page explaining the refusal when they land back on it.
 */
@DisplayName("เอกสารที่ 2: ต้องบอกล่วงหน้าว่าจำเป็นต้องแนบไฟล์ (GAP-37)")
class RequiredAttachmentNoticeTest extends AbstractFlowTest {

    @Autowired
    private AcademicRequestService academicRequestService;

    private AcademicRequest draftFor(UserDtls applicant) {
        return data.evaluation(applicant, RequestStatus.DRAFT);
    }

    @Test
    @DisplayName("เปิดหน้าครั้งแรกที่ยังไม่มีไฟล์แนบ — ต้องบอกว่าจำเป็นต้องแนบ ไม่ใช่ 'สามารถแนบได้'")
    void thePageSaysAttachmentsAreRequiredBeforeAnythingIsTyped() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // ตรวจเป็นวลีสั้นสองท่อนที่เป็นแก่นของกติกา ไม่ใช่ล็อกทั้งประโยค
        //
        // เดิมเทสนี้ค้นหาประโยค "ต้องแนบไฟล์อย่างน้อย 1 ไฟล์" แบบตรงตัว แล้วแดง
        // ตอนที่คำบนหน้าจอถูกขัดเกลาเป็น "จำเป็นต้องแนบ (อย่างน้อย 1 รายการ)"
        // ทั้งที่กติกายังถูกบังคับใช้อยู่ครบ — เทสที่ล็อกถ้อยคำจะแดงทุกครั้งที่มี
        // คนแก้คำ ซึ่งสอนให้คนอ่านผลเทสว่า "แดงแล้วไม่ต้องสนใจ"
        assertThat(page)
                .as("ผู้ยื่นต้องรู้ตั้งแต่ก่อนกรอก ไม่ใช่รู้ตอนกดบันทึกแล้วถูกเด้งกลับ")
                .contains("จำเป็นต้องแนบ")
                .contains("อย่างน้อย 1");
        assertThat(page)
                .as("คำว่า 'สามารถแนบ' ทำให้เข้าใจว่าเป็นทางเลือก ทั้งที่ระบบบังคับ")
                .doesNotContain("ท่านสามารถแนบไฟล์หลักฐานประกอบ");
    }

    @Test
    @DisplayName("กดบันทึกทั้งที่ยังไม่มีไฟล์แนบ — ต้องไม่ถูกบันทึก และถูกส่งกลับพร้อมเหตุผล")
    void savingWithoutAttachmentsIsRefusedAndExplained() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        expectAccepted(mvc.perform(post("/user/academic/request/" + draft.getId() + "/document-2")
                .with(user(TestDataFactory.APPLICANT_EMAIL)).with(csrf())
                .param("action", "submit")),
                "/user/academic/request/" + draft.getId() + "/document-2?error=no_attachments");

        assertThat(academicRequestService.getDocumentsByType(draft.getId(), 2))
                .as("ถูกปฏิเสธแล้วต้องไม่มีเอกสารถูกบันทึกไว้")
                .isEmpty();
    }

    @Test
    @DisplayName("หน้าที่ถูกเด้งกลับมาต้องอธิบายสาเหตุ ไม่ใช่เงียบเหมือนกดแล้วไม่มีอะไรเกิดขึ้น")
    void theBouncedPageExplainsWhy() throws Exception {
        UserDtls applicant = data.applicant();
        AcademicRequest draft = draftFor(applicant);

        String page = mvc.perform(get("/user/academic/request/" + draft.getId() + "/document-2")
                .param("error", "no_attachments")
                .with(user(TestDataFactory.APPLICANT_EMAIL)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page)
                .as("query string ที่ผู้ใช้ไม่เคยเห็น ต้องถูกแปลเป็นข้อความบนหน้าจอ")
                .contains("กรุณาแนบไฟล์เอกสารประกอบการประเมินผลการสอนอย่างน้อย 1 ไฟล์");
    }
}
