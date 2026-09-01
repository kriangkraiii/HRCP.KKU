package com.ecom.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * "งานวิจัยที่ยื่นไปแล้ว ใช้ยื่นซ้ำไม่ได้" — the rule, written down as tests.
 *
 * <p><b>The rule, as agreed:</b> a publication used in a position request that
 * has left {@code DRAFT} is spent — it must not appear in the picker again and
 * must not be accepted on another request. The single exception is
 * {@code REJECTED}: a request that was refused never consumed anything, so its
 * publications come back.
 *
 * <p><b>What had to be built first.</b> The rule was unenforceable not because a
 * check was missing but because the fact it would check was never recorded:
 * {@code ScopusPicker.applySelection()} asked for a formatted citation, wrote the
 * text into a plain input, and dropped {@code publication.id} in the browser
 * (GAP-11). The picker now keeps that id in a hidden field beside each line, the
 * save path turns it into a {@code position_request_publication} row, and this
 * class is what says the rule holds.
 *
 * <p>Written against the picker's own HTTP API rather than any internal
 * interface, so it constrains behaviour and not implementation — the link table
 * can be reshaped without touching a line here.
 */
@DisplayName("งานวิจัย: กติกาห้ามใช้ผลงานซ้ำ (GAP-11/GAP-12)")
class ResearchReuseLockSpec extends AbstractFlowTest {

    private static final long FS_ID = 7001L;

    @Autowired
    private PositionRequestPublicationRepository links;

    @Nested
    @DisplayName("กติกาห้ามใช้ผลงานซ้ำ")
    class TheRule {

        @Test
        @DisplayName("ยังเป็นแบบร่าง — ผลงานยังใช้ได้ตามปกติ")
        void draftDoesNotConsumeAPublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานชิ้นที่หนึ่ง", 2023, 5);
            PositionRequest draft =
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            data.recordPublicationUse(draft, paper);

            expectPickerOffers(applicant, 1);
        }

        @Test
        @DisplayName("ส่งคำร้องแล้ว — ผลงานนั้นหายจาก picker ทันที")
        void submittingConsumesThePublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานชิ้นที่หนึ่ง", 2023, 5);
            data.publication(FS_ID, "ผลงานชิ้นที่สอง", 2024, 2);
            PositionRequest submitted = data.positionRequest(
                    applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
            data.recordPublicationUse(submitted, paper);

            expectPickerOffers(applicant, 1);
            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.data[0].title").value("ผลงานชิ้นที่สอง"));
        }

        @Test
        @DisplayName("ล็อกตลอดทุกสถานะหลังส่ง จนถึงส่งออกกองทรัพยากรบุคคล")
        void lockHoldsThroughEveryPostDraftStatus() throws Exception {
            for (PositionRequestStatus status : PositionRequestStatus.values()) {
                if (status == PositionRequestStatus.DRAFT
                        || status == PositionRequestStatus.REJECTED) {
                    continue;
                }
                data.reset();
                UserDtls applicant = linkedApplicant();
                ScopusPublication paper = data.publication(FS_ID, "ผลงานที่ใช้ไปแล้ว", 2023, 5);
                data.recordPublicationUse(data.positionRequest(applicant, status, null), paper);

                expectPickerOffers(applicant, 0);
            }
        }

        /**
         * The one release valve. A refused request consumed nothing, and its
         * owner has to be able to put the same work forward again — otherwise a
         * single rejection would permanently retire their publications.
         */
        @Test
        @DisplayName("คำร้องถูกปฏิเสธ — ผลงานกลับมาใช้ได้")
        void rejectionReleasesThePublication() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่เคยยื่นแล้วถูกปฏิเสธ", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.REJECTED, null), paper);

