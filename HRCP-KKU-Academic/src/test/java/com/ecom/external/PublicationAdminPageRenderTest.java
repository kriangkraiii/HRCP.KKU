package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * The faculty-wide publication list, rendered.
 *
 * <p>Publications arrive from six places now, all into one table, so the screen
 * that reads across the whole faculty has to say which row came from where and
 * let a reviewer narrow to one source. Rendered rather than asserted on the
 * model: a Thymeleaf expression that cannot be resolved fails here and nowhere
 * else — the controller test passes either way.
 */
@DisplayName("หน้ารวมผลงานของแอดมิน: ต้องบอกแหล่งข้อมูลและกรองตามแหล่งได้")
class PublicationAdminPageRenderTest extends AbstractFlowTest {

    private static final long SOMCHAI = 4101L;

    private String openPublicationsPage(String queryString) throws Exception {
        data.admin();
        return mvc.perform(get("/admin/publications" + queryString)
                .with(user(TestDataFactory.ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("แต่ละแถวต้องบอกว่ามาจากแหล่งใด และมีตัวกรองแหล่งข้อมูลให้เลือก")
    void everyRowShowsWhereItCameFromAndTheFilterIsOffered() throws Exception {
        data.faculty(SOMCHAI, TestDataFactory.APPLICANT_EMAIL);
        data.publication(SOMCHAI, "ผลงานจาก Scopus", 2023, 9);
        data.harvestedPublication(SOMCHAI, "ผลงานจาก OpenAlex", 2024, "OPENALEX");

        String page = openPublicationsPage("");

        assertThat(page)
                .as("ผลงานจากแหล่งใหม่ต้องขึ้นในรายการรวม ไม่ใช่เห็นเฉพาะของ Scopus")
                .contains("ผลงานจาก OpenAlex")
                .contains("ผลงานจาก Scopus");
        assertThat(page)
                .as("และต้องบอกได้ว่าแถวไหนมาจากไหน")
                .contains("OPENALEX")
                .contains("SCOPUS");
        assertThat(page)
                .as("ต้องมีตัวกรองแหล่งข้อมูลในฟอร์มค้นหา")
                .contains("name=\"source\"")
                .contains("— ทุกแหล่ง —");
    }

    @Test
    @DisplayName("เลือกแหล่งข้อมูลแล้ว ต้องเหลือเฉพาะผลงานจากแหล่งนั้น")
    void filteringBySourceNarrowsTheList() throws Exception {
        data.faculty(SOMCHAI, TestDataFactory.APPLICANT_EMAIL);
        data.publication(SOMCHAI, "ผลงานจาก Scopus", 2023, 9);
        data.harvestedPublication(SOMCHAI, "ผลงานจาก OpenAlex", 2024, "OPENALEX");

        String page = openPublicationsPage("?source=OPENALEX");

        assertThat(page).contains("ผลงานจาก OpenAlex");
        assertThat(page)
                .as("ผลงานของแหล่งอื่นต้องไม่ติดมาด้วย")
                .doesNotContain("ผลงานจาก Scopus");
    }

    @Test
    @DisplayName("ยังไม่มีผลงานจากแหล่งใดเลย — หน้าต้องเปิดได้ตามปกติ")
    void anEmptyDatabaseStillRenders() throws Exception {
        assertThat(openPublicationsPage(""))
                .contains("ไม่พบผลงานตามเงื่อนไขที่เลือก");
    }
}
