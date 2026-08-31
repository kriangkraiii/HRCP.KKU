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
import org.springframework.test.context.TestPropertySource;

import com.ecom.external.model.ScopusPublication;
import com.ecom.external.service.ScopusQueryService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;
import com.ecom.support.TestDataFactory;

/**
 * "ต้องเป็นงานวิจัยของคนคนนั้นเท่านั้น" — verified through the real HTTP layer.
 *
 * <p>Ownership is derived on the server from {@link java.security.Principal} →
 * e-mail → {@code fs_user_id}. No endpoint accepts an owner id, so there is no
 * request shape that could ask for someone else's work. These tests go through
 * the full filter chain rather than calling the service, because "the service
 * filters correctly" and "the endpoint filters correctly" are different claims
 * and it is the second one that protects anybody.
 *
 * <p>The empty case matters as much as the filtered one: an account with no
 * upstream counterpart gets nothing, and that — not a bug in the picker — is the
 * most common reason a professor opens the modal and finds it empty.
 */
@DisplayName("งานวิจัย: ผู้ใช้ต้องเห็นเฉพาะผลงานของตนเอง")
class MyPublicationsScopingTest extends AbstractFlowTest {

    private static final long SOMCHAI_FS_ID = 5001L;
    private static final long MALEE_FS_ID = 5002L;

    @Autowired
    private ScopusQueryService scopusQuery;

    /** Somchai has two papers, Malee one. Both accounts are linked upstream. */
    private void seedTwoProfessorsWithPapers() {
        data.applicant();
        data.otherApplicant();
        data.faculty(SOMCHAI_FS_ID, TestDataFactory.APPLICANT_EMAIL);
        data.faculty(MALEE_FS_ID, TestDataFactory.OTHER_APPLICANT_EMAIL);

        data.publication(SOMCHAI_FS_ID, "Deep learning for Thai OCR", 2023, 12);
        data.publication(SOMCHAI_FS_ID, "Graph neural networks in agriculture", 2024, 3);
        data.publication(MALEE_FS_ID, "Malee's private work on quantum computing", 2024, 40);
    }

    @Nested
    @DisplayName("รายการผลงานผ่าน API")
    class ListingTests {

