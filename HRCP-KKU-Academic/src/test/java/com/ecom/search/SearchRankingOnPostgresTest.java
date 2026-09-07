package com.ecom.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.ecom.search.dto.SearchHit;
import com.ecom.search.dto.SearchPrincipal;
import com.ecom.search.dto.SearchQuery;
import com.ecom.search.repository.SearchDocumentQueryRepository;
import com.ecom.search.repository.SearchSqlBuilder;
import com.ecom.support.RequiredTools;

/**
 * The test that proves the engine actually works.
 *
 * <p>Everything else about the search runs on H2, where the trigram index, the
 * tsvector and the typo-tolerant tier all score zero by construction. So the
 * three things the whole design rests on — that Thai matches mid-phrase without
 * word boundaries, that English inside Thai still tokenises, and that a
 * one-character typo still finds its target — have to be checked on the engine
 * production runs.
 *
 * <p>It drives the real {@link SearchSqlBuilder} over raw JDBC, with no Spring
 * context: the SQL under test is the SQL that ships, binding included.
 *
 * <p>Routed through {@link RequiredTools} so a missing Docker daemon fails on CI
 * rather than vanishing into a skip — the failure mode that once hid a broken
 * V16 for weeks.
 */
@DisplayName("Search: การจัดอันดับและการจับคู่ภาษาไทยบน PostgreSQL จริง")
class SearchRankingOnPostgresTest {

    private static PostgreSQLContainer postgres;
    private static NamedParameterJdbcTemplate jdbc;
    private static SearchDocumentQueryRepository repository;

    /** An administrator, so scoping never hides a row a ranking assertion wants. */
    private static final SearchPrincipal ADMIN = new SearchPrincipal(1, true);

    @BeforeAll
    static void startPostgres() {
        RequiredTools.require(RequiredTools.dockerIsAvailable(), "Docker");

        postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("hrcp_search")
                .withUsername("hrcp")
                .withPassword("hrcp");
        postgres.start();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");

        // V21 stands alone — it has no foreign keys, so the bare-schema fixture
        // the other migration tests need is not required here.
        applyV21(dataSource);

        jdbc = new NamedParameterJdbcTemplate(dataSource);
        repository = new SearchDocumentQueryRepository(
                jdbc, new SearchSqlBuilder(SearchSqlBuilder.Flavor.POSTGRES));
    }

    /**
     * Runs V21 and nothing else.
     *
     * <p>The full chain is not wanted here — V3 onwards alter tables that no
     * migration creates, because Flyway was introduced onto a schema Hibernate
     * had already built, so a migrations-only database cannot run them. That is
     * {@code MigrationOnPostgresTest}'s problem, and it solves it with a fixture
     * of pre-existing tables. This class only needs the search index, and V21
     * declares no foreign keys precisely so it can stand on its own.
     */
    private static void applyV21(DataSource dataSource) {
        String ddl;
        try (var in = SearchRankingOnPostgresTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V21__create_search_document.sql")) {
            if (in == null) {
                throw new IllegalStateException("ไม่พบ V21 ใน classpath");
            }
            ddl = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("อ่าน V21 ไม่ได้", e);
        }

        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("รัน V21 ไม่สำเร็จ", e);
        }
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void freshRows() {
        jdbc.getJdbcTemplate().execute("TRUNCATE search_document");
    }

    private void insert(long id, String title, String keywords, String body,
            String visibility, Integer owner, String occurredAt) {
        jdbc.update("""
                INSERT INTO search_document
                    (entity_type, entity_id, doc_part, title, keywords, body,
                     category, url, visibility, owner_user_id, weight, occurred_at)
                VALUES ('ACADEMIC_REQUEST', :id, 'MAIN', :title, :keywords, :body,
                        'คำร้อง', '/user/academic/dashboard', :visibility, :owner, 1.0,
                        CAST(:occurredAt AS timestamp))
                """, values(id, title, keywords, body, visibility, owner, occurredAt));
    }