            expectPickerOffers(applicant, 1);
        }

        @Test
        @DisplayName("การล็อกไม่ข้ามไปหาอาจารย์ท่านอื่น")
        void lockIsPerOwnerNotGlobal() throws Exception {
            UserDtls somchai = linkedApplicant();
            UserDtls malee = data.otherApplicant();
            data.faculty(7002L, TestDataFactory.OTHER_APPLICANT_EMAIL);

            ScopusPublication somchaisPaper = data.publication(FS_ID, "ของสมชาย", 2023, 5);
            data.publication(7002L, "ของมาลี", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(somchai, PositionRequestStatus.SENT_TO_HR, null),
                    somchaisPaper);

            expectPickerOffers(somchai, 0);
            expectPickerOffers(malee, 1);
        }

        @Test
        @DisplayName("ยิง citation ของผลงานที่ใช้ไปแล้วตรง ๆ — ต้องถูกปฏิเสธ ไม่ใช่แค่ซ่อนใน UI")
        void spentPublicationIsRefusedAtTheApiToo() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่ใช้ไปแล้ว", 2023, 5);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.SCREENING_COMMITTEE, null),
                    paper);

            mvc.perform(get("/api/my/publications/citations")
                    .param("ids", String.valueOf(paper.getId()))
                    .with(user(applicant.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        private UserDtls linkedApplicant() {
            UserDtls applicant = data.applicant();
            data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
            return applicant;
        }

        private void expectPickerOffers(UserDtls applicant, int expected) throws Exception {
            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(expected));
        }
    }

    /**
     * The path the rule above depends on: what the picker leaves in the form has
     * to survive a save.
     *
     * <p>{@code TheRule} writes link rows directly, so it would go on passing
     * even if the hidden field the picker fills never reached the database. This
     * posts the form exactly as the browser does.
     */
    @Nested
    @DisplayName("เส้นทางจากฟอร์มถึงตารางผูก")
    class SavingTheForm {

        @Test
        @DisplayName("บันทึกเอกสารที่ 1 ที่มี scopus id — ต้องเกิดการผูกผลงานกับคำร้อง")
        void savingTheFormRecordsTheLink() throws Exception {
            UserDtls applicant = data.applicant();
            data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่เลือกจาก picker", 2023, 5);
            PositionRequest draft =
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

            expectAccepted(mvc.perform(post("/user/position/request/" + draft.getId() + "/document/1")
                    .with(user(applicant.getEmail()))
                    .with(csrf())
                    .param("target_position", "ผู้ช่วยศาสตราจารย์")
                    // ชื่อฟิลด์ชุดนี้คือสิ่งที่ ScopusPicker เขียนลงฟอร์มจริง
                    .param("asst_research_working_1", "Somchai J. (2023). ผลงานที่เลือกจาก picker.")
                    .param("asst_research_working_scopus_id_1", String.valueOf(paper.getId()))),
                    "/user/position/request/" + draft.getId());

            assertThat(links.findByRequestId(draft.getId()))
                    .as("id ที่ picker ใส่ไว้ในฟอร์มต้องถูกบันทึกเป็นการผูกผลงาน ไม่ใช่ถูกทิ้ง")
                    .extracting(PositionRequestPublication::getPublicationId)
                    .containsExactly(paper.getId());

            // ยังเป็นแบบร่าง — จึงยังไม่ถูกใช้ ต้องเลือกได้ตามปกติ
            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.total").value(1));
        }

        /**
         * The server half of the reported fault: a paper stayed available in the
         * picker after being submitted.
         *
         * <p>The cause was in the browser — a reopened form came back without the
         * hidden ids the picker had attached, so the next save posted citations
         * with nothing to tie them to and the links were dropped. That half is
         * held by {@code ScopusPickerCoverageTest.reopeningTheFormRestoresThePublicationIds}.
         *
         * <p>What this test holds is the contract that fix relies on: saving the
         * same form twice leaves exactly one link — neither a duplicate row nor a
         * lost one.
         */
        @Test
        @DisplayName("บันทึกฟอร์มเดิมซ้ำ — ต้องเหลือการผูกผลงานหนึ่งรายการ ไม่ซ้ำและไม่หาย")
        void savingTheSameFormTwiceKeepsExactlyOneLink() throws Exception {
            UserDtls applicant = data.applicant();
            data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่เลือกไว้ก่อนกดยื่น", 2023, 5);
            PositionRequest request =
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            String url = "/user/position/request/" + request.getId() + "/document/1";
            String citation = "Somchai J. (2023). ผลงานที่เลือกไว้ก่อนกดยื่น.";

            // ครั้งแรก: เพิ่งเลือกจาก picker — ฟอร์มมี hidden id ติดมาด้วย
            mvc.perform(post(url).with(user(applicant.getEmail())).with(csrf())
                    .param("asst_research_working_1", citation)
                    .param("asst_research_working_scopus_id_1", String.valueOf(paper.getId())));

            // ครั้งที่สอง: เปิดหน้าใหม่แล้วกดบันทึกอีกรอบ ข้อความ citation ยังอยู่ครบ
            // ทุกตัวอักษร — เป็นการบันทึกซ้ำ ไม่ใช่การลบผลงานออกจากฟอร์ม
            mvc.perform(post(url).with(user(applicant.getEmail())).with(csrf())
                    .param("asst_research_working_1", citation)
                    .param("asst_research_working_scopus_id_1", String.valueOf(paper.getId())));

            assertThat(links.findByRequestId(request.getId()))
                    .as("การเปิดหน้าใหม่แล้วบันทึกซ้ำ ต้องไม่ทำให้คำร้องกลายเป็นไม่ได้อ้างผลงานใด")
                    .hasSize(1);
        }

        @Test
        @DisplayName("ลบผลงานออกจากฟอร์มแล้วบันทึกใหม่ — การผูกต้องหายไปด้วย")
        void removingARowRemovesTheLink() throws Exception {
            UserDtls applicant = data.applicant();
            data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
            ScopusPublication paper = data.publication(FS_ID, "ผลงานที่จะถูกลบทิ้ง", 2023, 5);
            PositionRequest draft =
                    data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);

            String url = "/user/position/request/" + draft.getId() + "/document/1";
            mvc.perform(post(url).with(user(applicant.getEmail())).with(csrf())
                    .param("asst_research_working_1", "ผลงานที่จะถูกลบทิ้ง")
                    .param("asst_research_working_scopus_id_1", String.valueOf(paper.getId())));

            // บันทึกอีกครั้งโดยไม่มีแถวนั้นแล้ว
            mvc.perform(post(url).with(user(applicant.getEmail())).with(csrf())
                    .param("asst_research_working_1", ""));

            assertThat(links.findByRequestId(draft.getId()))
                    .as("ฟอร์มคือความจริงว่าคำร้องอ้างผลงานอะไรอยู่ตอนนี้")
                    .isEmpty();
        }
    }
}