        @Test
        @DisplayName("เห็นเฉพาะผลงานของตัวเอง ไม่เห็นของอาจารย์ท่านอื่นเลย")
        void listsOnlyOwnPublications() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications").with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linked").value(true))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.data[?(@.title =~ /.*Malee.*/)]").isEmpty());
        }

        /**
         * The likeliest cause of "ไม่มีงานวิจัยให้เลือกเลย" in production. The
         * account is fine and the picker is fine; the two systems simply never
         * matched, because the sign-in address differs from the one the campus
         * directory holds. The response says so via {@code linked:false}, which
         * is what the modal turns into "ไม่พบข้อมูลของคุณในระบบ Fund Management".
         */
        @Test
        @DisplayName("บัญชีที่ไม่มีคู่ในระบบคณะ — ได้ linked:false และรายการว่าง (ไม่ใช่ของคนอื่น)")
        void unmatchedAccountGetsNothing() throws Exception {
            seedTwoProfessorsWithPapers();
            data.user("unlinked@" + TestDataFactory.DOMAIN, "ไม่ผูก", "บัญชี", "ROLE_USER");

            mvc.perform(get("/api/my/publications")
                    .with(user("unlinked@" + TestDataFactory.DOMAIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.linked").value(false))
                    .andExpect(jsonPath("$.total").value(0));
        }

        @Test
        @DisplayName("อีเมลในระบบคณะมีช่องว่างต่อท้าย — ยังจับคู่ได้ถูกต้อง")
        void matchesDespiteTrailingWhitespaceUpstream() throws Exception {
            seedTwoProfessorsWithPapers();

            assertThat(scopusQuery.resolveFsUserId(
                    userNamed(TestDataFactory.APPLICANT_EMAIL)))
                    .contains(SOMCHAI_FS_ID);
        }

        @Test
        @DisplayName("ค้นหาและกรองปี ยังคงจำกัดอยู่ในผลงานของตัวเอง")
        void searchAndYearFilterStayScoped() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications")
                    .param("q", "quantum")
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            mvc.perform(get("/api/my/publications")
                    .param("year_from", "2024")
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        @DisplayName("ไม่ได้ล็อกอิน — เข้าถึง API ไม่ได้")
        void anonymousIsRefused() throws Exception {
            mvc.perform(get("/api/my/publications"))
                    .andExpect(status().is3xxRedirection());
        }
    }

    @Nested
    @DisplayName("การอ้างอิงผลงานที่เลือก")
    class CitationTests {

        @Test
        @DisplayName("ขอ citation ของผลงานคนอื่น — ถูกตัดทิ้ง ไม่คืนข้อมูลใด ๆ")
        void citationsForForeignIdsAreDropped() throws Exception {
            seedTwoProfessorsWithPapers();
            ScopusPublication maleesPaper = data.publication(MALEE_FS_ID,
                    "Another of Malee's papers", 2022, 7);

            mvc.perform(get("/api/my/publications/citations")
                    .param("ids", String.valueOf(maleesPaper.getId()))
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("ขอ citation ปนกัน — ได้เฉพาะของตัวเอง")
        void mixedIdsReturnOnlyOwnedOnes() throws Exception {
            data.applicant();
            data.otherApplicant();
            data.faculty(SOMCHAI_FS_ID, TestDataFactory.APPLICANT_EMAIL);
            data.faculty(MALEE_FS_ID, TestDataFactory.OTHER_APPLICANT_EMAIL);
            ScopusPublication mine = data.publication(SOMCHAI_FS_ID, "My own paper", 2023, 5);
            ScopusPublication theirs = data.publication(MALEE_FS_ID, "Not my paper", 2023, 5);

            mvc.perform(get("/api/my/publications/citations")
                    .param("ids", mine.getId() + "," + theirs.getId())
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].title").value("My own paper"));
        }

        @Test
        @DisplayName("ส่ง id ที่ไม่ใช่ตัวเลขมา — ไม่ล้ม แค่ไม่มีผลลัพธ์")
        void malformedIdsAreIgnored() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications/citations")
                    .param("ids", "abc,,-1,99999")
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Nested
    @DisplayName("ตัวเลข Scopus (จำนวนผลงาน / การอ้างอิง / h-index)")
    class MetricsTests {

        @Test
        @DisplayName("นับเฉพาะผลงานของตัวเอง")
        void metricsCountOnlyOwnWork() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications/metrics")
                    .with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.papers").value(2))
                    .andExpect(jsonPath("$.citations").value(15));
        }

        @Test
        @DisplayName("h-index คำนวณถูกต้องตามนิยาม")
        void hIndexFollowsTheDefinition() {
            assertThat(ScopusQueryService.hIndex(java.util.List.of())).isZero();
            assertThat(ScopusQueryService.hIndex(java.util.List.of(10, 8, 5, 4, 3))).isEqualTo(4);
            assertThat(ScopusQueryService.hIndex(java.util.List.of(1, 1, 1))).isEqualTo(1);
            assertThat(ScopusQueryService.hIndex(java.util.List.of(0, 0))).isZero();
        }

        @Test
        @DisplayName("บัญชีที่ไม่มีคู่ในระบบคณะ — ตัวเลขเป็นศูนย์ ไม่ใช่ของคนอื่น")
        void unmatchedAccountHasZeroMetrics() throws Exception {
            seedTwoProfessorsWithPapers();
            data.user("unlinked@" + TestDataFactory.DOMAIN, "ไม่ผูก", "บัญชี", "ROLE_USER");

            mvc.perform(get("/api/my/publications/metrics")
                    .with(user("unlinked@" + TestDataFactory.DOMAIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.papers").value(0))
                    .andExpect(jsonPath("$.citations").value(0))
                    .andExpect(jsonPath("$.hIndex").value(0));
        }
    }

    /**
     * GAP-13. {@code app.user.email} does two jobs: it names the account
     * {@code AdminInitializer} seeds, and it names the account allowed to see
     * <em>everyone's</em> publications. Setting it to a real professor's address
     * on production would hand that professor the whole faculty's work, silently.
     */
    @Nested
    @DisplayName("ทางลัดบัญชีทดสอบ (GAP-13)")
    @TestPropertySource(properties = "app.user.email=" + TestDataFactory.APPLICANT_EMAIL)
    class UniversalAccessTests {

        @Test
        @DisplayName("บัญชีที่ตรงกับ app.user.email เห็นผลงานของทุกคน")
        void configuredAccountSeesEverything() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications").with(user(TestDataFactory.APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(3));
        }

        @Test
        @DisplayName("บัญชีอื่นไม่ได้รับสิทธิ์นี้ตามไปด้วย")
        void otherAccountsAreUnaffected() throws Exception {
            seedTwoProfessorsWithPapers();

            mvc.perform(get("/api/my/publications")
                    .with(user(TestDataFactory.OTHER_APPLICANT_EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1));
        }
    }

    private UserDtls userNamed(String email) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        return u;
    }
}
