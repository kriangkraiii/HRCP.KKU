package com.ecom.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * ผลงานที่พิมพ์เอง (ไม่ได้เลือกจากรายการ) ก็ใช้ซ้ำไม่ได้ และรายการให้เลือกครอบทุกแหล่งข้อมูล
 */
@DisplayName("งานวิจัย: ผลงานที่พิมพ์เองใช้ซ้ำไม่ได้ และเลือกได้ทุกแหล่ง")
class PositionWorkReuseTest extends AbstractFlowTest {

    private static final long FS_ID = 7001L;
    private static final String CITATION =
            "Somchai J., Malee T. (2024). Deep learning for medical image classification. "
                    + "Journal of Computing, 12(3), 1-10.";

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private PositionRequestRepository positionRequests;

    private UserDtls linkedApplicant() {
        UserDtls applicant = data.applicant();
        data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
        return applicant;
    }

    private PositionRequest requestWithWork(UserDtls applicant, PositionRequestStatus status, String work) {
        PositionRequest request = data.positionRequest(applicant, status, null);
        data.positionDocument(request, 1, "{\"assoc_research_working_1\":\"" + work + "\"}");
        return request;
    }

    @Nested
    @DisplayName("ผลงานที่พิมพ์เอง")
    class TypedWorks {

        @Test
        @DisplayName("พิมพ์ผลงานเดิมที่ยื่นไปแล้ว (เว้นวรรค/เลขต่างกันนิดหน่อย) — ถือว่าซ้ำ")
        void retypedWorkIsCaught() {
            UserDtls applicant = linkedApplicant();
            requestWithWork(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, CITATION);
            PositionRequest draft = requestWithWork(applicant, PositionRequestStatus.DRAFT,
                    "Somchai J.,  Malee T. (๒๕๖๗). Deep learning for medical image classification. "
                            + "Journal of Computing, 12(3), 1-10");

            assertThat(positionService.reusedWorks(draft)).hasSize(1);
        }

        @Test
        @DisplayName("คำร้องก่อนหน้ายังเป็นแบบร่าง — ยังไม่นับว่าใช้ไปแล้ว")
        void aDraftSpendsNothing() {
            UserDtls applicant = linkedApplicant();
            requestWithWork(applicant, PositionRequestStatus.DRAFT, CITATION);
            PositionRequest draft = requestWithWork(applicant, PositionRequestStatus.DRAFT, CITATION);

            assertThat(positionService.reusedWorks(draft)).isEmpty();
        }

        @Test
        @DisplayName("ผลงานคนละชิ้นในวารสารเดียวกัน — ไม่ซ้ำ")
        void aDifferentWorkIsFine() {
            UserDtls applicant = linkedApplicant();
            requestWithWork(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, CITATION);
            PositionRequest draft = requestWithWork(applicant, PositionRequestStatus.DRAFT,
                    "Somchai J., Malee T. (2025). Graph neural networks for traffic forecasting in Khon Kaen. "
                            + "Journal of Computing, 13(1), 20-31.");

            assertThat(positionService.reusedWorks(draft)).isEmpty();
        }

        @Test
        @DisplayName("พิมพ์ชื่อเรื่องของผลงานที่เคยเลือกจากรายการไปแล้ว — ถือว่าซ้ำ")
        void typingAPickedTitleIsCaught() {
            UserDtls applicant = linkedApplicant();
            ScopusPublication paper = data.publication(FS_ID, "Deep learning for medical image classification", 2024, 3);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, null), paper);
            PositionRequest draft = requestWithWork(applicant, PositionRequestStatus.DRAFT, CITATION);

