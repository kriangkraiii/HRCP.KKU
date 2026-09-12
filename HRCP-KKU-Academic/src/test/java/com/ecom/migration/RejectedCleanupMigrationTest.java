package com.ecom.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.ecom.academic.model.AcademicDocument;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.model.SignatureModule;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.service.AcademicRequestService;
import com.ecom.academic.service.PositionRequestService;
import com.ecom.academic.service.SignatureWorkflowService;
import com.ecom.academic.service.SignatureWorkflowService.SignerAssignment;
import com.ecom.academic.service.SignatureWorkflowService.ActorContext;
import com.ecom.model.UserDtls;
import com.ecom.support.AbstractFlowTest;

/**
 * V24 ลบคำร้องที่เป็น REJECTED ทิ้งพร้อมข้อมูลลูก เพราะสถานะนี้ไม่มีใน enum แล้ว แถวที่ค้างอยู่จะโหลดไม่ขึ้น.
 *
 * <p>รันไฟล์ SQL จริงบน schema ของเทสต์ (H2 โหมด PostgreSQL ที่ Hibernate สร้างจาก entity) ซึ่ง
 * <em>ไม่มี</em> {@code ON DELETE CASCADE} ของ V6/V13 migration จึงต้องลบตารางลูกเองทุกตัว
 * ถ้าลืมตัวไหน เทสต์นี้จะชน foreign key ก่อนถึง production
 */
