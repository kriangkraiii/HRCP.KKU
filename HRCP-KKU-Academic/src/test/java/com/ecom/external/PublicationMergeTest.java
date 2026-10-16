package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestPublication;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.repository.PositionRequestPublicationRepository;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.PublicationMergeService;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * รวมผลงานตีพิมพ์ที่ซ้ำกันในฐานข้อมูล (docs/PLAN-bughunt-decisions.md ข้อ 2b)
 *
 * <p>ก่อนแก้การกรองซ้ำ ผลงานชิ้นเดียวกันจาก Scopus กับ OpenAlex มีสองแถว ตัวเลือกผลงานซ่อนผลงานที่ใช้ไปแล้วด้วย id
 * ผู้ยื่นที่ใช้แถวหนึ่งไปแล้วจึงเห็นอีกแถวและยื่นผลงานเดิมซ้ำได้
 */
@DisplayName("รวมผลงานตีพิมพ์ที่ซ้ำกัน")
class PublicationMergeTest extends AbstractFlowTest {

    private static final long OWNER = 9401L;

    @Autowired
    private ScopusPublicationRepository publications;

    @Autowired
    private PositionRequestPublicationRepository links;

    @Autowired
    private PublicationMergeService mergeService;

    @Autowired
    private PositionRequestService positionService;

    private ScopusPublication row(String source, String eid, String doi, String hash) {
        ScopusPublication p = new ScopusPublication();
        p.setFsUserId(OWNER);
        p.setDataSource(source);
        p.setEid(eid);
        p.setDoi(doi);
        p.setDedupHash(hash);
        p.setTitle("Attention is all Thai NLP needs");
        p.setPublicationYear(2024);
        p.setSyncedAt(java.time.LocalDateTime.now());
        return publications.saveAndFlush(p);
    }

    private void link(PositionRequest request, Long publicationId) {
        links.saveAndFlush(new PositionRequestPublication(request, publicationId, 1, 1));
    }

    @Test
    @DisplayName("DOI ตรงกัน: แถวของ OpenAlex ถูกซ่อน และคำร้องที่ใช้แถวนั้นย้ายไปชี้แถวของ Scopus")
    void aHarvestedCopyIsMergedIntoTheScopusRow() {
        ScopusPublication scopus = row("SCOPUS", "2-s2.0-1", "10.1/ABC", null);
        ScopusPublication harvested = row("OPENALEX", "OPENALEX:W1", "10.1/abc", null);
        UserDtls applicant = data.applicant();
        PositionRequest usedHarvested = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        link(usedHarvested, harvested.getId());
        PositionRequest usedBoth = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        link(usedBoth, harvested.getId());
        link(usedBoth, scopus.getId());

        int hidden = mergeService.mergeAll();

        assertThat(hidden).isEqualTo(1);
        assertThat(publications.findById(harvested.getId())).as("ถูกซ่อนจากทุก query").isEmpty();
        assertThat(publications.findMergedIntoId(harvested.getId())).isEqualTo(scopus.getId());
        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(OWNER))
                .extracting(ScopusPublication::getId).containsExactly(scopus.getId());
        assertThat(links.findByRequestId(usedHarvested.getId()))
                .as("ผลงานที่ใช้ไปแล้วยังนับว่าใช้แล้ว — ลิงก์ย้ายไปแถวที่เหลือ")
                .extracting(PositionRequestPublication::getPublicationId).containsExactly(scopus.getId());
        assertThat(links.findByRequestId(usedBoth.getId()))
                .as("คำร้องที่ลิงก์ไว้ทั้งสองแถว เหลือลิงก์เดียว")
                .extracting(PositionRequestPublication::getPublicationId).containsExactly(scopus.getId());
    }

    @Test
    @DisplayName("Scopus สองแถวที่ DOI ตรงกัน (เช่น erratum) — ไม่รวมให้ ส่งให้คนตรวจ")
    void twoScopusRowsAreLeftForAPerson() {
        row("SCOPUS", "2-s2.0-1", "10.1/abc", null);
        row("SCOPUS", "2-s2.0-2", "10.1/abc", null);
        row("OPENALEX", "OPENALEX:W1", "10.1/abc", null);

        PublicationMergeService.Review review = mergeService.findDuplicates();

        assertThat(review.mergeable()).isEmpty();
        assertThat(review.skipped()).hasSize(1);
        assertThat(mergeService.mergeAll()).isZero();
        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(OWNER)).hasSize(3);
    }

    @Test
    @DisplayName("ไม่มี DOI แต่ hash ของชื่อเรื่อง+ปี+ผู้แต่งตรงกัน — รวมได้")
    void rowsWithTheSameContentHashAreMerged() {
        ScopusPublication scopus = row("SCOPUS", "2-s2.0-1", null, "hash-1");
        ScopusPublication harvested = row("THAIJO", "THAIJO:1", null, "hash-1");

        mergeService.mergeAll();

        assertThat(publications.findById(harvested.getId())).isEmpty();
        assertThat(publications.findById(scopus.getId())).isPresent();
    }

    @Test
    @DisplayName("เอกสารที่ 1 ยังอ้าง id ของแถวที่ถูกรวม — บันทึกแล้วลิงก์ไปแถวที่เหลือ")
    void savingADocumentThatStillNamesAMergedRowLinksTheSurvivor() {
        ScopusPublication scopus = row("SCOPUS", "2-s2.0-1", "10.1/abc", null);
        ScopusPublication harvested = row("OPENALEX", "OPENALEX:W1", "10.1/abc", null);
        mergeService.mergeAll();
        PositionRequest request = data.positionRequest(data.applicant(), PositionRequestStatus.DRAFT, null);

        positionService.saveDocument(request, 1, "{\"asst_research_working_1\":\"งานวิจัย\","
                + "\"asst_research_working_scopus_id_1\":\"" + harvested.getId() + "\"}", null, "เอกสารที่ 1", null,
                "APPLICANT");

        assertThat(links.findByRequestId(request.getId()))
                .extracting(PositionRequestPublication::getPublicationId).containsExactly(scopus.getId());
    }

    @Test
    @DisplayName("หน้าแอดมิน: แสดงรายการก่อน แล้วกดรวม")
    void theAdminPageListsThenMerges() throws Exception {
        row("SCOPUS", "2-s2.0-1", "10.1/abc", null);
        ScopusPublication harvested = row("OPENALEX", "OPENALEX:W1", "10.1/abc", null);
        UserDtls admin = data.admin();

        String html = mvc.perform(get("/admin/external-sync/publication-duplicates")
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("#" + harvested.getId()).contains("รวมทั้งหมด");

        mvc.perform(post("/admin/external-sync/publication-duplicates/merge").with(csrf())
                .with(user(admin.getEmail()).roles("ADMIN")))
                .andExpect(status().is3xxRedirection());

        assertThat(publications.findById(harvested.getId())).isEmpty();
    }
}
