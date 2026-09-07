package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.search.index.SearchReconciler;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.repository.SearchDocumentRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * Covers the failures live indexing cannot see.
 *
 * <p>Entity listeners fire for writes that go through the persistence context.
 * A bulk {@code DELETE} in JPQL does not, and neither does a row removed by a
 * cascade — so those leave index rows behind, pointing at records that no
 * longer exist. In a result list that reads as the system being broken rather
 * than the record being gone.
 *
 * <p>The tests below manufacture exactly that drift, by writing to the index
 * directly, and then check the nightly pass repairs it.
 */
class SearchReconcilerTest extends AbstractFlowTest {

    @Autowired
    private SearchDocumentRepository index;

    @Autowired
    private SearchReconciler reconciler;

    private UserDtls applicant;

    @BeforeEach
    void seed() {
        data.reset();
        applicant = data.applicant();
    }

    @Test
    @DisplayName("an index row whose source row is gone is deleted")
    void orphansAreSweptAway() {
        // Stands in for a bulk delete: an index entry for a request id that the
        // source table has never heard of.
        SearchDocument orphan = new SearchDocument();
        orphan.setEntityType(SearchEntityType.ACADEMIC_REQUEST);
        orphan.setEntityId(987_654L);
        orphan.setDocPart("MAIN");
        orphan.setTitle("คำร้องที่ถูกลบไปแล้ว");
        orphan.setCategory("คำร้อง");
        orphan.setUrl("/user/academic/dashboard");
        orphan.setVisibility(com.ecom.search.model.SearchVisibility.ADMIN);
        index.save(orphan);

        reconciler.reconcile(SearchEntityType.ACADEMIC_REQUEST);

        assertThat(index.findByEntityTypeAndEntityId(SearchEntityType.ACADEMIC_REQUEST, 987_654L))
                .as("แถวกำพร้าต้องถูกกวาดทิ้ง ไม่งั้นผลค้นหาจะพาไปหน้า 404")
                .isEmpty();
    }

    @Test
    @DisplayName("a source row with no index entry gets one")
    void missingRowsAreRebuilt() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);

        // Stands in for an index write that failed, or a listener that threw.
        index.deleteByEntityTypeAndEntityId(SearchEntityType.ACADEMIC_REQUEST, r.getId());
        assertThat(index.findByEntityTypeAndEntityId(SearchEntityType.ACADEMIC_REQUEST, r.getId()))
                .isEmpty();

        reconciler.reconcile(SearchEntityType.ACADEMIC_REQUEST);

        assertThat(index.findByEntityTypeAndEntityIdAndDocPart(
                SearchEntityType.ACADEMIC_REQUEST, r.getId(), "MAIN"))
                .as("reconcile ต้องสร้างแถวที่หายไปกลับมา")
                .isPresent();
    }

    @Test
    @DisplayName("a stale row is refreshed from the source")
    void staleRowsAreRefreshed() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);

        SearchDocument row = index.findByEntityTypeAndEntityIdAndDocPart(
                SearchEntityType.ACADEMIC_REQUEST, r.getId(), "MAIN").orElseThrow();
        row.setTitle("ข้อมูลเก่าที่ไม่ตรงกับต้นทางแล้ว");
        row.setContentHash("stale");
        index.save(row);

        reconciler.reconcile(SearchEntityType.ACADEMIC_REQUEST);

        assertThat(index.findByEntityTypeAndEntityIdAndDocPart(
                SearchEntityType.ACADEMIC_REQUEST, r.getId(), "MAIN")
                .orElseThrow().getTitle())
                .as("เนื้อหาที่ไม่ตรงกับต้นทางต้องถูกเขียนทับ")
                .contains("คำร้องขอประเมินผลการสอน");
    }

    @Test
    @DisplayName("a full pass over an already-correct index changes nothing")
    void aCleanIndexIsLeftAlone() {
        data.evaluation(applicant, RequestStatus.RECEIVED);

        // The first pass may still have work to do; the second must not.
        reconciler.reconcileAll();
        String summary = reconciler.reconcileAll();

        assertThat(summary)
                .as("content_hash มีไว้ไม่ให้ pass กลางคืนเขียนทับทั้งตารางทุกคืน")
                .contains("เขียน 0")
                .contains("ลบ 0");
    }
}
