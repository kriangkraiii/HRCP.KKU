package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.ScopusPublication;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.repository.ScopusPublicationRepository;
import com.ecom.external.service.FsSyncWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Locks in the retention rule: <b>data that disappears upstream must survive
 * here</b>.
 *
 * <p>A promotion request can cite a publication for years after the source
 * system stops listing it, and an upstream outage that returns a short list must
 * not be able to erase our copy. These tests fail if anyone later adds a
 * "clean up rows we no longer see" step to the sync.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:syncretention;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "fs.api.enabled=false",
        "fs.sync.on-startup=false"
})
class SyncRetentionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private FsSyncWriter writer;

    @Autowired
    private ScopusPublicationRepository publicationRepo;

    @Autowired
    private FsFacultyRepository facultyRepo;

    @BeforeEach
    void reset() {
        publicationRepo.deleteAll();
        facultyRepo.deleteAll();
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("ผลงานที่ต้นทางลบไปแล้ว ต้องยังอยู่ในฐานข้อมูลของเรา")
    void publicationRemovedUpstreamIsKeptLocally() {
        // First run: upstream sends two papers.
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-1\",\"title\":\"Paper one\",\"publication_year\":2023}"),
                json("{\"user_id\":1001,\"eid\":\"E-2\",\"title\":\"Paper two\",\"publication_year\":2024}")));

        assertThat(publicationRepo.countByFsUserId(1001L)).isEqualTo(2);

        // Second run: upstream has dropped E-2 entirely.
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-1\",\"title\":\"Paper one\",\"publication_year\":2023}")));

        assertThat(publicationRepo.countByFsUserId(1001L))
                .as("ผลงานที่หายจากต้นทางต้องไม่ถูกลบตาม")
                .isEqualTo(2);
        assertThat(publicationRepo.findByFsUserIdAndEid(1001L, "E-2")).isPresent();
    }

    @Test
    @DisplayName("ต้นทางส่งข้อมูลมาว่างเปล่า (เช่น API ล่ม) ต้องไม่ล้างข้อมูลเรา")
    void emptyUpstreamResponseDoesNotWipeLocalData() {
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-1\",\"title\":\"Paper one\"}")));

        writer.writePublicationBatch(List.of());

        assertThat(publicationRepo.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("อาจารย์ที่ถูกลบจากต้นทาง ต้องยังอยู่ พร้อมผลงานเดิม")
    void facultyRemovedUpstreamKeepsTheirPublications() {
        writer.writeFacultyBatch(List.of(
                json("{\"user_id\":1001,\"user_fname\":\"Somchai\",\"email\":\"somchai@kku.ac.th\"}"),
                json("{\"user_id\":1002,\"user_fname\":\"Malee\",\"email\":\"malee@kku.ac.th\"}")));
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1002,\"eid\":\"E-9\",\"title\":\"Malee paper\"}")));

        // Upstream now only reports Somchai.
        writer.writeFacultyBatch(List.of(
                json("{\"user_id\":1001,\"user_fname\":\"Somchai\",\"email\":\"somchai@kku.ac.th\"}")));

        assertThat(facultyRepo.count()).isEqualTo(2);
        assertThat(facultyRepo.findById(1002L)).isPresent();
        assertThat(publicationRepo.countByFsUserId(1002L)).isEqualTo(1);
    }

    @Test
    @DisplayName("อัปเดตซ้ำต้องแก้แถวเดิม ไม่สร้างแถวใหม่ และ id ต้องคงที่")
    void resyncUpdatesInPlaceAndKeepsTheLocalId() {
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-1\",\"title\":\"Old title\",\"cited_by\":1}")));
        Long idBefore = publicationRepo.findByFsUserIdAndEid(1001L, "E-1").orElseThrow().getId();

        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-1\",\"title\":\"New title\",\"cited_by\":7}")));

        ScopusPublication after = publicationRepo.findByFsUserIdAndEid(1001L, "E-1").orElseThrow();
        assertThat(publicationRepo.count()).isEqualTo(1);
        // A stable id matters: a saved selection in a request form points at it.
        assertThat(after.getId()).isEqualTo(idBefore);
        assertThat(after.getTitle()).isEqualTo("New title");
        assertThat(after.getCitedBy()).isEqualTo(7);
    }

    @Test
    @DisplayName("ผลงานเรื่องเดียวกันของอาจารย์คนละคน ต้องเก็บแยกแถวกัน")
    void sameEidForTwoAuthorsIsStoredAsTwoRows() {
        // Co-authored papers arrive once per faculty member; the natural key is
        // (fs_user_id, eid), so both copies must persist.
        writer.writePublicationBatch(List.of(
                json("{\"user_id\":1001,\"eid\":\"E-SHARED\",\"title\":\"Joint work\"}"),
                json("{\"user_id\":1002,\"eid\":\"E-SHARED\",\"title\":\"Joint work\"}")));

        assertThat(publicationRepo.count()).isEqualTo(2);
        assertThat(publicationRepo.findByFsUserIdAndEid(1001L, "E-SHARED")).isPresent();
        assertThat(publicationRepo.findByFsUserIdAndEid(1002L, "E-SHARED")).isPresent();
    }

    @Test
    @DisplayName("เก็บ field ที่โปรเจกต์เดิมไม่มี รวมถึง raw_json ทั้งก้อน")
    void storesFieldsTheProjectDidNotPreviouslyHave() {
        writer.writePublicationBatch(List.of(json("""
                {"user_id":1001,"eid":"E-1","title":"T",
                 "affiliation_afid":"60017165","affiliation_city":"Khon Kaen",
                 "affiliations_json":"[{\\"afid\\":\\"60017165\\"}]",
                 "user_affiliation_country":"Thailand",
                 "openaccess_flag":1,"conference_venue":"Hall A",
                 "fund_sponsor":"NRCT","article_number":"5866"}
                """)));

        ScopusPublication p = publicationRepo.findByFsUserIdAndEid(1001L, "E-1").orElseThrow();

        assertThat(p.getAffiliationAfid()).isEqualTo("60017165");
        assertThat(p.getAffiliationCity()).isEqualTo("Khon Kaen");
        assertThat(p.getAffiliationsJson()).contains("60017165");
        assertThat(p.getUserAffiliationCountry()).isEqualTo("Thailand");
        assertThat(p.getOpenAccessFlag()).isEqualTo(1);
        assertThat(p.getConferenceVenue()).isEqualTo("Hall A");
        assertThat(p.getFundSponsor()).isEqualTo("NRCT");
        assertThat(p.getArticleNumber()).isEqualTo("5866");
        // Anything not mapped to a column is still recoverable from the raw payload.
        assertThat(p.getRawJson()).contains("\"eid\":\"E-1\"");
    }

    @Test
    @DisplayName("scopus_id ที่มี prefix SCOPUS_ID: ต้องถูกตัดให้เหลือตัวเลข")
    void stripsScopusIdPrefix() {
        writer.writePublicationBatch(List.of(json(
                "{\"user_id\":1001,\"eid\":\"E-1\",\"scopus_id\":\"SCOPUS_ID:85198403691\"}")));

        assertThat(publicationRepo.findByFsUserIdAndEid(1001L, "E-1").orElseThrow().getScopusId())
                .isEqualTo("85198403691");
    }

    @Test
    @DisplayName("อีเมลต้นทางที่มีช่องว่าง/ตัวพิมพ์ใหญ่ ต้องถูกทำให้เป็นมาตรฐานก่อนเก็บ")
    void normalisesEmailOnWrite() {
        writer.writeFacultyBatch(List.of(json(
                "{\"user_id\":1001,\"email\":\"  Somchai@KKU.ac.th \"}")));

        FsFaculty f = facultyRepo.findById(1001L).orElseThrow();
        assertThat(f.getEmail()).isEqualTo("somchai@kku.ac.th");
    }

    @Test
    @DisplayName("is_active ที่ต้นทางส่งเป็น \"A\" ต้องนับว่ายังใช้งานอยู่")
    void treatsUpstreamActiveFlagCorrectly() {
        // The published contract says "1"/"0"; the live feed sends "A".
        FsFaculty f = new FsFaculty();
        f.setFsUserId(1L);
        f.setSyncedAt(LocalDateTime.now());

        f.setIsActive("A");
        assertThat(f.isActive()).isTrue();
        f.setIsActive("1");
        assertThat(f.isActive()).isTrue();
        f.setIsActive("0");
        assertThat(f.isActive()).isFalse();
    }
}
