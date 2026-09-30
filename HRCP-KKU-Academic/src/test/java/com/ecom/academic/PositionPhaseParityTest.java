package com.ecom.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * สิ่งที่เฟส 1 (ประเมินการสอน) ทำไว้แล้ว เฟส 2 (ขอตำแหน่ง) ต้องทำเหมือนกัน
 */
@DisplayName("เฟส 2 ทำเหมือนเฟส 1")
class PositionPhaseParityTest extends AbstractFlowTest {

    private static final String DOC = "{\"title\":\"นาย\",\"applicant_name\":\"สมชาย ใจดี\"}";

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private AdminLogRepository adminLogs;

    @Nested
    @DisplayName("เอกสารของแอดมิน (7, 8) — มีรายชื่อผู้ทรงคุณวุฒิ ผู้ยื่นเปิดไม่ได้")
    class AdminDocuments {

        @ParameterizedTest(name = "เอกสารที่ {0}")
        @ValueSource(ints = { 7, 8 })
        @DisplayName("ผู้ยื่นยิง URL ดาวน์โหลดตรง — 403")
        void applicantCannotDownloadAdminDocuments(int type) throws Exception {
            UserDtls applicant = data.applicant();
            PositionRequest request = data.positionRequest(applicant,
                    PositionRequestStatus.SCREENING_COMMITTEE, null);
            data.positionDocument(request, type, "{\"expert_1_name\":\"ผู้ทรงคุณวุฒิลับ\"}");

            mvc.perform(get("/user/position/request/" + request.getId() + "/document/" + type
                    + "/download?format=pdf")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("API ดูตัวอย่าง: ผู้ยื่นขอเอกสารของแอดมิน — 403 ส่วนแอดมินยังได้")
        void previewOfAdminDocumentsIsForAdminsOnly() throws Exception {
            UserDtls applicant = data.applicant();

            mvc.perform(post("/api/position/preview/8")
                    .with(user(applicant.getEmail()).roles("USER")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());

            mvc.perform(post("/api/position/preview/8")
                    .with(user(data.admin().getEmail()).roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("คำนำหน้า — เฉพาะตำแหน่งวิชาการ ไม่มี ดร. (กติกาเดียวกับเฟส 1)")
    class TitleOptions {

        private static final String[] RANK_OPTIONS = {
                "นาย", "นาง", "นางสาว", "อาจารย์",
                "ผู้ช่วยศาสตราจารย์", "รองศาสตราจารย์", "ศาสตราจารย์" };

        @ParameterizedTest(name = "ฝั่งผู้ยื่น เอกสารที่ {0}")
        @ValueSource(ints = { 1, 2, 3, 4, 6 })
        void applicantForms(int type) throws Exception {
            UserDtls applicant = data.applicant();
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

            String html = mvc.perform(get("/user/position/request/" + draft.getId() + "/document/" + type)
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertRankOnly(html);
        }

        @ParameterizedTest(name = "ฝั่งแอดมิน เอกสารที่ {0}")
        @ValueSource(ints = { 1, 2, 3, 4, 6 })
        void adminForms(int type) throws Exception {
            PositionRequest request = data.positionRequest(data.applicant(),
                    PositionRequestStatus.DOCUMENT_VERIFICATION, null);

            String html = mvc.perform(get("/admin/position/request/" + request.getId() + "/document/" + type)
                    .with(user(data.admin().getEmail()).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertRankOnly(html);
        }

        private void assertRankOnly(String html) {
            Matcher m = Pattern.compile("<select name=\"(?:applicant_)?title\"[^>]*>.*?</select>", Pattern.DOTALL)
                    .matcher(html);
            assertThat(m.find()).as("ไม่พบช่องคำนำหน้า").isTrue();
            String select = m.group();
            assertThat(select).doesNotContain("ดร.").contains("data-academic-title");
            for (String option : RANK_OPTIONS) {
                assertThat(select).contains("<option value=\"" + option + "\">");
            }
            assertThat(html).as("ต้องโหลดตัวแปลงคำนำหน้าที่เก็บไว้ (ผศ.ดร. → ผู้ช่วยศาสตราจารย์)")
                    .contains("/js/academic_title.js");
        }
    }

    @Nested
    @DisplayName("การกระทำของผู้ยื่นบันทึกลง log ของผู้ดูแลระบบ")
    class AuditLog {

        @Test
        @DisplayName("ยกเลิกแบบร่าง")
        void cancellingADraftIsLogged() throws Exception {
            UserDtls applicant = data.applicant();
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            data.positionDocument(draft, 1, DOC);

            mvc.perform(post("/user/position/request/" + draft.getId() + "/cancel-draft")
                    .param("confirmCode", "DELETE")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection());

            assertThat(positionService.findById(draft.getId())).isEmpty();
            awaitCondition("log CANCEL_DRAFT ของคำร้องนี้", () -> logged("CANCEL_DRAFT", draft.getId()));
        }

        @Test
        @DisplayName("ยื่นการแก้ไขเอกสารที่ถูกส่งกลับ")
        void submittingARevisionIsLogged() throws Exception {
            UserDtls applicant = data.applicant();
            PositionRequest request = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.positionDocument(request, 6, DOC);
            positionService.openDocumentForRevision(request.getId(), 6, "แก้ข้อมูล");

            mvc.perform(post("/user/position/request/" + request.getId() + "/document/6/submit-revision")
                    .with(csrf()).with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().is3xxRedirection());

            awaitCondition("log SUBMIT_REVISION ของคำร้องนี้", () -> logged("SUBMIT_REVISION", request.getId()));
        }

        private boolean logged(String action, Long requestId) {
            return adminLogs.findByActionContaining(action).stream()
                    .anyMatch(l -> l.getDetails() != null && l.getDetails().endsWith("#" + requestId));
        }
    }
}
