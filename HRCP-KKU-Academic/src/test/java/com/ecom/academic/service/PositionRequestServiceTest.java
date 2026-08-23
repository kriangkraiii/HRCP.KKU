package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.PositionDocument;
import com.ecom.academic.model.PositionRequest;
import com.ecom.academic.model.PositionRequestStatus;
import com.ecom.academic.model.PositionStatusHistory;
import com.ecom.academic.repository.PositionDocumentRepository;
import com.ecom.academic.repository.PositionRequestRepository;
import com.ecom.academic.repository.PositionStatusHistoryRepository;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Tests for PositionRequestService — Phase 2 Academic Position Request System
 *
 * Covers: request lifecycle, document CRUD, status transitions, eligibility
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_pos",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.mail.host=localhost",
        "spring.mail.port=25"
})
public class PositionRequestServiceTest {

    @Autowired
    private PositionRequestService positionService;

    @Autowired
    private PositionRequestRepository requestRepository;

    @Autowired
    private PositionDocumentRepository documentRepository;

    @Autowired
    private PositionStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    private UserDtls testUser;
    private UserDtls testAdmin;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        documentRepository.deleteAll();
        requestRepository.deleteAll();

        testUser = createUser("testuser" + java.util.UUID.randomUUID() + "@test.com", "ROLE_USER");
        testAdmin = createUser("admin" + java.util.UUID.randomUUID() + "@test.com", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        statusHistoryRepository.deleteAll();
        documentRepository.deleteAll();
        requestRepository.deleteAll();
    }

    // ================== Request Lifecycle Tests ==================

    @Nested
    class RequestLifecycle {

        @Test
        void createDraftRequest_Success() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            assertThat(request).isNotNull();
            assertThat(request.getId()).isNotNull();
            assertThat(request.getRequestCode()).startsWith("KKU-POS-");
            assertThat(request.getCurrentStatus()).isEqualTo(PositionRequestStatus.DRAFT);
            assertThat(request.getApplicant().getId()).isEqualTo(testUser.getId());
        }