            assertThat(positionService.reusedWorks(draft)).containsExactly(CITATION);
        }

        @Test
        @DisplayName("ผลงานของผู้ยื่นคนอื่น — ไม่เกี่ยว")
        void anotherApplicantsWorkDoesNotCount() {
            UserDtls somchai = linkedApplicant();
            requestWithWork(data.otherApplicant(), PositionRequestStatus.DOCUMENT_RECEIVED, CITATION);
            PositionRequest draft = requestWithWork(somchai, PositionRequestStatus.DRAFT, CITATION);

            assertThat(positionService.reusedWorks(draft)).isEmpty();
        }
    }

    @Nested
    @DisplayName("รายการให้เลือก")
    class Picker {

        @Test
        @DisplayName("ทุกแหล่งรวมกัน และแต่ละรายการบอกแหล่งที่มา — กรองตามแหล่งได้")
        void everySourceIsOfferedAndFilterable() throws Exception {
            UserDtls applicant = linkedApplicant();
            data.publication(FS_ID, "ผลงานจาก Scopus", 2024, 3);
            data.harvestedPublication(FS_ID, "ผลงานจาก ThaiJO", 2023, "THAIJO");
            data.harvestedPublication(FS_ID, "ผลงานจาก OpenAlex", 2022, "OPENALEX");

            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(3));

            mvc.perform(get("/api/my/publications").param("source", "THAIJO").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("ผลงานจาก ThaiJO"))
                    .andExpect(jsonPath("$.data[0].dataSource").value("THAIJO"));

            mvc.perform(get("/api/my/publications").param("source", "scopus").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.data[0].dataSource").value("SCOPUS"));
        }
    }

    /**
     * ข้อบังคับ มข. พ.ศ. 2569 ข้อ 34: ผลงานจากคำร้องที่สภามีมติแล้วนำมาใช้ใหม่ได้ ต้องระบุว่าเคยใช้
     * ส่วนคำร้องที่ยังไม่มีมติ (รวมส่งออก มข. แล้วและขอทบทวน) ยังจองผลงานไว้
     */
    @Nested
    @DisplayName("ผลงานจากคำร้องที่สภามีมติแล้ว")
    class AfterTheCouncilDecides {

        private PositionRequest decided(UserDtls applicant, PositionRequestStatus status, java.time.LocalDate councilDate) {
            PositionRequest earlier = requestWithWork(applicant, status, CITATION);
            earlier.setCouncilResolutionDate(councilDate);
            return positionRequests.save(earlier);
        }

        private PositionRequest draftWith(UserDtls applicant, String extraJson) {
            PositionRequest draft = data.positionRequest(applicant, PositionRequestStatus.DRAFT, null);
            data.positionDocument(draft, 1, "{\"assoc_research_working_1\":\"" + CITATION + "\"" + extraJson + "}");
            return draft;
        }

        @Test
        @DisplayName("ไม่อนุมัติแล้ว: ใช้ผลงานเดิมได้ แต่ยังไม่ระบุว่าเคยใช้ — ต้องระบุก่อนส่ง")
        void reusableButMustBeDisclosed() {
            UserDtls applicant = linkedApplicant();
            decided(applicant, PositionRequestStatus.COUNCIL_REJECTED, java.time.LocalDate.now().minusYears(1));
            PositionRequest draft = draftWith(applicant, "");

            assertThat(positionService.reusedWorks(draft)).isEmpty();
            assertThat(positionService.earlierUses(draft))
                    .singleElement()
                    .satisfies(u -> {
                        assertThat(u.work()).isEqualTo(CITATION);
                        assertThat(u.disclosed()).isFalse();
                        assertThat(u.overFiveYears()).isFalse();
                    });
        }

        @Test
        @DisplayName("ติ๊กเคยใช้ พร้อมปีและผลระดับคุณภาพ — ครบ ส่งได้")
        void disclosedWithYearAndLevel() {
            UserDtls applicant = linkedApplicant();
            decided(applicant, PositionRequestStatus.COUNCIL_APPROVED, java.time.LocalDate.now().minusYears(2));
            PositionRequest draft = draftWith(applicant, ",\"assoc_used_research_1\":\"used\","
                    + "\"assoc_used_research_year_1\":\"2567\",\"assoc_used_research_level_1\":\"ดี\"");

            assertThat(positionService.earlierUses(draft)).singleElement()
                    .satisfies(u -> assertThat(u.disclosed()).isTrue());
        }

        @Test
        @DisplayName("ติ๊กเคยใช้แต่ไม่กรอกผลระดับคุณภาพ — ยังไม่ครบ")
        void disclosureNeedsTheLevelToo() {
            UserDtls applicant = linkedApplicant();
            decided(applicant, PositionRequestStatus.COUNCIL_APPROVED, java.time.LocalDate.now().minusYears(2));
            PositionRequest draft = draftWith(applicant, ",\"assoc_used_research_1\":\"used\","
                    + "\"assoc_used_research_year_1\":\"2567\"");

            assertThat(positionService.earlierUses(draft)).singleElement()
                    .satisfies(u -> assertThat(u.disclosed()).isFalse());
        }

        @Test
        @DisplayName("มติสภาเกิน ๕ ปี — เตือน ไม่ห้าม")
        void olderThanFiveYearsIsAWarning() {
            UserDtls applicant = linkedApplicant();
            decided(applicant, PositionRequestStatus.COUNCIL_REJECTED, java.time.LocalDate.now().minusYears(6));
            PositionRequest draft = draftWith(applicant, "");

            assertThat(positionService.reusedWorks(draft)).isEmpty();
            assertThat(positionService.earlierUses(draft)).singleElement()
                    .satisfies(u -> assertThat(u.overFiveYears()).isTrue());
        }

        @Test
        @DisplayName("หน้าเอกสารที่ 1 แสดงผลงานที่เคยใช้ พร้อมบอกว่ายังไม่ระบุ และมติเกิน ๕ ปี")
        void theFormShowsEarlierUses() throws Exception {
            UserDtls applicant = linkedApplicant();
            PositionRequest earlier = decided(applicant, PositionRequestStatus.COUNCIL_REJECTED,
                    java.time.LocalDate.of(2019, 5, 1));
            PositionRequest draft = draftWith(applicant, "");

            String page = mvc.perform(get("/user/position/request/" + draft.getId() + "/document/1")
                    .with(user(applicant.getEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(page).contains("id=\"earlierUsesAlert\"")
                    .contains(earlier.getRequestCode())
                    .contains("มติสภาปี พ.ศ. <span>2562</span>")
                    .contains("ยังไม่ระบุว่าเคยใช้")
                    .contains("มติสภาเกิน ๕ ปี")
                    .doesNotContain("id=\"reusedWorksAlert\"");
        }

        @Test
        @DisplayName("ยังรอมติสภา (ส่งออก มข. / ขอทบทวน) — ยังจองผลงานไว้ ใช้ซ้ำไม่ได้")
        void undecidedRequestsStillHoldTheirWorks() {
            UserDtls applicant = linkedApplicant();
            requestWithWork(applicant, PositionRequestStatus.APPEAL_SUBMITTED, CITATION);
            PositionRequest draft = draftWith(applicant, "");

            assertThat(positionService.reusedWorks(draft)).containsExactly(CITATION);
            assertThat(positionService.earlierUses(draft)).isEmpty();
        }

        @Test
        @DisplayName("ผลงานที่เลือกจากรายการในคำร้องที่สภามีมติแล้ว กลับมาให้เลือกได้อีก")
        void decidedWorksReturnToThePicker() throws Exception {
            UserDtls applicant = linkedApplicant();
            ScopusPublication decidedPaper = data.publication(FS_ID, "ผลงานที่สภามีมติแล้ว", 2023, 2);
            ScopusPublication pendingPaper = data.publication(FS_ID, "ผลงานที่ยังรอมติ", 2024, 1);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.COUNCIL_APPROVED, null), decidedPaper);
            data.recordPublicationUse(
                    data.positionRequest(applicant, PositionRequestStatus.SENT_TO_HR, null), pendingPaper);

            mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("ผลงานที่สภามีมติแล้ว"));
        }
    }
}
