package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.external.harvest.model.RawPublication;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.FsSyncWriter;
import com.ecom.support.AbstractFlowTest;

/**
 * The same paper reaches us from several places at once — Crossref has it by
 * DOI, OpenAlex by its own id, ThaiJO by neither — and a professor opening the
 * picker must see it once, not five times.
 *
 * <p>{@code PublicationDeduplicator} answers this in three tiers: DOI, then a
 * hash of normalised title + year + first author, then a {@code pg_trgm} title
 * similarity. These tests drive the first two through the real write path.
 *
 * <p>These titles are deliberately long enough to reach the third tier, which
 * this database has no {@code pg_trgm} to answer. That used to take the whole
 * batch down with it — the failed statement marked the transaction rollback-only
 * and every write was discarded behind a single WARN line — so the first two
 * tiers could not be tested here at all (GAP-47). The lookup now runs in a
 * transaction of its own, so an unanswerable third tier costs one comparison
 * rather than fifty rows.
 */
@DisplayName("การกรองผลงานซ้ำจากหลายแหล่ง (V14)")
class HarvestDeduplicationTest extends AbstractFlowTest {

    private static final long FS_ID = 9301L;

    @Autowired
    private FsSyncWriter writer;

    @Autowired
    private ScopusPublicationRepository publications;

    private RawPublication.Builder work(String source) {
        return RawPublication.builder()
                .targetFsUserId(FS_ID)
                .title("Attention is all Thai NLP needs: a multi-source study")
                .publicationName("Journal of Testing")
                .publicationYear(2024)
                .authorNames("Somchai Jaidee | Malee Tangjai")
                .dataSource(source);
    }

    /**
     * A threshold nothing can match, so a database that <em>does</em> have
     * pg_trgm still answers "no fuzzy match" and these tests measure the first
     * two tiers either way.
     */
    private static final double NO_FUZZY = 1.01;

    @Test
    @DisplayName("แหล่งต่างกันแต่ DOI เดียวกัน — ต้องเหลือรายการเดียว")
    void theSameDoiFromTwoSourcesIsOneRecord() {
        writer.writeHarvestedBatch(List.of(
                work("CROSSREF").externalId("cr-1").doi("10.1234/abc").build()), NO_FUZZY);
        writer.writeHarvestedBatch(List.of(
                work("OPENALEX").externalId("W999").doi("10.1234/ABC").build()), NO_FUZZY);

        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(FS_ID))
                .as("DOI เดียวกัน (ต่างแค่ตัวพิมพ์) ต้องถูกรวมเป็นรายการเดียว")
                .hasSize(1);
    }

    @Test
    @DisplayName("ไม่มี DOI แต่ชื่อเรื่อง/ปี/ผู้แต่งตรงกัน — ต้องเหลือรายการเดียว")
    void theSameWorkWithoutADoiIsMatchedByItsContent() {
        writer.writeHarvestedBatch(List.of(
                work("DBLP").externalId("dblp-1").build()), NO_FUZZY);
        writer.writeHarvestedBatch(List.of(
                work("THAIJO").externalId("tj-1").build()), NO_FUZZY);

        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(FS_ID))
                .as("ผลงานที่ไม่มี DOI ต้องยังจับซ้ำได้จาก hash ของชื่อเรื่อง+ปี+ผู้แต่ง")
                .hasSize(1);
    }

    @Test
    @DisplayName("ผลงานคนละชิ้นจริง ๆ ต้องไม่ถูกยุบรวมกัน")
    void genuinelyDifferentWorkIsKept() {
        writer.writeHarvestedBatch(List.of(
                work("CROSSREF").externalId("cr-1").doi("10.1234/abc").build()), NO_FUZZY);
        writer.writeHarvestedBatch(List.of(
                RawPublication.builder()
                        .targetFsUserId(FS_ID)
                        .title("A completely different paper about database indexing")
                        .publicationYear(2024)
                        .authorNames("Somchai Jaidee")
                        .dataSource("OPENALEX")
                        .externalId("W1000")
                        .build()), NO_FUZZY);

        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(FS_ID)).hasSize(2);
    }

    @Test
    @DisplayName("ผลงานชิ้นเดียวกันของอาจารย์คนละคน ต้องแยกกันคนละรายการ")
    void aCoAuthoredWorkIsRecordedOncePerAuthor() {
        writer.writeHarvestedBatch(List.of(
                work("CROSSREF").externalId("cr-1").doi("10.1234/abc").build(),
                RawPublication.builder()
                        .targetFsUserId(9302L)
                        .title("Attention is all Thai NLP needs: a multi-source study")
                        .publicationYear(2024)
                        .authorNames("Somchai Jaidee | Malee Tangjai")
                        .dataSource("CROSSREF")
                        .externalId("cr-1")
                        .doi("10.1234/abc")
                        .build()), NO_FUZZY);

        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(FS_ID)).hasSize(1);
        assertThat(publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(9302L))
                .as("การกรองซ้ำผูกกับเจ้าของ ผลงานร่วมจึงต้องปรากฏในรายการของทั้งสองคน")
                .hasSize(1);
    }

    @Test
    @DisplayName("GAP-46: ผลงานเดียวกันจากสองแหล่ง มาถึงพร้อมกันในชุดเขียนเดียว")
    void twoSourcesArrivingInOneBatch() {
        writer.writeHarvestedBatch(List.of(
                work("CROSSREF").externalId("cr-1").doi("10.1234/abc").build(),
                work("OPENALEX").externalId("W999").doi("10.1234/abc").build()), NO_FUZZY);

        List<ScopusPublication> stored =
                publications.findByFsUserIdOrderByPublicationYearDescCitedByDesc(FS_ID);

        assertThat(stored)
                .as("""
                        การกรองซ้ำถามจากฐานข้อมูลอย่างเดียว ไม่ได้ตรวจกันเองภายในชุดที่กำลังเขียน
                        ผลงานชิ้นเดียวกันที่ adapter สองตัวส่งมาในรอบเดียวกันจึงมีโอกาสเข้าไปสองแถว
                        (adapter รันขนานกันด้วย virtual thread และ V14 ไม่มี unique index บน dedup_hash)""")
                .hasSize(1);
    }
}
