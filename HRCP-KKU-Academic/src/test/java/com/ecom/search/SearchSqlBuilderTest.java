package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.search.dto.SearchQuery;
import com.ecom.search.repository.SearchSqlBuilder;
import com.ecom.search.repository.SearchSqlBuilder.Flavor;

/**
 * The SQL the two databases each get.
 *
 * <p>The point of one builder emitting both dialects is that the visibility
 * predicate is written once. If it ever diverged, {@code SearchScopingTest} —
 * which runs on H2 — would be proving something about code production never
 * executes, and would keep passing while production leaked. So the sharing is
 * asserted directly here rather than assumed.
 *
 * <p>A pure unit test; the builder is a string builder with no Spring in it,
 * which is also what lets the PostgreSQL ranking test drive it over raw JDBC.
 */
class SearchSqlBuilderTest {

    private final SearchSqlBuilder postgres = new SearchSqlBuilder(Flavor.POSTGRES);
    private final SearchSqlBuilder portable = new SearchSqlBuilder(Flavor.PORTABLE);

    private static final SearchQuery PLAIN = SearchQuery.of("ประเมิน", 20);

    @Test
    @DisplayName("เงื่อนไขสิทธิ์เป็นข้อความเดียวกันเป๊ะทั้งสองภาษา")
    void scopeClauseIsIdentical() {
        assertThat(portable.scopeClause())
                .as("นี่คือมาตรการความปลอดภัย ถ้าสองฝั่งต่างกันเมื่อไหร่ เทสต์ที่รันบน H2 "
                        + "จะเลิกพูดถึงโค้ดที่ production ใช้จริง")
                .isEqualTo(postgres.scopeClause());
    }

    @Test
    @DisplayName("เงื่อนไขสิทธิ์ครอบทั้งสี่ระดับการมองเห็น")
    void scopeClauseCoversEveryVisibility() {
        String clause = postgres.scopeClause();

        assertThat(clause)
                .contains("is_deleted = FALSE")
                .contains("'PUBLIC'")
                .contains("d.owner_user_id = :userId")
                .contains("'OWNER','OWNER_OR_ADMIN'")
                .contains(":isAdmin = 1")
                .contains("'ADMIN','OWNER_OR_ADMIN'");
    }

    @Test
    @DisplayName("ทุกคำสั่งพกเงื่อนไขสิทธิ์ไปด้วยเสมอ")
    void everyStatementIsScoped() {
        String scope = postgres.scopeClause();

        assertThat(postgres.hits(PLAIN, false)).contains(scope);
        assertThat(postgres.count(PLAIN, false)).contains(scope);
        assertThat(postgres.typeFacets(PLAIN, false)).contains(scope);
        assertThat(postgres.statusFacets(PLAIN, false)).contains(scope);
        // ชั้น fuzzy คือเส้นทางที่ลืมง่ายที่สุด เพราะรันเฉพาะตอนหาไม่เจอ
        assertThat(postgres.hits(PLAIN, true)).contains(scope);
        assertThat(portable.hits(PLAIN, false)).contains(scope);
    }

    @Test
    @DisplayName("ฝั่ง portable ต้องไม่เอ่ยถึงคอลัมน์ที่ H2 ไม่มี")
    void portableNeverNamesPostgresOnlyColumns() {
        String sql = portable.hits(PLAIN, false) + portable.count(PLAIN, false)
                + portable.typeFacets(PLAIN, false) + portable.statusFacets(PLAIN, false);

        assertThat(sql)
                .as("search_text กับ tsv เป็น generated column ที่สร้างใน V21 เท่านั้น "
                        + "สคีมาของ H2 มาจาก entity ซึ่งไม่ได้ map สองตัวนี้ไว้")
                .doesNotContain("search_text")
                .doesNotContain("d.tsv")
                .doesNotContain("word_similarity")
                .doesNotContain("ts_rank_cd")
                .doesNotContain("plainto_tsquery");
    }

