package com.ecom.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs every migration, on the database production actually uses.
 *
 * <p>{@code FlywayMigrationRunTest} covers V0–V4 on H2 and says in its own
 * comment why it stops there: V5 and V6 use PostgreSQL partial indexes
 * ({@code CREATE UNIQUE INDEX … WHERE …}), which H2 cannot express in any form.
 * Its closing note — <em>"they still need one run against real PostgreSQL before
 * deploy"</em> — never had anything to act on it. Meanwhile V7 through V11 were
 * added and no test executed those either.
 *
 * <p>So this is the missing half: a real PostgreSQL, the whole migration set,
 * and assertions on the two things that only exist on the real engine — that the
 * partial unique indexes are created, and that they actually constrain data.
 *
 * <p>Skipped rather than failed when Docker is not running.
 */
@EnabledIfDockerAvailable
@DisplayName("Migration: รันทุกไฟล์ที่มีอยู่ครบชุดบน PostgreSQL จริง")
class MigrationOnPostgresTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("hrcp_migration")
                    .withUsername("hrcp")
                    .withPassword("hrcp");

    /**
     * The tables the migrations alter but never create.
     *
     * <p>V0 is an empty baseline: when Flyway was introduced the schema already
     * existed, built by Hibernate. On a database this bare, the later migrations
     * therefore have nothing to attach to unless the tables they reference are
     * put there first. Only the columns the migrations touch are needed.
     */
    private static final List<String> PRE_EXISTING_SCHEMA = List.of(
            """
            CREATE TABLE IF NOT EXISTS user_dtls (
                id SERIAL PRIMARY KEY,
                email VARCHAR(255) UNIQUE)
            """,
            """
            CREATE TABLE IF NOT EXISTS academic_request (
                id BIGSERIAL PRIMARY KEY,
                current_status VARCHAR(50),
                CONSTRAINT academic_request_current_status_check
                    CHECK (current_status IN ('DRAFT','RECEIVED','COMPLETED')))
            """,
            """
            CREATE TABLE IF NOT EXISTS position_request (
                id BIGSERIAL PRIMARY KEY,
                current_status VARCHAR(50))
            """,
            """
            CREATE TABLE IF NOT EXISTS staff_member (
                id BIGSERIAL PRIMARY KEY,
                first_name VARCHAR(255),
                last_name VARCHAR(255))
            """,
            """
            CREATE TABLE IF NOT EXISTS academic_document (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT REFERENCES academic_request(id))
            """,
            // V7 drops a check constraint on this one, so it has to be there.
            """
            CREATE TABLE IF NOT EXISTS notifications (
                id BIGSERIAL PRIMARY KEY,
                type VARCHAR(50))
            """,
            // V12 drops the enum checks on these. They carry a CHECK here so the
            // test proves the migration removes a constraint that really exists,
            // rather than passing because there was never anything to drop.
            """
            CREATE TABLE IF NOT EXISTS request_status_history (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT REFERENCES academic_request(id),
                old_status VARCHAR(50),
                new_status VARCHAR(50),
                CONSTRAINT request_status_history_new_status_check
                    CHECK (new_status IN ('DRAFT','RECEIVED','COMPLETED')))
            """,
            """
            CREATE TABLE IF NOT EXISTS position_status_history (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT REFERENCES position_request(id),
                old_status VARCHAR(50),
                new_status VARCHAR(50))
            """,
            """
            CREATE TABLE IF NOT EXISTS academic_committee_member (
                id BIGSERIAL PRIMARY KEY,
                committee_type VARCHAR(50))
            """,
            """
            CREATE TABLE IF NOT EXISTS position_document_edit_log (
                id BIGSERIAL PRIMARY KEY,
                action VARCHAR(50))
            """,
            """
            CREATE TABLE IF NOT EXISTS scopus_publication (
                id BIGSERIAL PRIMARY KEY,
                fs_user_id BIGINT,
                eid VARCHAR(255),
                title VARCHAR(2000),
                synced_at TIMESTAMP DEFAULT NOW() NOT NULL)
            """,
            // V16, V18 และ V20 ล้วน ALTER ตารางนี้ แต่ไม่มี migration ไฟล์ใด
            // สร้างมันเลย — บนเครื่อง production ตารางนี้มีอยู่เพราะยุคก่อนใช้
            // ddl-auto=update แล้ว Hibernate สร้างให้ ซึ่งคือสภาพที่รายการนี้
            // จำลองอยู่ทั้งชุด
            //
            // ตั้งใจให้เป็นรูปร่าง "ก่อน V16": ยังไม่มี checklist_item และความยาว
            // คอลัมน์ยังเป็น 255 ตัวอักษร ทั้งสามไฟล์จึงต้องพิสูจน์ว่ามันแก้ของจริง
            // ไม่ใช่ผ่านเพราะไม่มีอะไรให้แก้
            """
            CREATE TABLE IF NOT EXISTS academic_attachment (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT REFERENCES academic_request(id),
                original_filename VARCHAR(255) NOT NULL,
                stored_file_path VARCHAR(255) NOT NULL,
                file_type VARCHAR(255),
                file_size BIGINT,
                uploaded_at TIMESTAMP,
                is_deleted BOOLEAN DEFAULT FALSE,
                deleted_at TIMESTAMP)
            """);

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    /**
     * Empties the database before every test.
     *
     * <p>Necessary because the container is shared: Flyway records what it has
     * applied, so the second test to run would see nothing left to do and get an
     * empty migration list back. Dropping the schema means each test migrates
     * from bare metal, which is also the only state that proves anything about
     * migrations.
     */
    @BeforeEach
    void resetToABareDatabase() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
            for (String ddl : PRE_EXISTING_SCHEMA) {
                st.execute(ddl);
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static MigrateResult migrate() {
        return migrateUpTo(null);
    }

    /**
     * @param target the last version to apply, or null for everything — lets a
     *               test stand the database up as it was at a past release
     */
    private static MigrateResult migrateUpTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0");
        if (target != null) {
            configuration = configuration.target(org.flywaydb.core.api.MigrationVersion.fromVersion(target));
        }
        return configuration.load().migrate();
    }

    /**
     * ทุกเวอร์ชันที่มีอยู่ในโฟลเดอร์ migration ต้องอยู่ในรายการที่รันไปจริง
     *
     * <p>อ่านรายชื่อไฟล์แทนการเขียนเลขไว้ตายตัว เพราะรายการที่เขียนมือค้างอยู่ที่
     * {@code "14"} มานาน ระหว่างนั้น V15-V20 ถูกเพิ่มเข้ามาโดยไม่มีอะไรยืนยันว่ามันรันได้
     * และเมื่อ V16 พังจริง เทสทั้งคลาสก็ error พร้อมกันหมดโดยไม่มีใครสังเกต
     * เพราะ Docker ไม่ได้เปิด มันจึงขึ้นเป็น skip เงียบ ๆ มาตลอด
     */
    private static List<String> versionsOnDisk() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "db", "migration"))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("V\\d+__.*\\.sql"))
                    .map(n -> n.substring(1, n.indexOf("__")))
                    .filter(v -> !"0".equals(v)) // V0 คือ baseline ที่ Flyway ข้ามโดยตั้งใจ
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .toList();
        }
    }

    @Test
    @DisplayName("migration ทุกไฟล์ที่มีอยู่รันผ่านทั้งชุด และสร้างตารางครบทุกตัว")
    void everyMigrationApplies() throws SQLException, IOException {
        MigrateResult result = migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrations)
                .as("ทุกเวอร์ชันในโฟลเดอร์ migration ต้องถูกรันจริง")
                .extracting(m -> m.version)
                .containsAll(versionsOnDisk());

        assertThat(tableNames())
                .as("ตารางที่ migration รับผิดชอบต้องถูกสร้างครบ")
                .contains("petitions", "petition_statuses", "academic_document_edit_log",
                        "user_signature", "signature_request", "signature_step",
                        "signature_audit_event", "document_workflow_config",
                        "position_request_publication", "external_author_mapping",
                        "journal_tier",
                        // V15 และ V19
                        "kku_regulation_docs", "user_digital_certificate");

        assertThat(tableNames())
                .as("V17 เลิกใช้ที่เก็บไฟล์ส่วนตัวของผู้ใช้ ตารางจึงต้องหายไป")
                .doesNotContain("user_file", "user_folder");
    }

    @Test
    @DisplayName("V16/V18: คอลัมน์ checklist_item ถูกเพิ่มแล้วขยายเป็น integer")
    void v16AndV18AddTheChecklistColumnAsAnInteger() throws SQLException {
        migrate();

        assertThat(columnType("academic_attachment", "checklist_item"))
                .as("""
                        V16 สร้างคอลัมน์นี้เป็น smallint แต่ entity ประกาศเป็น Integer
                        ddl-auto=validate จึงทำให้แอปสตาร์ตไม่ขึ้น V18 มีไว้แก้เรื่องนี้""")
                .isEqualTo("integer");
    }

    @Test
    @DisplayName("V20: ช่องเก็บพาธและชื่อไฟล์ต้องยาวพอสำหรับลิงก์ภายนอก")
    void v20WidensTheColumnsThatHoldLinks() throws SQLException {
        migrate();

        assertThat(columnLength("academic_attachment", "stored_file_path"))
                .as("ลิงก์ Google Drive/OneDrive ยาวเกิน 255 ตัวอักษรได้ง่าย")
                .isEqualTo(2048);
        assertThat(columnLength("academic_attachment", "original_filename"))
                .as("ชื่อลิงก์ที่ผู้ใช้ตั้งเองก็ยาวกว่าชื่อไฟล์ทั่วไป")
                .isEqualTo(500);
    }

    /**
     * The search index is entirely PostgreSQL-shaped, so H2 can say nothing
     * about it: two generated columns, a GIN trigram index and a GIN tsvector
     * index, none of which H2 can express. If the generated columns silently
     * failed to be generated, every search would return nothing and no other
     * test in the repository would notice.
     */
    @Test
    @DisplayName("V21: ตาราง search_document พร้อม generated column และ GIN index ครบ")
    void v21CreatesTheSearchIndex() throws SQLException {
        migrate();

        assertThat(tableNames()).contains("search_document");

        assertThat(isGenerated("search_document", "search_text"))
                .as("ถ้าไม่ใช่ generated column จะไม่มีอะไรเขียนค่าลงไป และการค้นหาจะว่างเปล่าเสมอ")
                .isTrue();
        assertThat(isGenerated("search_document", "tsv")).isTrue();

        assertThat(indexNames())
                .as("ไม่มี GIN trigram ก็ยังค้นได้ แต่กลายเป็น seq scan ทุกครั้ง")
                .contains("idx_search_doc_trgm", "idx_search_doc_tsv", "ux_search_doc_entity");

        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThat(singleInt(st, "SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'"))
                    .as("pg_trgm คือตัวจับคู่หลักของภาษาไทย")
                    .isEqualTo(1);

            st.execute("""
                    INSERT INTO search_document
                        (entity_type, entity_id, title, keywords, body, category, url, visibility)
                    VALUES ('ACADEMIC_REQUEST', 1,
                            'คำร้องขอประเมินผลการสอน', 'KKU-ACAD-256801-0001',
                            'Machine Learning', 'คำร้อง', '/user/academic/dashboard', 'OWNER_OR_ADMIN')
                    """);

            // ไทยเขียนติดกันไม่เว้นวรรค คำค้นจึงต้องจับกลางคำได้
            assertThat(singleInt(st,
                    "SELECT count(*) FROM search_document WHERE search_text LIKE '%ประเมิน%'"))
                    .as("trigram/LIKE ต้องเจอคำที่อยู่กลางวลีภาษาไทย")
                    .isEqualTo(1);

            // ส่วนภาษาอังกฤษที่ปนอยู่ยังแตกเป็น token ให้ tsvector ใช้ได้
            assertThat(singleInt(st,
                    "SELECT count(*) FROM search_document "
                            + "WHERE tsv @@ plainto_tsquery('simple', 'machine learning')"))
                    .as("to_tsvector('simple') แยกคำอังกฤษออกจากข้อความไทยได้")
                    .isEqualTo(1);

            // และคำที่พิมพ์ผิดหนึ่งตัวยังต้องอยู่เหนือ threshold ของชั้น fuzzy
            try (ResultSet rs = st.executeQuery(
                    "SELECT word_similarity('ประเมิณ', search_text) FROM search_document")) {
                rs.next();
                assertThat(rs.getDouble(1))
                        .as("word_similarity เทียบกับช่วงที่ตรงที่สุด ต่างจาก similarity ที่หารด้วยทั้งข้อความ")
                        .isGreaterThanOrEqualTo(0.45);
            }
        }
    }

    /**
     * The check that stands between a deploy and an application that will not
     * start.
     *
     * <p>Production runs {@code ddl-auto=validate}, and deploy is a Windows
     * Service restart with no easy way back. One column whose mapped type
     * disagrees with V21 — the exact failure V16/V18 above records — and the jar
     * refuses to boot after the service has already been stopped.
     *
     * <p>Validating the whole persistence unit is not possible here: the core
     * tables were built by Hibernate before Flyway existed and no migration
     * creates them, so most entities have nothing to validate against on a
     * migrations-only database. This validates the one entity V21 does create,
     * which is the only mapping this change introduces.
     */
    @Test
    @DisplayName("V21: mapping ของ SearchDocument ผ่าน ddl-auto=validate กับสคีมาจริง")
    void v21MatchesTheEntityMapping() {
        migrate();

        org.hibernate.cfg.Configuration cfg = new org.hibernate.cfg.Configuration()
                .setProperty("hibernate.connection.url", POSTGRES.getJdbcUrl())
                .setProperty("hibernate.connection.username", POSTGRES.getUsername())
                .setProperty("hibernate.connection.password", POSTGRES.getPassword())
                .setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .addAnnotatedClass(com.ecom.search.model.SearchDocument.class);

        // buildSessionFactory runs the validation and throws if the schema and
        // the mapping disagree, naming the offending column.
        try (org.hibernate.SessionFactory factory = cfg.buildSessionFactory()) {
            assertThat(factory).isNotNull();
        }
    }

    /**
     * The deploy itself.
     *
     * <p>Every other test here starts from an empty database and runs the whole
     * set at once. That is not what a release does: production already has V0–V20
     * recorded, and the new jar arrives carrying one more file. The interesting
     * question is whether that increment applies to a database that has already
     * lived through the earlier ones — which is a different question, and the one
     * that gets answered for the first time during the deploy window if nothing
     * asks it here.
     *
     * <p>The deploy is a Windows Service stop, a jar swap and a start, so a
     * migration that fails takes the application down with it and there is no
     * quick way back.
     */
    @Test
    @DisplayName("อัปเกรดจากรีลีสก่อนหน้า: ฐานที่มี V0–V20 อยู่แล้ว ต้องรับ V21 ได้")
    void upgradingFromThePreviousReleaseApplies() throws SQLException, IOException {
        List<String> onDisk = versionsOnDisk();
        String previous = onDisk.get(onDisk.size() - 2);
        String latest = onDisk.get(onDisk.size() - 1);

        // ยกฐานให้เป็นสภาพเดียวกับ production ก่อน deploy
        MigrateResult beforeRelease = migrateUpTo(previous);
        assertThat(beforeRelease.success).isTrue();
        assertThat(tableNames())
                .as("ก่อน deploy ต้องยังไม่มีตารางของรีลีสใหม่")
                .doesNotContain("search_document");

        // แล้ว jar ใหม่ก็มาถึง
        MigrateResult release = migrate();

        assertThat(release.success).isTrue();
        assertThat(release.migrationsExecuted)
                .as("ต้องรันเฉพาะไฟล์ที่เพิ่มเข้ามา ไม่ใช่รันซ้ำทั้งชุด")
                .isEqualTo(onDisk.size() - Integer.parseInt(previous));
        assertThat(release.migrations)
                .extracting(m -> m.version)
                .as("V%s ต้องถูกรันในรอบนี้", latest)
                .contains(latest);
        assertThat(tableNames()).contains("search_document");

        // และหลังอัปเกรด mapping ของ entity ต้องยังตรงกับสคีมา ไม่งั้นแอปไม่บูต
        assertThat(isGenerated("search_document", "search_text")).isTrue();
        assertThat(indexNames()).contains("idx_search_doc_trgm");
    }

    @Test
    @DisplayName("รันซ้ำเป็นครั้งที่สองต้องไม่พังและไม่ทำอะไรเพิ่ม (idempotent)")
    void migratingTwiceIsSafe() {
        migrate();
        MigrateResult second = migrate();

        assertThat(second.success).isTrue();
        assertThat(second.migrationsExecuted)
                .as("รอบสองต้องไม่มี migration ใดถูกรันซ้ำ")
                .isZero();
    }

    /**
     * The reason this test class exists. H2 has no partial index at all, so on
     * the old setup this constraint was written, shipped, and never once checked.
     */
    @Test
    @DisplayName("V5: partial unique index บังคับได้จริง — ลายเซ็นเริ่มต้นมีได้คนละหนึ่งอันเท่านั้น")
    void v5PartialUniqueIndexIsEnforced() throws SQLException {
        migrate();

        assertThat(indexNames()).contains("idx_user_signature_one_default");

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO user_dtls (email) VALUES ('sig-owner@example.invalid')");
            int ownerId = singleInt(st,
                    "SELECT id FROM user_dtls WHERE email = 'sig-owner@example.invalid'");

            st.execute(insertSignature(ownerId, true));

            // A second signature that is not the default is perfectly fine …
            st.execute(insertSignature(ownerId, false));

            // … but a second default one must not be.
            assertThatThrownBy(() -> st.execute(insertSignature(ownerId, true)))
                    .as("index มี WHERE is_default — ห้ามมีลายเซ็นเริ่มต้นซ้ำต่อหนึ่งคน")
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("V6: ตารางลงนามผูก foreign key ถูกต้อง และลบเป็นทอด ๆ ตามที่ประกาศ")
    void v6ForeignKeysCascade() throws SQLException {
        migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO academic_request DEFAULT VALUES");
            int requestId = singleInt(st, "SELECT max(id) FROM academic_request");

            st.execute("""
                    INSERT INTO signature_request
                        (module, request_id, document_type, document_label, status,
                         verification_code, frozen_json, frozen_hash)
                    VALUES ('ACADEMIC', %d, 0, 'ทดสอบ', 'DRAFT', 'CODE-%d', '{}', 'hash-%d')
                    """.formatted(requestId, requestId, requestId));
            int envelopeId = singleInt(st, "SELECT max(id) FROM signature_request");

            st.execute("""
                    INSERT INTO signature_step
                        (signature_request_id, slot_key, role_label, anchor_placeholder,
                         step_order, status)
                    VALUES (%d, 'applicant', 'ผู้ยื่นคำร้อง', '{{sign_applicant}}', 1, 'WAITING')
                    """.formatted(envelopeId));

            st.execute("DELETE FROM signature_request WHERE id = " + envelopeId);

            assertThat(singleInt(st,
                    "SELECT count(*) FROM signature_step WHERE signature_request_id = " + envelopeId))
                    .as("ON DELETE CASCADE — ลบซองแล้วขั้นตอนต้องหายตาม ไม่เหลือแถวกำพร้า")
                    .isZero();
        }
    }

    /**
     * V11's whole purpose. A check constraint listing enum values means every
     * new value needs a migration before the application can write it — and
     * until then the write fails at runtime with a constraint violation.
     */
    @Test
    @DisplayName("V11: ลบ CHECK ที่ผูกกับค่า enum ออกแล้วจริง — เพิ่มค่าใหม่ได้โดยไม่ต้อง migrate")
    void v11RemovesEnumCheckConstraints() throws SQLException {
        migrate();

        assertThat(checkConstraintsOn("signature_request"))
                .as("ถ้ายังเหลือ CHECK อยู่ การเพิ่มสถานะใหม่ใน enum จะทำให้ระบบเขียนข้อมูลไม่ได้")
                .isEmpty();
        assertThat(checkConstraintsOn("signature_step")).isEmpty();
        assertThat(checkConstraintsOn("user_signature")).isEmpty();
    }

    /**
     * V12 is what lets the two statuses the flow document requires exist at all.
     * Without it the CHECK frozen when the table was first created rejects any
     * value added to the enum afterwards.
     */
    @Test
    @DisplayName("V12: เพิ่มค่าสถานะใหม่ลงตารางได้ หลัง CHECK เก่าถูกลบ")
    void v12LetsNewStatusValuesBeStored() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute(
                    "INSERT INTO academic_request (current_status) VALUES ('COLLEGE_ENDORSED')"))
                    .as("ก่อน migrate: CHECK เก่ายังปฏิเสธค่าใหม่")
                    .isInstanceOf(SQLException.class);
        }

        migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO academic_request (current_status) VALUES ('COLLEGE_ENDORSED')");
            int id = singleInt(st, "SELECT max(id) FROM academic_request");
            st.execute("""
                    INSERT INTO request_status_history (request_id, old_status, new_status)
                    VALUES (%d, 'COMPLETED_PASS', 'COLLEGE_ENDORSED')
                    """.formatted(id));

            assertThat(singleInt(st,
                    "SELECT count(*) FROM request_status_history WHERE new_status = 'COLLEGE_ENDORSED'"))
                    .as("ตารางประวัติสถานะก็ต้องรับค่าใหม่ได้ ไม่งั้นการเปลี่ยนสถานะจะ rollback ทั้งรายการ")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("V12: ลบ CHECK ของคอลัมน์ enum บนตารางคำร้องและประวัติครบทุกตัว")
    void v12RemovesEveryRequestEnumCheck() throws SQLException {
        migrate();

        for (String table : List.of("academic_request", "request_status_history",
                "position_request", "position_status_history",
                "academic_document_edit_log", "position_document_edit_log",
                "academic_committee_member")) {
            assertThat(checkConstraintsOn(table))
                    .as("%s ยังเหลือ CHECK ที่ผูกกับค่า enum", table)
                    .isEmpty();
        }
    }

    /**
     * V13 carries the rule "the same work cannot be submitted twice" (GAP-11/12).
     * The unique key is the half of it the database owns: a double submit, a
     * double-clicked save or a retried request must not be able to record the
     * same publication twice on one request and make it look like two pieces of
     * work.
     */
    @Test
    @DisplayName("V13: ตารางผูกผลงานกันการผูกผลงานชิ้นเดิมกับคำร้องเดิมซ้ำ")
    void v13EnforcesOneLinkPerPublicationPerRequest() throws SQLException {
        migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO position_request (current_status) VALUES ('DRAFT')");
            int requestId = singleInt(st, "SELECT max(id) FROM position_request");

            String link = """
                    INSERT INTO position_request_publication
                        (request_id, publication_id, document_type, slot_index)
                    VALUES (%d, 555, 1, %d)
                    """;
            st.execute(link.formatted(requestId, 1));

            assertThatThrownBy(() -> st.execute(link.formatted(requestId, 2)))
                    .as("ผลงานชิ้นเดียวกันบนคำร้องเดียวกัน ต้องมีได้แถวเดียว")
                    .isInstanceOf(SQLException.class);
        }
    }

    /**
     * Deleting a request must take its publication links with it. Without the
     * cascade the delete fails on a foreign key, and a request nobody can remove
     * is a support ticket rather than an error anyone sees.
     */
    @Test
    @DisplayName("V13: ลบคำร้องแล้วการผูกผลงานต้องถูกลบตามไปด้วย")
    void v13LinksAreDeletedWithTheirRequest() throws SQLException {
        migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO position_request (current_status) VALUES ('DRAFT')");
            int requestId = singleInt(st, "SELECT max(id) FROM position_request");
            st.execute("""
                    INSERT INTO position_request_publication
                        (request_id, publication_id, document_type, slot_index)
                    VALUES (%d, 777, 1, 1)
                    """.formatted(requestId));

            st.execute("DELETE FROM position_request WHERE id = " + requestId);

            assertThat(singleInt(st, "SELECT count(*) FROM position_request_publication"))
                    .isZero();
        }
    }

    // ------------------------------------------------------------------
    // Small SQL helpers
    // ------------------------------------------------------------------

    private static String insertSignature(int ownerId, boolean isDefault) {
        return """
                INSERT INTO user_signature (user_id, kind, name, image_path, is_default)
                VALUES (%d, 'DRAW', 'ลายเซ็น', 'sig-%d-%s.png', %s)
                """.formatted(ownerId, ownerId, isDefault ? "d" : "n", isDefault);
    }

    private static int singleInt(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<String> tableNames() throws SQLException {
        return queryStrings("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public'");
    }

    private List<String> indexNames() throws SQLException {
        return queryStrings("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'");
    }

    /** ชนิดของคอลัมน์ตามที่ PostgreSQL บันทึกไว้จริง เช่น {@code integer}, {@code smallint} */
    private String columnType(String table, String column) throws SQLException {
        List<String> found = queryStrings(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = '" + table + "' "
                        + "AND column_name = '" + column + "'");
        assertThat(found).as("ไม่พบคอลัมน์ %s.%s", table, column).hasSize(1);
        return found.get(0);
    }

    /** คอลัมน์นี้เป็น GENERATED ALWAYS ... STORED หรือไม่ */
    private boolean isGenerated(String table, String column) throws SQLException {
        List<String> found = queryStrings(
                "SELECT is_generated FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = '" + table + "' "
                        + "AND column_name = '" + column + "'");
        assertThat(found).as("ไม่พบคอลัมน์ %s.%s", table, column).hasSize(1);
        return "ALWAYS".equals(found.get(0));
    }

    /** ความยาวสูงสุดของคอลัมน์ชนิดตัวอักษร */
    private int columnLength(String table, String column) throws SQLException {
        List<String> found = queryStrings(
                "SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = '" + table + "' "
                        + "AND column_name = '" + column + "'");
        assertThat(found).as("ไม่พบคอลัมน์ %s.%s", table, column).hasSize(1);
        return Integer.parseInt(found.get(0));
    }

    /**
     * Check constraints a table carries, ignoring the NOT NULL ones PostgreSQL
     * also records as checks.
     */
    private List<String> checkConstraintsOn(String table) throws SQLException {
        return queryStrings("""
                SELECT con.conname
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                WHERE con.contype = 'c'
                  AND rel.relname = '%s'
                  AND pg_get_constraintdef(con.oid) NOT LIKE '%%IS NOT NULL%%'
                """.formatted(table));
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Connection c = connect();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }
}
