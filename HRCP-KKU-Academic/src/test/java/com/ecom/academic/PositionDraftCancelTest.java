package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRank;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * ยกเลิกแบบร่างคำร้องขอตำแหน่งจากหน้า /user/position/dashboard
 *
 * <p>เซิร์ฟเวอร์บังคับพิมพ์ DELETE ก่อนลบ แต่ modal ของหน้านี้เคยไม่มีช่องให้พิมพ์
 * กดยืนยันแล้วจึงได้ข้อความ "กรุณาพิมพ์ยืนยันด้วย 'DELETE'" ทุกครั้ง ลบแบบร่างไม่ได้เลย
 */
@DisplayName("ยกเลิกแบบร่างคำร้องขอตำแหน่ง")
class PositionDraftCancelTest extends AbstractFlowTest {

    @Autowired
    private PositionRequestService positionService;

    private PositionRequest draftWithData(UserDtls applicant) {
        AcademicRequest evaluation = data.evaluationFor(applicant, AcademicRank.ASSISTANT_PROFESSOR, "อาจารย์");
        PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT,
                evaluation, "ผู้ช่วยศาสตราจารย์");
        // ข้อมูลที่ผู้ใช้กรอกเอง — แบบร่างว่างจะถูกลบทิ้งเองตอนเปิด dashboard
        data.positionDocument(draft, 1, "{\"major\":\"วิทยาการคอมพิวเตอร์\"}");
        return draft;
    }

    @Test
    @DisplayName("modal ยกเลิกแบบร่างมีช่องให้พิมพ์ DELETE")
    void theCancelModalAsksForTheConfirmation() throws Exception {
        UserDtls applicant = data.applicant();
        draftWithData(applicant);

        String html = mvc.perform(get("/user/position/dashboard")
                .with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).containsPattern("<input[^>]*name=\"confirmCode\"");
    }

    @Test
    @DisplayName("พิมพ์ DELETE แล้วยืนยัน — แบบร่างถูกลบ")
    void typingDeleteRemovesTheDraft() throws Exception {
        UserDtls applicant = data.applicant();
        PositionRequest draft = draftWithData(applicant);

        mvc.perform(post("/user/position/request/" + draft.getId() + "/cancel-draft")
                .param("confirmCode", "DELETE")
                .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("succMsg"));

        assertThat(positionService.findById(draft.getId())).isEmpty();
    }
}