    /**
     * A plain map, not {@code Map.of} — several of these values are deliberately
     * null (a row with no body is the normal case) and {@code Map.of} rejects
     * nulls with an NPE that says nothing about which key was at fault.
     */
    private static Map<String, Object> values(long id, String title, String keywords, String body,
            String visibility, Integer owner, String occurredAt) {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", id);
        params.put("title", title);
        params.put("keywords", keywords);
        params.put("body", body);
        params.put("visibility", visibility);
        params.put("owner", owner);
        params.put("occurredAt", occurredAt);
        return params;
    }

    private List<SearchHit> search(String term) {
        return repository.hits(SearchQuery.of(term, 20), ADMIN, false);
    }

    private List<SearchHit> searchFuzzy(String term) {
        return repository.hits(SearchQuery.of(term, 20), ADMIN, true);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("ค้นคำไทยกลางวลีที่ไม่มีเว้นวรรคได้")
    void thaiMatchesMidPhrase() {
        insert(1, "คำร้องขอประเมินผลการสอน (KKU-ACAD-2568-0001)", "สมชาย ใจดี", null,
                "PUBLIC", null, "2026-01-01 00:00:00");

        assertThat(search("ประเมิน"))
                .as("ไทยเขียนติดกัน ตัวตัดคำของ PostgreSQL จึงมองทั้งวลีเป็นโทเคนเดียว "
                        + "trigram เป็นเหตุผลที่ยังค้นเจอ")
                .extracting(SearchHit::entityId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("ค้นภาษาอังกฤษที่ปนอยู่ในข้อความไทยได้")
    void englishInsideThaiIsFound() {
        insert(1, "คำร้องขอประเมินผลการสอน", "รายวิชา", "หัวข้อ Machine Learning และการวิเคราะห์",
                "PUBLIC", null, "2026-01-01 00:00:00");

        assertThat(search("machine learning"))
                .as("to_tsvector('simple') แยกคำอังกฤษออกจากข้อความไทยได้ แม้ไทยจะเป็นโทเคนเดียว")
                .extracting(SearchHit::entityId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("พิมพ์ผิดหนึ่งตัวอักษรยังค้นเจอในชั้น fuzzy")
    void oneCharacterTypoStillFinds() {
        insert(1, "คำร้องขอประเมินผลการสอน", "สมชาย ใจดี", null, "PUBLIC", null,
                "2026-01-01 00:00:00");

        assertThat(search("ประเมิณ"))
                .as("ชั้นแรกเทียบตรงตัว จึงต้องไม่เจอ")
                .isEmpty();
        assertThat(searchFuzzy("ประเมิณ"))
                .as("ชั้นสองใช้ word_similarity — ไม่ใช่ similarity ที่หารด้วยทั้งข้อความ")
                .extracting(SearchHit::entityId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("คำที่พิมพ์ผิดในเนื้อหา ไม่ทำให้ชั้น fuzzy ช้าเพราะไม่อ่าน body")
    void fuzzyIgnoresTheBody() {
        insert(1, "หัวข้ออื่นที่ไม่เกี่ยวข้อง", "คำอื่น", "ศาสตราจารย์เฉพาะกิจอยู่ในเนื้อหาเท่านั้น",
                "PUBLIC", null, "2026-01-01 00:00:00");

        assertThat(searchFuzzy("ศาสตราจารย์เฉพาะกิด"))
                .as("ชั้น fuzzy อ่านแค่ title กับ keywords — วัดแล้วต่างกัน 4.5 วิ กับ 342 ms "
                        + "ที่ห้าหมื่นแถว และเป็นความหมายที่ถูกกว่าด้วย")
                .isEmpty();
    }

    @Test
    @DisplayName("แมตช์ที่ชื่อเรื่องต้องมาก่อนแมตช์ที่เนื้อหา")
    void titleOutranksBody() {
        insert(1, "เรื่องทั่วไป", "ไม่มีอะไร", "ประเมินผลการสอนถูกกล่าวถึงในเนื้อหา",
                "PUBLIC", null, "2026-01-01 00:00:00");
        insert(2, "ประเมินผลการสอน", "ไม่มีอะไร", "เนื้อหาอื่น",
                "PUBLIC", null, "2026-01-01 00:00:00");

        assertThat(search("ประเมินผลการสอน"))
                .extracting(SearchHit::entityId)
                .as("ชื่อเรื่องตรงเป๊ะได้ 3.0 ส่วนเนื้อหาได้ 1.0")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("คะแนนเท่ากันให้เรียงตามความใหม่")
    void recencyBreaksTies() {
        insert(1, "คำร้องขอประเมินผลการสอน", "เหมือนกัน", null, "PUBLIC", null,
                "2020-01-01 00:00:00");
        insert(2, "คำร้องขอประเมินผลการสอน", "เหมือนกัน", null, "PUBLIC", null,
                "2026-09-01 00:00:00");

        assertThat(search("คำร้องขอประเมินผลการสอน"))
                .extracting(SearchHit::entityId)
                .as("เนื้อหาเหมือนกันทุกอย่าง ต่างแค่วันที่")
                .containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("เงื่อนไขสิทธิ์ทำงานบนเอนจินจริงเหมือนที่ทดสอบบน H2")
    void scopingHoldsOnTheRealEngine() {
        insert(1, "คำร้องของผู้ใช้หมายเลขสี่สิบสอง", "ก", null, "OWNER_OR_ADMIN", 42,
                "2026-01-01 00:00:00");
        insert(2, "คำร้องของผู้ใช้คนอื่น", "ข", null, "OWNER_OR_ADMIN", 99,
                "2026-01-01 00:00:00");
        insert(3, "แจ้งเตือนส่วนตัวของผู้ใช้คนอื่น", "ค", null, "OWNER", 99,
                "2026-01-01 00:00:00");

        SearchPrincipal user42 = new SearchPrincipal(42, false);
        List<Long> visibleToUser42 = repository.hits(SearchQuery.of("คำร้อง", 20), user42, false)
                .stream().map(SearchHit::entityId).toList();

        assertThat(visibleToUser42)
                .as("ผู้ยื่นเห็นเฉพาะของตัวเอง")
                .containsExactly(1L);

        assertThat(repository.hits(SearchQuery.of("แจ้งเตือนส่วนตัว", 20), ADMIN, false))
                .as("แจ้งเตือนเป็น OWNER แอดมินก็ต้องไม่เห็น")
                .isEmpty();
    }

    @Test
    @DisplayName("แถวที่ถูกลบแบบ soft delete ไม่โผล่ในผลค้นหา")
    void softDeletedRowsAreHidden() {
        insert(1, "คำร้องที่ยังอยู่", "ก", null, "PUBLIC", null, "2026-01-01 00:00:00");
        insert(2, "คำร้องที่ถูกลบแล้ว", "ข", null, "PUBLIC", null, "2026-01-01 00:00:00");
        jdbc.getJdbcTemplate().update("UPDATE search_document SET is_deleted = TRUE WHERE entity_id = 2");

        assertThat(search("คำร้อง"))
                .extracting(SearchHit::entityId)
                .containsExactly(1L);
    }

    @Test
    @DisplayName("show_trgm บนภาษาไทยไม่ว่างเปล่า — สมมติฐานที่ทั้งดีไซน์แขวนอยู่")
    void thaiProducesTrigrams() {
        Integer count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT array_length(show_trgm('คำร้องขอกำหนดตำแหน่งทางวิชาการ'), 1)", Integer.class);

        assertThat(count)
                .as("ถ้าสระและวรรณยุกต์ไทยไม่ถูกนับเป็นตัวอักษร trigram จะแตกเป็นเศษ "
                        + "และ GIN index จะกลายเป็นแค่ตัวกรองคร่าว ๆ")
                .isNotNull()
                .isGreaterThan(25);
    }
}
