package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.external.model.FsFaculty;
import com.ecom.external.model.FsFacultyChange;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.external.repository.FsFacultyRepository;
import com.ecom.external.service.FacultyChangeReviewService;
import com.ecom.external.service.FsSyncWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Faculty details end up in official promotion documents, so an upstream edit
 * must not reach {@link FsFaculty} until an administrator confirms it. These
 * tests pin that gate: what gets staged, what gets applied, and — most
 * importantly — that a rejection leaves our stored values untouched.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:facultychange;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "fs.api.enabled=false",
        "fs.sync.on-startup=false"
})
class FacultyChangeApprovalTest {

    private static final String ADMIN = "admin@kku.ac.th";

    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private FsSyncWriter writer;

    @Autowired
    private FacultyChangeReviewService reviewService;

    @Autowired
    private FsFacultyRepository facultyRepo;

    @Autowired
    private FsFacultyChangeRepository changeRepo;

    @BeforeEach
    void reset() {
        changeRepo.deleteAll();
        facultyRepo.deleteAll();
    }

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Seeds one professor as if this were the very first sync. */
    private void seedSomchai() {
        writer.writeFacultyBatch(List.of(json("""
                {"user_id":1001,"prefix":"ผศ. ดร.","user_fname":"สมชาย","user_lname":"ใจดี",
                 "email":"somchai@kku.ac.th","position_title":"ผู้ช่วยศาสตราจารย์",
                 "scopus_id":"6506313280","tel":"0891111111"}
                """)));
    }

    /** The same professor, now promoted upstream. */
    private JsonNode promotedSomchai() {
        return json("""
                {"user_id":1001,"prefix":"รศ. ดร.","user_fname":"สมชาย","user_lname":"ใจดี",
                 "email":"somchai@kku.ac.th","position_title":"รองศาสตราจารย์",
                 "scopus_id":"6506313280","tel":"0891111111"}
                """);
    }

    @Test
    @DisplayName("ครั้งแรกที่ยังไม่มีข้อมูล ต้องบันทึกทันที ไม่ต้องรออนุมัติ")
    void firstTimeInsertAppliesImmediately() {
        seedSomchai();

        assertThat(facultyRepo.findById(1001L)).isPresent();
        assertThat(reviewService.pendingCount())
                .as("อาจารย์ใหม่ไม่ควรค้างรออนุมัติ เพราะไม่มีข้อมูลเดิมให้ทับ")
                .isZero();
    }

    @Test
    @DisplayName("ข้อมูลเปลี่ยน ต้องเข้าคิวรออนุมัติ และยังไม่แก้ข้อมูลจริง")
    void changedDataIsStagedAndNotAppliedYet() {
        seedSomchai();

        writer.writeFacultyBatch(List.of(promotedSomchai()));

        FsFaculty stored = facultyRepo.findById(1001L).orElseThrow();
        assertThat(stored.getPositionTitle())
                .as("ต้องยังเป็นค่าเดิมจนกว่าแอดมินจะอนุมัติ")
                .isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(stored.getPrefix()).isEqualTo("ผศ. ดร.");

        assertThat(reviewService.pendingCount()).isEqualTo(1);
        FsFacultyChange change = reviewService.pending().get(0);
        assertThat(change.getFieldCount()).isEqualTo(2);

        Map<String, Map<String, String>> diff = reviewService.readDiff(change);
        assertThat(diff).containsKeys("คำนำหน้า", "ตำแหน่งทางวิชาการ");
        assertThat(diff.get("ตำแหน่งทางวิชาการ").get("old")).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(diff.get("ตำแหน่งทางวิชาการ").get("new")).isEqualTo("รองศาสตราจารย์");
    }