@DisplayName("Migration V24: ลบคำร้อง REJECTED ที่ค้างอยู่ในฐานข้อมูล")
class RejectedCleanupMigrationTest extends AbstractFlowTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AcademicRequestService academicService;

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private SignatureWorkflowService workflow;

    private UserDtls applicant;
    private UserDtls officer;

    @BeforeEach
    void cast() {
        applicant = data.applicant();
        officer = data.admin();
        data.signatureFor(applicant);
        allowTheRemovedStatusToBeStored();
    }

    /**
     * ปลดชนิด ENUM ของคอลัมน์สถานะในสคีมาของเทสต์
     *
     * <p>Hibernate 6 แมป {@code @Enumerated(STRING)} เป็นชนิด {@code ENUM} ของ H2 ซึ่งปฏิเสธทั้งการเก็บและ
     * การเปรียบเทียบค่าที่ไม่อยู่ในรายการ — เทสต์นี้จึงเขียนค่า {@code 'REJECTED'} ลงไปไม่ได้เลย
     * ฐานข้อมูลจริงเป็น {@code varchar} มาตั้งแต่ V12 ที่ถอด CHECK constraint ของ enum ออก
     * การปรับตรงนี้จึงทำให้สคีมาของเทสต์ตรงกับของจริง ไม่ใช่การหลบข้อจำกัด
     */
    private void allowTheRemovedStatusToBeStored() {
        for (String table : List.of("academic_request", "position_request")) {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN current_status SET DATA TYPE VARCHAR(50)");
        }
    }

    private void markRejected(String table, Long id) {
        jdbc.update("UPDATE " + table + " SET current_status = 'REJECTED' WHERE id = ?", id);
    }

    private void runV24() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c,
                    new ClassPathResource("db/migration/V24__remove_rejected_requests.sql"));
        }
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void notification(String link) {
        jdbc.update("INSERT INTO notifications (recipient_id, message, link, type, is_read, is_starred,"
                + " is_important, is_deleted, created_at) VALUES (?, 'ทดสอบ', ?, 'ACADEMIC_STATUS_UPDATE',"
                + " FALSE, FALSE, FALSE, FALSE, ?)", applicant.getId(), link, LocalDateTime.now());
    }

    private void searchEntry(String entityType, Long entityId) {
        jdbc.update("INSERT INTO search_document (entity_type, entity_id, doc_part, title, category, url,"
                + " is_external, visibility, weight, indexed_at, is_deleted, extraction_state)"
                + " VALUES (?, ?, 'TEST', 'ทดสอบ', 'ทดสอบ', '/x', FALSE, 'PUBLIC', 1.0, ?, FALSE, 'NONE')",
                entityType, entityId, LocalDateTime.now());
    }

    private SignatureRequest envelopeFor(SignatureModule module, Long requestId) {
        return workflow.createEnvelope(module, requestId, 1, "เอกสารทดสอบ", "{}",
                List.of(new SignerAssignment("applicant", applicant.getId())), null, applicant,
                ActorContext.none()).request();
    }

    @Test
    @DisplayName("คำร้องประเมินการสอนที่ REJECTED หายไปพร้อมข้อมูลลูกทุกตาราง")
    void aRejectedEvaluationIsRemovedWithEverythingHangingOffIt() throws Exception {
        AcademicRequest rejected = data.evaluation(applicant, RequestStatus.RECEIVED);
        AcademicDocument document = data.academicDocument(rejected, 1, "{}");
        academicService.updateStatus(rejected.getId(), RequestStatus.SUB_COMMITTEE_APPOINTED, officer,
                "ทดสอบ", false);
        SignatureRequest envelope = envelopeFor(SignatureModule.ACADEMIC, rejected.getId());
        notification("/user/academic/request/" + rejected.getId());
        searchEntry("ACADEMIC_REQUEST", rejected.getId());
        searchEntry("ACADEMIC_DOCUMENT", document.getId());
        markRejected("academic_request", rejected.getId());

        runV24();

        Long id = rejected.getId();
        assertThat(count("SELECT COUNT(*) FROM academic_request WHERE id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM academic_document WHERE request_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM request_status_history WHERE request_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM signature_request WHERE id = ?", envelope.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM signature_step WHERE signature_request_id = ?",
                envelope.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE link = ?",
                "/user/academic/request/" + id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM search_document WHERE doc_part = 'TEST'")).isZero();
    }

    @Test
    @DisplayName("คำร้องขอตำแหน่งที่ REJECTED หายไปพร้อมข้อมูลลูกทุกตาราง")
    void aRejectedPositionRequestIsRemovedWithEverythingHangingOffIt() throws Exception {
        PositionRequest rejected = data.positionRequest(applicant, PositionRequestStatus.DOCUMENT_RECEIVED, null);
        data.positionDocument(rejected, 1, "{}");
        positionService.updateStatus(rejected.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, officer,
                "ทดสอบ", false);
        SignatureRequest envelope = envelopeFor(SignatureModule.POSITION, rejected.getId());
        notification("/user/position/request/" + rejected.getId());
        searchEntry("POSITION_REQUEST", rejected.getId());
        markRejected("position_request", rejected.getId());

        runV24();

        Long id = rejected.getId();
        assertThat(count("SELECT COUNT(*) FROM position_request WHERE id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM position_document WHERE request_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM position_status_history WHERE request_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM signature_request WHERE id = ?", envelope.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM notifications WHERE link = ?",
                "/user/position/request/" + id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM search_document WHERE doc_part = 'TEST'")).isZero();
    }

    @Test
    @DisplayName("คำร้องอื่นไม่ถูกแตะ")
    void otherRequestsAreUntouched() throws Exception {
        AcademicRequest kept = data.evaluation(applicant, RequestStatus.RECEIVED);
        data.academicDocument(kept, 1, "{}");
        PositionRequest keptPosition = data.positionRequest(data.otherApplicant(),
                PositionRequestStatus.DOCUMENT_RECEIVED, null);
        SignatureRequest envelope = envelopeFor(SignatureModule.ACADEMIC, kept.getId());
        AcademicRequest rejected = data.evaluation(data.otherApplicant(), RequestStatus.RECEIVED);
        markRejected("academic_request", rejected.getId());

        runV24();

        assertThat(count("SELECT COUNT(*) FROM academic_request WHERE id = ?", kept.getId())).isOne();
        assertThat(count("SELECT COUNT(*) FROM academic_document WHERE request_id = ?", kept.getId())).isOne();
        assertThat(count("SELECT COUNT(*) FROM position_request WHERE id = ?", keptPosition.getId())).isOne();
        assertThat(count("SELECT COUNT(*) FROM signature_request WHERE id = ?", envelope.getId())).isOne();
    }

    @Test
    @DisplayName("คำร้องขอตำแหน่งที่ผูกผลประเมินที่ถูกลบ ยังอยู่ แต่ไม่ผูกแล้ว")
    void aPositionRequestBuiltOnARemovedEvaluationSurvivesUnlinked() throws Exception {
        AcademicRequest rejected = data.evaluation(applicant, RequestStatus.RECEIVED);
        PositionRequest linked = data.positionRequest(applicant, PositionRequestStatus.DRAFT, rejected);
        markRejected("academic_request", rejected.getId());

        runV24();

        assertThat(jdbc.queryForList("SELECT linked_evaluation_id FROM position_request WHERE id = ?",
                linked.getId())).singleElement()
                .satisfies(row -> assertThat(row.get("linked_evaluation_id")).isNull());
    }

    @Test
    @DisplayName("คำร้องที่เคยถูกย้อนออกจาก REJECTED — ตัวคำร้องอยู่ แต่แถวประวัติที่มี REJECTED ถูกลบ")
    void historyRowsNamingRejectedAreRemovedFromSurvivingRequests() throws Exception {
        AcademicRequest kept = data.evaluation(applicant, RequestStatus.RECEIVED);
        academicService.updateStatus(kept.getId(), RequestStatus.SUB_COMMITTEE_APPOINTED, officer, "ขั้นแรก", false);
        academicService.updateStatus(kept.getId(), RequestStatus.MEETING_SCHEDULED, officer, "ขั้นสอง", false);
        jdbc.update("UPDATE request_status_history SET old_status = 'REJECTED' WHERE request_id = ? AND note = ?",
                kept.getId(), "ขั้นสอง");

        runV24();

        assertThat(count("SELECT COUNT(*) FROM academic_request WHERE id = ?", kept.getId())).isOne();
        assertThat(count("SELECT COUNT(*) FROM request_status_history WHERE request_id = ?"
                + " AND (old_status = 'REJECTED' OR new_status = 'REJECTED')", kept.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM request_status_history WHERE request_id = ? AND note = ?",
                kept.getId(), "ขั้นแรก")).isOne();
    }
}
