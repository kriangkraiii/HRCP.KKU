package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.AcademicDocumentEditLog;
import com.ecom.academic.model.AcademicDocumentEditLog.EditAction;
import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.repository.AcademicDocumentEditLogRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * The edit-history tab: autosave must not flood it, and non-form changes
 * (attachments, uploads) must show up in it.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_edit_log",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
@DisplayName("ประวัติการแก้ไขเอกสาร")
class DocumentEditLogTest {

    @Autowired
    private AcademicRequestService requestService;

    @Autowired
    private AcademicDocumentEditLogRepository editLogRepository;

    @Autowired
    private UserRepository userRepository;

    private UserDtls applicant;
    private UserDtls admin;
    private AcademicRequest request;

    @BeforeEach
    void setUp() {
        editLogRepository.deleteAll();
        applicant = createUser("edit-log-applicant-" + java.util.UUID.randomUUID() + "@test.com", "ROLE_USER");
        admin = createUser("edit-log-admin-" + java.util.UUID.randomUUID() + "@test.com", "ROLE_ADMIN");
        request = requestService.createDraftRequest(applicant);
    }

    private UserDtls createUser(String email, String role) {
        UserDtls u = new UserDtls();
        u.setEmail(email);
        u.setName("ผู้ใช้ " + email);
        u.setPassword("{noop}x");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setEmailNotificationEnabled(false);
        return userRepository.save(u);
    }

    private List<AcademicDocumentEditLog> history() {
        return requestService.getEditHistory(request.getId());
    }

    @Test
    @DisplayName("บันทึกร่างอัตโนมัติติดกันหลายครั้ง รวมเป็นแถวเดียว")
    void consecutiveAutosavesShareOneRow() {
        for (int i = 0; i < 5; i++) {
            requestService.logDocumentEdit(request, 1, null, applicant, EditAction.DRAFT_SAVED);
        }

        assertThat(history()).hasSize(1);
    }

    @Test
    @DisplayName("บันทึกร่างของคนละเอกสาร คนละคน หรือห่างเกิน 10 นาที แยกแถว")
    void autosavesSplitByDocumentUserAndTime() {
        requestService.logDocumentEdit(request, 1, null, applicant, EditAction.DRAFT_SAVED);
        requestService.logDocumentEdit(request, 2, null, applicant, EditAction.DRAFT_SAVED);
        requestService.logDocumentEdit(request, 2, null, admin, EditAction.DRAFT_SAVED);
        assertThat(history()).hasSize(3);

        AcademicDocumentEditLog adminRow = history().stream()
                .filter(l -> l.getEditedBy().getId().equals(admin.getId()))
                .findFirst().orElseThrow();
        adminRow.setEditedAt(LocalDateTime.now().minusMinutes(11));
        editLogRepository.save(adminRow);
        requestService.logDocumentEdit(request, 2, null, admin, EditAction.DRAFT_SAVED);

        assertThat(history()).hasSize(4);
    }

    @Test
    @DisplayName("การบันทึกจริงไม่ถูกรวมกับบันทึกร่าง")
    void explicitSavesAreNeverMerged() {
        requestService.logDocumentEdit(request, 1, null, applicant, EditAction.DRAFT_SAVED);
        requestService.logDocumentEdit(request, 1, null, applicant, EditAction.UPDATED);
        requestService.logDocumentEdit(request, 1, null, applicant, EditAction.DRAFT_SAVED);

        assertThat(history()).extracting(AcademicDocumentEditLog::getAction)
                .containsExactly(EditAction.DRAFT_SAVED, EditAction.UPDATED, EditAction.DRAFT_SAVED);
    }

    @Test
    @DisplayName("เอกสารที่ส่งกลับให้แก้ แสดงบนแดชบอร์ดพร้อมเหตุผล แม้สถานะยังเป็นรับคำร้อง")
    void sentBackDocumentsAreReportedWhileStatusIsUnchanged() {
        requestService.saveDocument(request, 1, "{}", null, null, 0);
        requestService.saveDocument(request, 2, "{}", null, null, 0);
        request.setCurrentStatus(com.ecom.academic.model.RequestStatus.RECEIVED);
        request = requestService.save(request);

        assertThat(requestService.sentBackDocuments(request)).isEmpty();

        requestService.openDocumentForRevision(request.getId(), 2, "แก้ชื่อรายวิชา");

        assertThat(requestService.sentBackDocuments(request))
                .containsExactly(java.util.Map.entry(2, "แก้ชื่อรายวิชา"));
    }

    @Test
    @DisplayName("แบบร่างไม่นับว่าถูกส่งกลับ")
    void draftsAreNeverSentBack() {
        requestService.saveDocument(request, 1, "{}", null, null, 0);
        requestService.openDocumentForRevision(request.getId(), 1, null);

        assertThat(requestService.sentBackDocuments(request)).isEmpty();
    }

    @Test
    @DisplayName("การแนบ/ลบไฟล์ถูกบันทึกพร้อมชื่อไฟล์")
    void attachmentChangesAreLoggedWithTheFileName() {
        requestService.logDocumentChange(request, 2, "แนบไฟล์: ผลงาน.pdf", applicant, EditAction.ATTACHMENT_ADDED);
        requestService.logDocumentChange(request, AcademicRequestService.REQUEST_FILES_DOC_TYPE,
                "ลบ: ผล.pdf", admin, EditAction.ATTACHMENT_DELETED);

        List<AcademicDocumentEditLog> rows = history();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AcademicDocumentEditLog::getDocumentLabel)
                .anySatisfy(l -> assertThat(l).endsWith("— แนบไฟล์: ผลงาน.pdf"))
                .contains("ลบ: ผล.pdf");
    }
}