    @Test
    @DisplayName("กดอนุมัติแล้ว ข้อมูลจริงต้องถูกอัปเดต")
    void approvingAppliesTheChange() {
        seedSomchai();
        writer.writeFacultyBatch(List.of(promotedSomchai()));

        Long changeId = reviewService.pending().get(0).getId();
        boolean applied = reviewService.approve(changeId, ADMIN, "ตรวจสอบกับ HR แล้ว");

        assertThat(applied).isTrue();

        FsFaculty stored = facultyRepo.findById(1001L).orElseThrow();
        assertThat(stored.getPositionTitle()).isEqualTo("รองศาสตราจารย์");
        assertThat(stored.getPrefix()).isEqualTo("รศ. ดร.");

        FsFacultyChange decided = changeRepo.findById(changeId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(FsFacultyChange.STATUS_APPROVED);
        assertThat(decided.getReviewedBy()).isEqualTo(ADMIN);
        assertThat(decided.getReviewedAt()).isNotNull();
        assertThat(reviewService.pendingCount()).isZero();
    }

    @Test
    @DisplayName("ไม่อนุมัติ ข้อมูลเดิมต้องคงอยู่ ไม่ถูกแก้")
    void rejectingKeepsTheOriginalValues() {
        seedSomchai();
        writer.writeFacultyBatch(List.of(promotedSomchai()));

        Long changeId = reviewService.pending().get(0).getId();
        reviewService.reject(changeId, ADMIN, "รอเอกสารยืนยันจากคณะ");

        FsFaculty stored = facultyRepo.findById(1001L).orElseThrow();
        assertThat(stored.getPositionTitle()).isEqualTo("ผู้ช่วยศาสตราจารย์");
        assertThat(stored.getPrefix()).isEqualTo("ผศ. ดร.");

        assertThat(changeRepo.findById(changeId).orElseThrow().getStatus())
                .isEqualTo(FsFacultyChange.STATUS_REJECTED);
        assertThat(reviewService.pendingCount()).isZero();
    }

    @Test
    @DisplayName("sync ซ้ำหลายรอบ ต้องไม่สร้างรายการรออนุมัติซ้ำซ้อน")
    void repeatedSyncsUpdateTheSamePendingEntry() {
        seedSomchai();

        writer.writeFacultyBatch(List.of(promotedSomchai()));
        writer.writeFacultyBatch(List.of(promotedSomchai()));
        writer.writeFacultyBatch(List.of(promotedSomchai()));

        assertThat(changeRepo.count())
                .as("ค่าที่เปลี่ยนทุกคืนไม่ควรทำให้คิวตรวจสอบท่วม")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("ข้อมูลไม่เปลี่ยน ต้องไม่มีรายการรออนุมัติ")
    void unchangedDataCreatesNoReviewItem() {
        seedSomchai();
        seedSomchai();

        assertThat(reviewService.pendingCount()).isZero();
        assertThat(changeRepo.count()).isZero();
    }

    @Test
    @DisplayName("อนุมัติรายการที่ตรวจไปแล้ว ต้องไม่ทำซ้ำ")
    void alreadyDecidedChangeCannotBeApprovedTwice() {
        seedSomchai();
        writer.writeFacultyBatch(List.of(promotedSomchai()));
        Long changeId = reviewService.pending().get(0).getId();

        assertThat(reviewService.approve(changeId, ADMIN, null)).isTrue();
        assertThat(reviewService.approve(changeId, ADMIN, null)).isFalse();
    }

    @Test
    @DisplayName("อนุมัติทั้งหมดต้องใช้ได้กับหลายรายการพร้อมกัน")
    void approveAllHandlesEveryPendingEntry() {
        writer.writeFacultyBatch(List.of(
                json("{\"user_id\":1001,\"user_fname\":\"A\",\"email\":\"a@kku.ac.th\"}"),
                json("{\"user_id\":1002,\"user_fname\":\"B\",\"email\":\"b@kku.ac.th\"}")));

        writer.writeFacultyBatch(List.of(
                json("{\"user_id\":1001,\"user_fname\":\"A2\",\"email\":\"a@kku.ac.th\"}"),
                json("{\"user_id\":1002,\"user_fname\":\"B2\",\"email\":\"b@kku.ac.th\"}")));

        assertThat(reviewService.pendingCount()).isEqualTo(2);

        assertThat(reviewService.approveAll(ADMIN)).isEqualTo(2);
        assertThat(facultyRepo.findById(1001L).orElseThrow().getFirstName()).isEqualTo("A2");
        assertThat(facultyRepo.findById(1002L).orElseThrow().getFirstName()).isEqualTo("B2");
    }

    @Test
    @DisplayName("อาจารย์ใหม่ที่เพิ่งมีในต้นทาง ต้องเข้าระบบได้เลยโดยไม่ต้องรออนุมัติ")
    void brandNewFacultyIsNotBlockedBehindReview() {
        seedSomchai();

        writer.writeFacultyBatch(List.of(json(
                "{\"user_id\":2002,\"user_fname\":\"มาลี\",\"email\":\"malee@kku.ac.th\"}")));

        assertThat(facultyRepo.findById(2002L)).isPresent();
        assertThat(reviewService.pendingCount()).isZero();
    }

    @Test
    @DisplayName("อีเมลเปลี่ยนแล้วอนุมัติ ต้องค้นเจอด้วยอีเมลใหม่")
    void approvedEmailChangeIsResolvableAfterwards() {
        seedSomchai();
        writer.writeFacultyBatch(List.of(json("""
                {"user_id":1001,"prefix":"ผศ. ดร.","user_fname":"สมชาย","user_lname":"ใจดี",
                 "email":"somchai.new@kku.ac.th","position_title":"ผู้ช่วยศาสตราจารย์"}
                """)));

        reviewService.approve(reviewService.pending().get(0).getId(), ADMIN, null);

        assertThat(facultyRepo.findByEmailNormalized("somchai.new@kku.ac.th")).isPresent();
    }
}