    @Test
    @DisplayName("ฝั่ง PostgreSQL ต้องแมตช์กับคอลัมน์ที่ GIN index สร้างไว้")
    void postgresMatchesTheIndexedExpression() {
        String sql = postgres.hits(PLAIN, false);

        assertThat(sql)
                .as("idx_search_doc_trgm สร้างบน search_text — แมตช์กับนิพจน์อื่นเมื่อไหร่ "
                        + "index จะถูกปิดใช้งานเงียบ ๆ")
                .contains("d.search_text LIKE :pattern")
                .contains("d.tsv @@ plainto_tsquery('simple', :q)");
        assertThat(sql)
                .as("ILIKE ใช้ index ตัวนี้ไม่ได้ และไม่จำเป็นเพราะ search_text lower ไว้แล้ว")
                .doesNotContain("ILIKE");
    }

    @Test
    @DisplayName("ชั้น fuzzy อ่านแค่ title กับ keywords ไม่แตะ body")
    void fuzzyTierAvoidsTheBody() {
        String sql = postgres.hits(PLAIN, true);

        assertThat(sql).contains("word_similarity(:q, lower(coalesce(d.title,'')");
        assertThat(sql)
                .as("วัดที่ห้าหมื่นแถว: อ่านทั้งเอกสาร 4.5 วินาที อ่านแค่ title+keywords 342 ms")
                .doesNotContain("word_similarity(:q, d.search_text)");
    }

    @Test
    @DisplayName("การจัดอันดับไม่คำนวณ similarity บนเนื้อหาทุกแถว")
    void scoringDoesNotScanEveryBody() {
        assertThat(postgres.hits(PLAIN, false))
                .as("term นี้ถูกคำนวณทุกแถวที่ match เพราะ ORDER BY ต้องใช้คะแนนครบ "
                        + "วัดแล้วกินไป 4.7 วินาทีจาก 5.5 ที่ห้าหมื่นแถว")
                .doesNotContain("word_similarity(:q, d.search_text)");
    }

    @Test
    @DisplayName("การเรียงลำดับปิดท้ายด้วย id เสมอ เพื่อให้แบ่งหน้าไม่ทำแถวหาย")
    void everySortIsTotal() {
        for (SearchQuery.SearchSort sort : SearchQuery.SearchSort.values()) {
            SearchQuery query = new SearchQuery("ประเมิน", null, null, null, null, sort, 0, 20);
            assertThat(postgres.hits(query, false))
                    .as("ถ้าลำดับไม่ครบ แถวที่คะแนนเท่ากันจะโผล่สองหน้า หรือหายไปทั้งคู่ (%s)", sort)
                    .containsPattern("d\\.id (ASC|DESC)");
        }
    }

    @Test
    @DisplayName("ตัวกรองที่ไม่ได้เลือก ต้องไม่ปรากฏใน SQL เลย")
    void unsetFiltersAreAbsent() {
        String withoutFilters = postgres.hits(PLAIN, false);

        assertThat(withoutFilters)
                .as("IN ที่รับลิสต์ว่างเป็น syntax error ไม่ใช่การไม่กรอง")
                .doesNotContain(":types")
                .doesNotContain(":statuses")
                .doesNotContain(":from")
                .doesNotContain(":to");

        SearchQuery filtered = new SearchQuery("ประเมิน",
                java.util.List.of("ACADEMIC_REQUEST"), java.util.List.of("RECEIVED"),
                java.time.LocalDateTime.now().minusDays(1), java.time.LocalDateTime.now(),
                SearchQuery.SearchSort.RELEVANCE, 0, 20);

        assertThat(postgres.hits(filtered, false))
                .contains("d.entity_type IN (:types)")
                .contains("d.status IN (:statuses)")
                .contains("d.occurred_at >= :from")
                .contains("d.occurred_at <= :to");
    }

    @Test
    @DisplayName("การนับ facet ต้องถอดตัวกรองของตัวเองออก ไม่งั้นติ๊กแล้วตัวเลือกอื่นหาย")
    void facetCountsDropTheirOwnFilter() {
        SearchQuery filtered = new SearchQuery("ประเมิน",
                java.util.List.of("ACADEMIC_REQUEST"), java.util.List.of("RECEIVED"),
                null, null, SearchQuery.SearchSort.RELEVANCE, 0, 20);

        assertThat(postgres.typeFacets(filtered, false))
                .as("ถ้ายังกรองด้วย type อยู่ รายการ facet จะเหลือตัวเดียวและกดกลับไม่ได้")
                .doesNotContain(":types")
                .contains("d.status IN (:statuses)");

        assertThat(postgres.statusFacets(filtered, false))
                .doesNotContain(":statuses")
                .contains("d.entity_type IN (:types)");
    }
}
