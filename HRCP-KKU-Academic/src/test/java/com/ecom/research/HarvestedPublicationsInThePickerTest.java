package com.ecom.research;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.external.model.ScopusPublication;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * Whether work harvested from the new sources actually reaches the professor
 * when they come to submit — and whether the rules built for Scopus rows still
 * hold for it.
 *
 * <p>The harvest (V14: Crossref, OpenAlex, DBLP, ThaiJO, KKU IR) writes into the
 * same {@code scopus_publication} table keyed by {@code fs_user_id}, so nothing
 * had to be wired for the picker to see it. That is a claim worth a test rather
 * than an assumption: harvested rows differ from Scopus rows in three ways that
 * the picker predates — no {@code eid}, no citation count, and a
 * {@code data_source} that is not {@code SCOPUS} — and any one of them could
 * have been what a query or a formatter quietly depended on.
 */
@DisplayName("งานวิจัยจากแหล่งใหม่ (V14) ต้องใช้ยื่นขอตำแหน่งได้เหมือนของ Scopus")
class HarvestedPublicationsInThePickerTest extends AbstractFlowTest {

    private static final long FS_ID = 8101L;

    private UserDtls linkedApplicant() {
        UserDtls applicant = data.applicant();
        data.faculty(FS_ID, TestDataFactory.APPLICANT_EMAIL);
        return applicant;
    }

    @Test
    @DisplayName("ผลงานที่ดึงมาจากแหล่งใหม่ ต้องปรากฏใน picker ตอนยื่นขอตำแหน่ง")
    void harvestedWorkAppearsInThePicker() throws Exception {
        UserDtls applicant = linkedApplicant();
        data.harvestedPublication(FS_ID, "การเรียนรู้เชิงลึกสำหรับลายมือไทย", 2024, "OPENALEX");
        data.harvestedPublication(FS_ID, "A federated approach to Thai NLP", 2023, "CROSSREF");
        data.publication(FS_ID, "Scopus-indexed work", 2022, 12);

        mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total")
                        .value(3));
    }

    @Test
    @DisplayName("ผลงานจากแหล่งใหม่ต้องบอกได้ว่ามาจากไหน และจัดรูป citation ลงฟอร์มได้")
    void harvestedWorkCarriesItsSourceAndFormatsAsACitation() throws Exception {
        UserDtls applicant = linkedApplicant();
        ScopusPublication harvested =
                data.harvestedPublication(FS_ID, "A federated approach to Thai NLP", 2023, "CROSSREF");

        mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                .andExpect(jsonPath("$.data[0].dataSource").value("CROSSREF"))
                .andExpect(jsonPath("$.data[0].eid").doesNotExist());

        // The citation the form receives must still read as a citation even
        // though the row carries neither an eid nor a citation count.
        mvc.perform(get("/api/my/publications/citations")
                .param("ids", String.valueOf(harvested.getId()))
                .with(user(applicant.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].citation")
                        .value(org.hamcrest.Matchers.containsString("A federated approach to Thai NLP")))
                .andExpect(jsonPath("$.data[0].citation")
                        .value(org.hamcrest.Matchers.containsString("(2023)")));
    }

    @Test
    @DisplayName("กติกาห้ามยื่นซ้ำต้องคุมผลงานจากแหล่งใหม่ด้วย ไม่ใช่เฉพาะของ Scopus")
    void theReuseLockCoversHarvestedWorkToo() throws Exception {
        UserDtls applicant = linkedApplicant();
        ScopusPublication harvested =
                data.harvestedPublication(FS_ID, "ผลงานจากแหล่งใหม่ที่ยื่นไปแล้ว", 2024, "THAIJO");

        data.recordPublicationUse(
                data.positionRequest(applicant, PositionRequestStatus.SCREENING_COMMITTEE, null),
                harvested);

        mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/my/publications/citations")
                .param("ids", String.valueOf(harvested.getId()))
                .with(user(applicant.getEmail())))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("ผลงานของอาจารย์ท่านอื่นที่ดึงมาจากแหล่งเดียวกัน ต้องไม่รั่วข้ามคน")
    void harvestedWorkStaysWithItsOwner() throws Exception {
        UserDtls applicant = linkedApplicant();
        data.otherApplicant();
        data.faculty(8102L, TestDataFactory.OTHER_APPLICANT_EMAIL);

        data.harvestedPublication(FS_ID, "ของสมชาย", 2024, "OPENALEX");
        data.harvestedPublication(8102L, "ของมาลี", 2024, "OPENALEX");

        mvc.perform(get("/api/my/publications").with(user(applicant.getEmail())))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].title").value("ของสมชาย"));
    }
}