        @Test
        void createDraftRequest_GeneratesRequestCode() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            String code = request.getRequestCode();
            assertThat(code).isNotNull();
            assertThat(code).matches("KKU-POS-\\d{4}-\\d{4}");
        }

        @Test
        void findById_ReturnsRequest_WhenExists() {
            PositionRequest created = positionService.createDraftRequest(testUser, null);
            Optional<PositionRequest> found = positionService.findById(created.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(created.getId());
        }

        @Test
        void findById_ReturnsEmpty_WhenNotExists() {
            Optional<PositionRequest> found = positionService.findById(99999L);
            assertThat(found).isEmpty();
        }

        @Test
        void findByApplicant_ReturnsRequests() {
            positionService.createDraftRequest(testUser, null);
            positionService.createDraftRequest(testUser, null);

            List<PositionRequest> requests = positionService.findByApplicant(testUser.getId());
            assertThat(requests).hasSize(2);
        }

        @Test
        void findByApplicant_ReturnsEmpty_WhenNoRequests() {
            List<PositionRequest> requests = positionService.findByApplicant(testUser.getId());
            assertThat(requests).isEmpty();
        }

        @Test
        void findAll_ReturnsAllRequests() {
            positionService.createDraftRequest(testUser, null);
            positionService.createDraftRequest(testAdmin, null);

            List<PositionRequest> all = positionService.findAll();
            assertThat(all).hasSize(2);
        }

        @Test
        void findDraftByApplicant_ReturnsDraft_WhenExists() {
            positionService.createDraftRequest(testUser, null);

            Optional<PositionRequest> draft = positionService.findDraftByApplicant(testUser.getId());
            assertThat(draft).isPresent();
            assertThat(draft.get().getCurrentStatus()).isEqualTo(PositionRequestStatus.DRAFT);
        }
    }

    // ================== Status Transition Tests ==================

    @Nested
    class StatusTransitions {

        @Test
        void submitRequest_ChangesStatusToSubmitted() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            PositionRequest submitted = positionService.submitRequest(request);

            assertThat(submitted.getCurrentStatus()).isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
            assertThat(submitted.getSubmissionDate()).isNotNull();
        }

        @Test
        void submitRequest_CreatesStatusHistory() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);

            List<PositionStatusHistory> history = positionService.getStatusHistory(request.getId());
            assertThat(history).isNotEmpty();
            assertThat(history.get(0).getNewStatus()).isEqualTo(PositionRequestStatus.DOCUMENT_RECEIVED);
            assertThat(history.get(0).getOldStatus()).isEqualTo(PositionRequestStatus.DRAFT);
            assertThat(history.get(0).getNote()).isEqualTo("ส่งคำร้องเข้าระบบ");
        }

        @Test
        void updateStatus_ChangesStatus() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);

            PositionRequest updated = positionService.updateStatus(
                    request.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, testAdmin, "กำลังตรวจสอบ");

            assertThat(updated.getCurrentStatus()).isEqualTo(PositionRequestStatus.DOCUMENT_VERIFICATION);
        }

        @Test
        void updateStatus_RecordsHistory() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            positionService.updateStatus(request.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, testAdmin,
                    "ตรวจสอบ");
            positionService.updateStatus(request.getId(), PositionRequestStatus.SCREENING_APPROVED, testAdmin,
                    "อนุมัติ");

            List<PositionStatusHistory> history = positionService.getStatusHistory(request.getId());
            assertThat(history).hasSize(3); // submit + 2 updates
            assertThat(history).extracting(PositionStatusHistory::getNewStatus)
                    .contains(PositionRequestStatus.DOCUMENT_RECEIVED,
                            PositionRequestStatus.DOCUMENT_VERIFICATION,
                            PositionRequestStatus.SCREENING_APPROVED);
        }

        @Test
        void updateStatus_RecordsChangedByUser() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            positionService.updateStatus(request.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION,
                    testAdmin, "โดยแอดมิน");

            // getStatusHistory returns most-recent-first
            List<PositionStatusHistory> history = positionService.getStatusHistory(request.getId());
            PositionStatusHistory latestEntry = history.get(0);
            assertThat(latestEntry.getChangedBy()).isNotNull();
            assertThat(latestEntry.getChangedBy().getId()).isEqualTo(testAdmin.getId());
        }

        @Test
        void updateStatus_ThrowsException_WhenRequestNotFound() {
            assertThatThrownBy(
                    () -> positionService.updateStatus(99999L, PositionRequestStatus.SCREENING_APPROVED, testAdmin,
                            "note"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        void updateStatus_FullLifecycle_DraftToSentToHR() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            positionService.updateStatus(request.getId(), PositionRequestStatus.DOCUMENT_VERIFICATION, testAdmin, null);
            positionService.updateStatus(request.getId(), PositionRequestStatus.SCREENING_APPROVED, testAdmin, null);
            PositionRequest completed = positionService.updateStatus(
                    request.getId(), PositionRequestStatus.SENT_TO_HR, testAdmin, "ส่งออก");

            assertThat(completed.getCurrentStatus()).isEqualTo(PositionRequestStatus.SENT_TO_HR);

            List<PositionStatusHistory> history = positionService.getStatusHistory(request.getId());
            assertThat(history).hasSize(4); // submit + 3 updates
        }
    }

    // ================== Active Request Tests ==================

    @Nested
    class ActiveRequestChecks {

        @Test
        void hasActiveRequest_ReturnsFalse_WhenNoRequests() {
            assertThat(positionService.hasActiveRequest(testUser.getId())).isFalse();
        }

        @Test
        void hasActiveRequest_ReturnsTrue_WhenDraftExists() {
            positionService.createDraftRequest(testUser, null);
            assertThat(positionService.hasActiveRequest(testUser.getId())).isTrue();
        }

        @Test
        void hasActiveRequest_ReturnsTrue_WhenSubmitted() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            assertThat(positionService.hasActiveRequest(testUser.getId())).isTrue();
        }

        @Test
        void hasActiveRequest_ReturnsFalse_WhenSentToHR() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            positionService.updateStatus(request.getId(), PositionRequestStatus.SENT_TO_HR, testAdmin, null);
            assertThat(positionService.hasActiveRequest(testUser.getId())).isFalse();
        }

        @Test
        void hasActiveRequest_ReturnsFalse_WhenCompleted() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);
            positionService.submitRequest(request);
            positionService.updateStatus(request.getId(), PositionRequestStatus.SENT_TO_HR, testAdmin, null);
            assertThat(positionService.hasActiveRequest(testUser.getId())).isFalse();
        }
    }

    // ================== Document CRUD Tests ==================

    @Nested
    class DocumentCRUD {

        @Test
        void saveDocument_CreatesNewDocument() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            PositionDocument doc = positionService.saveDocument(
                    request, 1, "{\"key\":\"value\"}", null,
                    "แบบ ก.พ.ว. มข. 03", null, "APPLICANT");

            assertThat(doc).isNotNull();
            assertThat(doc.getId()).isNotNull();
            assertThat(doc.getDocumentType()).isEqualTo(1);
            assertThat(doc.getJsonData()).isEqualTo("{\"key\":\"value\"}");
            assertThat(doc.getFilledBy()).isEqualTo("APPLICANT");
            assertThat(doc.getIsDraft()).isFalse();
        }

        @Test
        void saveDocument_UpdatesExistingDocument() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            positionService.saveDocument(request, 1, "{\"v\":1}", null, "doc1", null, "APPLICANT");
            PositionDocument updated = positionService.saveDocument(
                    request, 1, "{\"v\":2}", null, "doc1", null, "APPLICANT");

            assertThat(updated.getJsonData()).isEqualTo("{\"v\":2}");
            List<PositionDocument> docs = positionService.getDocumentsByType(request.getId(), 1);
            assertThat(docs).hasSize(1); // should update, not create new
        }

        @Test
        void saveDraft_CreatesDraftDocument() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            PositionDocument draft = positionService.saveDraft(
                    request, 2, "{\"draft\":true}", "doc2", "APPLICANT");

            assertThat(draft).isNotNull();
            assertThat(draft.getIsDraft()).isTrue();
            assertThat(draft.getDocumentType()).isEqualTo(2);
        }

        @Test
        void saveDraft_UpdatesExistingDraft() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            positionService.saveDraft(request, 3, "{\"d\":1}", "doc3", "APPLICANT");
            PositionDocument updated = positionService.saveDraft(
                    request, 3, "{\"d\":2}", "doc3", "APPLICANT");

            assertThat(updated.getJsonData()).isEqualTo("{\"d\":2}");
        }

        @Test
        void getDocuments_ReturnsAllDocsForRequest() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            positionService.saveDocument(request, 1, "{}", null, "doc1", null, "APPLICANT");
            positionService.saveDocument(request, 2, "{}", null, "doc2", null, "APPLICANT");
            positionService.saveDocument(request, 3, "{}", null, "doc3", null, "APPLICANT");

            List<PositionDocument> docs = positionService.getDocuments(request.getId());
            assertThat(docs).hasSize(3);
        }

        @Test
        void getDocumentsByType_ReturnsCorrectDocs() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            positionService.saveDocument(request, 1, "{}", null, "doc1", null, "APPLICANT");
            positionService.saveDocument(request, 2, "{}", null, "doc2", null, "APPLICANT");

            List<PositionDocument> docs = positionService.getDocumentsByType(request.getId(), 1);
            assertThat(docs).hasSize(1);
            assertThat(docs.get(0).getDocumentType()).isEqualTo(1);
        }

        @Test
        void getCompletedDocTypes_ReturnsOnlyNonDraftDocTypes() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            positionService.saveDocument(request, 1, "{}", null, "doc1", null, "APPLICANT");
            positionService.saveDocument(request, 2, "{}", null, "doc2", null, "APPLICANT");
            positionService.saveDraft(request, 3, "{}", "doc3", "APPLICANT"); // draft

            List<Integer> completed = positionService.getCompletedDocTypes(request.getId());
            assertThat(completed).containsExactlyInAnyOrder(1, 2); // draft excluded
        }

        @Test
        void saveDocument_AdminFillsDoc5() {
            PositionRequest request = positionService.createDraftRequest(testUser, null);

            PositionDocument doc = positionService.saveDocument(
                    request, 5, "{\"eval\":\"approved\"}", null,
                    "แบบประเมิน", null, "ADMIN");

            assertThat(doc.getFilledBy()).isEqualTo("ADMIN");
            assertThat(doc.getDocumentType()).isEqualTo(5);
        }
    }

    // ================== Doc Labels Tests ==================

    @Nested
    class DocLabels {

        @Test
        void getDocLabels_Returns9Documents() {
            assertThat(positionService.getDocLabels()).hasSize(9);
        }

        @Test
        void getApplicantDocLabels_Returns6Documents() {
            assertThat(positionService.getApplicantDocLabels()).hasSize(6);
            assertThat(positionService.getApplicantDocLabels().keySet())
                    .containsExactlyInAnyOrder(1, 2, 3, 4, 6, 9);
        }

        @Test
        void getAdminDocLabels_Returns9Documents() {
            assertThat(positionService.getAdminDocLabels()).hasSize(9);
        }

        @Test
        void getDocLabel_ReturnsCorrectLabel() {
            assertThat(positionService.getDocLabel(1)).contains("ก.พ.ว.");
            assertThat(positionService.getDocLabel(5)).contains("ผู้บังคับบัญชา");
        }

        @Test
        void getDocLabel_ReturnsFallback_WhenUnknown() {
            assertThat(positionService.getDocLabel(99)).isEqualTo("เอกสารที่ 99");
        }
    }

    // ================== PositionRequestStatus Enum Tests ==================

    @Nested
    class StatusTests {

        @Test
        void status_DraftIsEditable() {
            assertThat(PositionRequestStatus.DRAFT.isEditable()).isTrue();
        }

        @Test
        void status_DocumentReceivedIsNotEditable() {
            assertThat(PositionRequestStatus.DOCUMENT_RECEIVED.isEditable()).isFalse();
        }

        @Test
        void status_SentToHRIsTerminal() {
            assertThat(PositionRequestStatus.SENT_TO_HR.isTerminal()).isTrue();
        }

        @Test
        void status_RevisionRequestedIsNotTerminal() {
            assertThat(PositionRequestStatus.REVISION_REQUESTED.isTerminal()).isFalse();
        }

        @Test
        void status_DocumentVerificationIsNotTerminal() {
            assertThat(PositionRequestStatus.DOCUMENT_VERIFICATION.isTerminal()).isFalse();
        }

        @Test
        void status_AllHaveThaiLabels() {
            for (PositionRequestStatus s : PositionRequestStatus.values()) {
                assertThat(s.getThaiLabel()).isNotNull().isNotEmpty();
            }
        }

        @Test
        void status_AllHaveIcons() {
            for (PositionRequestStatus s : PositionRequestStatus.values()) {
                assertThat(s.getIcon()).isNotNull().isNotEmpty();
            }
        }

        @Test
        void status_AllHaveBadgeClasses() {
            for (PositionRequestStatus s : PositionRequestStatus.values()) {
                assertThat(s.getBadgeClass()).isNotNull().isNotEmpty();
            }
        }
    }

    // ================== Helper Methods ==================

    private UserDtls createUser(String email, String role) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test " + role);
        user.setEmail(email);
        user.setMobileNumber("0812345678");
        user.setPassword("password");
        user.setRole(role);
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        return userRepository.save(user);
    }
}
