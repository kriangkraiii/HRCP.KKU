package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.model.UserDtls;
import com.ecom.search.index.SearchDocumentLoader;
import com.ecom.search.index.SearchIndexer;
import com.ecom.search.model.SearchDocument;
import com.ecom.search.model.SearchEntityType;
import com.ecom.search.model.SearchVisibility;
import com.ecom.search.repository.SearchDocumentRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * Proves the index keeps up with the tables it mirrors.
 *
 * <p>This is the test that makes the entity-listener approach worth its
 * strangeness. Nothing in the request services calls the indexer; the whole
 * mechanism is an annotation on the entity and a listener that fires after
 * commit. If that wiring comes loose — a missing {@code @EntityListeners}, a
 * bean container change, a listener that throws and is swallowed — every search
 * silently returns stale results, and only a test that saves through the normal
 * path and then reads the index would notice.
 *
 * <p>Runs with {@code app.search.async=false} (set on {@code AbstractFlowTest}),
 * so the write happens on this thread rather than being raced.
 */
class SearchIndexFreshnessTest extends AbstractFlowTest {

    @Autowired
    private SearchDocumentRepository index;

    private UserDtls applicant;

    @BeforeEach
    void seed() {
        data.reset();
        applicant = data.applicant();
    }

    private Optional<SearchDocument> indexRowFor(SearchEntityType type, Long id) {
        return index.findByEntityTypeAndEntityIdAndDocPart(type, id, "MAIN");
    }

    @Test
    @DisplayName("saving a request creates its index row")
    void savingCreatesTheRow() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);

        Optional<SearchDocument> row = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId());
        assertThat(row).as("บันทึกคำร้องแล้วต้องมีแถวใน index ทันที").isPresent();
        assertThat(row.get().getTitle()).contains("คำร้องขอประเมินผลการสอน");
        assertThat(row.get().getStatus()).isEqualTo(RequestStatus.RECEIVED.name());
    }

    @Test
    @DisplayName("the applicant's every name is in the keywords, not just one column")
    void keywordsCarryEveryName() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);

        String keywords = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId())
                .orElseThrow().getKeywords();

        assertThat(keywords)
                .as("บั๊กเดิมคือ index/ค้นหาดูชื่อจากคอลัมน์เดียว ที่นี่ต้องครบทุกคอลัมน์")
                .contains("สมชาย")
                .contains("ใจดี")
                .contains(applicant.getEmail());
    }

    @Test
    @DisplayName("the request is scoped to its applicant, not left open")
    void rowIsScopedToTheApplicant() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);

        SearchDocument row = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId()).orElseThrow();

        assertThat(row.getVisibility()).isEqualTo(SearchVisibility.OWNER_OR_ADMIN);
        assertThat(row.getOwnerUserId())
                .as("ถ้าเจ้าของว่าง ผู้ยื่นคนอื่นจะค้นเจอคำร้องนี้")
                .isEqualTo(applicant.getId());
    }

    @Test
    @DisplayName("updating a request updates its index row")
    void updatingRefreshesTheRow() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);
        String hashBefore = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId())
                .orElseThrow().getContentHash();

        r.setCurrentStatus(RequestStatus.COMPLETED);
        data.saveEvaluation(r);

        SearchDocument row = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(RequestStatus.COMPLETED.name());
        assertThat(row.getContentHash())
                .as("เนื้อหาเปลี่ยน hash ต้องเปลี่ยนตาม ไม่งั้น reconcile จะข้ามแถวนี้ตลอดไป")
                .isNotEqualTo(hashBefore);
    }

    @Test
    @DisplayName("re-saving unchanged content does not rewrite the row")
    void unchangedContentIsNotRewritten() {
        AcademicRequest r = data.evaluation(applicant, RequestStatus.RECEIVED);
        SearchDocument before = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId()).orElseThrow();
        var indexedAtBefore = before.getIndexedAt();

        data.saveEvaluation(r);

        SearchDocument after = indexRowFor(SearchEntityType.ACADEMIC_REQUEST, r.getId()).orElseThrow();
        assertThat(after.getIndexedAt())
                .as("content_hash มีไว้ให้ข้าม UPDATE ที่ไม่มีอะไรเปลี่ยน "
                        + "ไม่งั้น reconcile รอบกลางคืนจะเขียนทับทั้งตารางและสร้าง tsvector ใหม่หมด")
                .isEqualTo(indexedAtBefore);
    }

    @Test
    @DisplayName("a user is indexed under every name they are known by")
    void usersAreIndexed() {
        SearchDocument row = indexRowFor(SearchEntityType.SYSTEM_USER,
                applicant.getId().longValue()).orElseThrow();

        assertThat(row.getVisibility())
                .as("รายชื่อผู้ใช้เป็นข้อมูลของแอดมิน ผู้ยื่นทั่วไปต้องไม่เห็น")
                .isEqualTo(SearchVisibility.ADMIN);
        assertThat(row.getKeywords()).contains("สมชาย").contains("ใจดี");
    }

    /**
     * Locks the shape that keeps the index from corrupting the tables it mirrors.
     *
     * <p>Indexing loads requests and users to flatten them. When that read ran in
     * a writable transaction, Hibernate flushed those entities at commit and
     * wrote back the values they held when they were read — so an index task
     * running alongside a status change put the old status back. It showed up as
     * a workflow test failing roughly two runs in three with a request reverted
     * to DRAFT, which is a data-loss bug wearing a flaky test's clothes.
     *
     * <p>A structural assertion rather than a behavioural one because the bug is
     * a race: reproducing it on demand is unreliable, but the property that
     * prevents it is exact. {@code readOnly = true} puts Hibernate in
     * {@code FlushMode.MANUAL}, and {@code reindex} must stay non-transactional
     * so an outer writable transaction cannot swallow the read-only one.
     */
    @Test
    @DisplayName("the source read is read-only, so indexing can never write back")
    void loadingSourceRowsCannotFlushThem() throws NoSuchMethodException {
        Transactional load = SearchDocumentLoader.class
                .getMethod("load", SearchEntityType.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(load)
                .as("SearchDocumentLoader.load ต้องมี @Transactional")
                .isNotNull();
        assertThat(load.readOnly())
                .as("ถ้าไม่ readOnly Hibernate จะ flush เอนทิตีต้นทางกลับลงตาราง "
                        + "ด้วยค่าที่อ่านมา ทับการแก้ไขที่เกิดขึ้นระหว่างนั้น")
                .isTrue();
        assertThat(load.propagation())
                .as("ต้องเป็นธุรกรรมของตัวเอง เพราะถูกเรียกหลังธุรกรรมต้นทาง commit ไปแล้ว")
                .isEqualTo(Propagation.REQUIRES_NEW);

        assertThat(SearchIndexer.class
                .getMethod("reindex", SearchEntityType.class, Long.class)
                .getAnnotation(Transactional.class))
                .as("reindex ต้องไม่เป็น @Transactional ไม่งั้นธุรกรรมนอกที่เขียนได้ "
                        + "จะกลืนธุรกรรม readOnly ข้างในและ flush กลับมาอีก")
                .isNull();
    }

    @Test
    @DisplayName("index rows exist for more than one entity type")
    void severalTypesAreCovered() {
        data.evaluation(applicant, RequestStatus.RECEIVED);

        List<SearchEntityType> populated = index.findAll().stream()
                .map(SearchDocument::getEntityType)
                .distinct()
                .toList();

        assertThat(populated).contains(
                SearchEntityType.ACADEMIC_REQUEST,
                SearchEntityType.SYSTEM_USER);
    }
}
