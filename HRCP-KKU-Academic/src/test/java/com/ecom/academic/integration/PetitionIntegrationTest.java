package com.ecom.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.Petition;
import com.ecom.academic.model.PetitionStatus;
import com.ecom.academic.model.StatusType;
import com.ecom.academic.repository.PetitionRepository;
import com.ecom.academic.repository.PetitionStatusRepository;
import com.ecom.academic.service.PetitionService;
import com.ecom.exception.ActivePetitionExistsException;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Integration tests for Petition Status Management System
 * 
 * Tests end-to-end flows:
 * - Create petition → Add statuses → Verify history
 * - User cannot submit duplicate petition when active petition exists
 * - User can submit new petition after previous petition is completed
 * - Status history displays correctly in chronological order
 * 
 * **Validates: Requirements 1.2, 1.3, 3.4, 5.1**
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class PetitionIntegrationTest {

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private PetitionStatusRepository petitionStatusRepository;

    @Autowired
    private UserRepository userRepository;

    private UserDtls testUser;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();

        // Create test user
        testUser = new UserDtls();
        testUser.setTitle("นาย");
        testUser.setName("สมชาย ใจดี");
        testUser.setEmail("somchai" + java.util.UUID.randomUUID() + "@test.com");
        testUser.setMobileNumber("0812345678");
        testUser.setPassword("password");
        testUser.setRole("ROLE_USER");
        testUser.setIsEnable(true);
        testUser.setAccountNonLocked(true);
        testUser = userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Integration Test: End-to-end flow - Create petition → Add statuses → Verify history
     * 
     * This test validates the complete workflow from petition creation through
     * multiple status updates and verifies the status history is maintained correctly.
     * 
     * **Validates: Requirements 1.2, 1.3, 3.4, 5.1**
     */
    @Test
    void endToEndFlow_CreatePetition_AddStatuses_VerifyHistory() throws InterruptedException {
        // Step 1: Create a new petition
        String title = "คำร้องขอทุนการศึกษา";
        String description = "ขอทุนการศึกษาสำหรับภาคเรียนที่ 1/2567";
        
        Petition createdPetition = petitionService.createPetition(testUser.getId(), title, description);
        
        // Verify petition was created successfully
        assertThat(createdPetition).isNotNull();
        assertThat(createdPetition.getId()).isNotNull();
        assertThat(createdPetition.getTitle()).isEqualTo(title);
        assertThat(createdPetition.getDescription()).isEqualTo(description);
        assertThat(createdPetition.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(createdPetition.getCreatedAt()).isNotNull();
        
        // Verify initial RECEIVED status was added automatically
        List<PetitionStatus> initialHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(createdPetition.getId());
        assertThat(initialHistory).hasSize(1);
        assertThat(initialHistory.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        assertThat(initialHistory.get(0).getNote()).isEqualTo("รับคำร้องเข้าระบบ");
        
        Thread.sleep(10); // Ensure different timestamps
        
        // Step 2: Add COMMITTEE_ASSIGNED status
        PetitionStatus committeeStatus = petitionService.addStatus(
            createdPetition.getId(),
            StatusType.COMMITTEE_ASSIGNED,
            "แต่งตั้งอนุกรรมการพิจารณาทุนการศึกษา"
        );
        
        assertThat(committeeStatus).isNotNull();
        assertThat(committeeStatus.getStatusType()).isEqualTo(StatusType.COMMITTEE_ASSIGNED);
        assertThat(committeeStatus.getCreatedAt()).isNotNull();
        
        Thread.sleep(10); // Ensure different timestamps
        
        // Step 3: Add MEETING_SCHEDULED status
        PetitionStatus meetingStatus = petitionService.addStatus(
            createdPetition.getId(),
            StatusType.MEETING_SCHEDULED,
            "นัดประชุมวันที่ 15 มีนาคม 2567"
        );
        
        assertThat(meetingStatus).isNotNull();
        assertThat(meetingStatus.getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        assertThat(meetingStatus.getCreatedAt()).isNotNull();
        
        Thread.sleep(10); // Ensure different timestamps
        
        // Step 4: Add RESULT_APPROVED status
        PetitionStatus approvedStatus = petitionService.addStatus(
            createdPetition.getId(),
            StatusType.RESULT_APPROVED,
            "อนุมัติทุนการศึกษา จำนวน 10,000 บาท"
        );
        
        assertThat(approvedStatus).isNotNull();
        assertThat(approvedStatus.getStatusType()).isEqualTo(StatusType.RESULT_APPROVED);
        assertThat(approvedStatus.getCreatedAt()).isNotNull();
        
        Thread.sleep(10); // Ensure different timestamps
        
        // Step 5: Add COMPLETED status
        PetitionStatus completedStatus = petitionService.addStatus(
            createdPetition.getId(),
            StatusType.COMPLETED,
            "ดำเนินการเสร็จสิ้น"
        );
        
        assertThat(completedStatus).isNotNull();
        assertThat(completedStatus.getStatusType()).isEqualTo(StatusType.COMPLETED);
        assertThat(completedStatus.getCreatedAt()).isNotNull();
        
        // Step 6: Verify complete status history
        List<PetitionStatus> fullHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(createdPetition.getId());
        
        // Verify all 5 statuses are present
        assertThat(fullHistory).hasSize(5);
        
        // Verify statuses are in correct chronological order
        assertThat(fullHistory.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        assertThat(fullHistory.get(1).getStatusType()).isEqualTo(StatusType.COMMITTEE_ASSIGNED);
        assertThat(fullHistory.get(2).getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        assertThat(fullHistory.get(3).getStatusType()).isEqualTo(StatusType.RESULT_APPROVED);
        assertThat(fullHistory.get(4).getStatusType()).isEqualTo(StatusType.COMPLETED);
        
        // Verify timestamps are in ascending order
        for (int i = 0; i < fullHistory.size() - 1; i++) {
            LocalDateTime currentTime = fullHistory.get(i).getCreatedAt();
            LocalDateTime nextTime = fullHistory.get(i + 1).getCreatedAt();
            assertThat(currentTime).isBefore(nextTime);
        }
        
        // Verify notes are preserved
        assertThat(fullHistory.get(0).getNote()).isEqualTo("รับคำร้องเข้าระบบ");
        assertThat(fullHistory.get(1).getNote()).isEqualTo("แต่งตั้งอนุกรรมการพิจารณาทุนการศึกษา");
        assertThat(fullHistory.get(2).getNote()).isEqualTo("นัดประชุมวันที่ 15 มีนาคม 2567");
        assertThat(fullHistory.get(3).getNote()).isEqualTo("อนุมัติทุนการศึกษา จำนวน 10,000 บาท");
        assertThat(fullHistory.get(4).getNote()).isEqualTo("ดำเนินการเสร็จสิ้น");
        
        // Step 7: Verify petition can be retrieved with history
        Petition retrievedPetition = petitionService.getPetitionWithHistory(createdPetition.getId());
        assertThat(retrievedPetition).isNotNull();
        assertThat(retrievedPetition.getId()).isEqualTo(createdPetition.getId());
        assertThat(retrievedPetition.getTitle()).isEqualTo(title);
    }

    /**
     * Integration Test: User cannot submit duplicate petition when active petition exists
     * 
     * This test validates that the system prevents users from submitting multiple
     * petitions simultaneously when they have an active petition in progress.
     * 
     * **Validates: Requirements 1.2**
     */
    @Test
    void userCannotSubmitDuplicatePetition_WhenActivePetitionExists() {
        // Step 1: User creates first petition
        Petition firstPetition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องขอเปลี่ยนแปลงข้อมูล",
            "ขอเปลี่ยนชื่อในระบบ"
        );
        
        assertThat(firstPetition).isNotNull();
        assertThat(firstPetition.getId()).isNotNull();
        
        // Verify petition is active (has RECEIVED status)
        List<PetitionStatus> statusHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(firstPetition.getId());
        assertThat(statusHistory).hasSize(1);
        assertThat(statusHistory.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        
        // Step 2: Verify user cannot submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(canSubmit).isFalse();
        
        // Step 3: Attempt to create second petition should throw exception
        org.junit.jupiter.api.Assertions.assertThrows(
            ActivePetitionExistsException.class,
            () -> petitionService.createPetition(
                testUser.getId(),
                "คำร้องขอลาพักการศึกษา",
                "ขอลาพักการศึกษา 1 ภาคเรียน"
            ),
            "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่"
        );
        
        // Step 4: Verify only one petition exists in database
        List<Petition> allPetitions = petitionService.getUserPetitions(testUser.getId());
        assertThat(allPetitions).hasSize(1);
        assertThat(allPetitions.get(0).getId()).isEqualTo(firstPetition.getId());
        
        // Step 5: Add more statuses to keep petition active
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.COMMITTEE_ASSIGNED,
            "แต่งตั้งอนุกรรมการ"
        );
        
        // Step 6: Verify user still cannot submit new petition
        canSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(canSubmit).isFalse();
        
        // Step 7: Attempt to create petition again should still fail
        org.junit.jupiter.api.Assertions.assertThrows(
            ActivePetitionExistsException.class,
            () -> petitionService.createPetition(
                testUser.getId(),
                "คำร้องอื่น",
                "รายละเอียด"
            )
        );
    }

    /**
     * Integration Test: User can submit new petition after previous petition is completed
     * 
     * This test validates that users can submit new petitions once their previous
     * petition reaches a terminal status (COMPLETED or REJECTED).
     * 
     * **Validates: Requirements 1.3**
     */
    @Test
    void userCanSubmitNewPetition_AfterPreviousPetitionIsCompleted() throws InterruptedException {
        // Step 1: User creates first petition
        Petition firstPetition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องขอทุนการศึกษา ครั้งที่ 1",
            "ขอทุนสำหรับภาคเรียนที่ 1"
        );
        
        assertThat(firstPetition).isNotNull();
        
        // Step 2: Process petition through workflow
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.COMMITTEE_ASSIGNED,
            "แต่งตั้งอนุกรรมการ"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.MEETING_SCHEDULED,
            "นัดประชุม"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.RESULT_APPROVED,
            "อนุมัติ"
        );
        
        Thread.sleep(10);
        
        // Step 3: Complete the petition
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.COMPLETED,
            "เสร็จสิ้น"
        );
        
        // Step 4: Verify user can now submit new petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(canSubmit).isTrue();
        
        // Step 5: Create second petition successfully
        Petition secondPetition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องขอทุนการศึกษา ครั้งที่ 2",
            "ขอทุนสำหรับภาคเรียนที่ 2"
        );
        
        assertThat(secondPetition).isNotNull();
        assertThat(secondPetition.getId()).isNotEqualTo(firstPetition.getId());
        assertThat(secondPetition.getTitle()).isEqualTo("คำร้องขอทุนการศึกษา ครั้งที่ 2");
        
        // Step 6: Verify both petitions exist in database
        List<Petition> allPetitions = petitionService.getUserPetitions(testUser.getId());
        assertThat(allPetitions).hasSize(2);
        
        // Step 7: Verify first petition is completed
        Petition retrievedFirstPetition = petitionService.getPetitionWithHistory(firstPetition.getId());
        List<PetitionStatus> firstPetitionHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(retrievedFirstPetition.getId());
        assertThat(firstPetitionHistory.get(firstPetitionHistory.size() - 1).getStatusType())
            .isEqualTo(StatusType.COMPLETED);
        
        // Step 8: Verify second petition has initial RECEIVED status
        List<PetitionStatus> secondPetitionHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(secondPetition.getId());
        assertThat(secondPetitionHistory).hasSize(1);
        assertThat(secondPetitionHistory.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        
        // Step 9: Verify user cannot submit third petition (second is active)
        canSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(canSubmit).isFalse();
    }

    /**
     * Integration Test: User can submit new petition after previous petition is rejected
     * 
     * This test validates that users can submit new petitions after their previous
     * petition was rejected.
     * 
     * **Validates: Requirements 1.3**
     */
    @Test
    void userCanSubmitNewPetition_AfterPreviousPetitionIsRejected() {
        // Step 1: User creates first petition
        Petition firstPetition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องขอเปลี่ยนสาขา",
            "ขอเปลี่ยนจากสาขา A ไปสาขา B"
        );
        
        assertThat(firstPetition).isNotNull();
        
        // Step 2: Reject the petition
        petitionService.addStatus(
            firstPetition.getId(),
            StatusType.REJECTED,
            "ไม่ผ่านเกณฑ์การพิจารณา"
        );
        
        // Step 3: Verify user can submit new petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(canSubmit).isTrue();
        
        // Step 4: Create second petition successfully
        Petition secondPetition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องขอเปลี่ยนสาขา (แก้ไข)",
            "ขอเปลี่ยนจากสาขา A ไปสาขา C พร้อมเอกสารเพิ่มเติม"
        );
        
        assertThat(secondPetition).isNotNull();
        assertThat(secondPetition.getId()).isNotEqualTo(firstPetition.getId());
        
        // Step 5: Verify both petitions exist
        List<Petition> allPetitions = petitionService.getUserPetitions(testUser.getId());
        assertThat(allPetitions).hasSize(2);
    }

    /**
     * Integration Test: Status history displays correctly in chronological order
     * 
     * This test validates that status history is always maintained in chronological
     * order regardless of how statuses are added or retrieved.
     * 
     * **Validates: Requirements 3.4, 5.1**
     */
    @Test
    void statusHistoryDisplaysCorrectly_InChronologicalOrder() throws InterruptedException {
        // Step 1: Create petition
        Petition petition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องทดสอบลำดับสถานะ",
            "ทดสอบการแสดงผลสถานะตามลำดับเวลา"
        );
        
        // Record creation times for verification
        LocalDateTime startTime = LocalDateTime.now();
        
        Thread.sleep(10);
        
        // Step 2: Add multiple statuses with delays to ensure different timestamps
        petitionService.addStatus(
            petition.getId(),
            StatusType.COMMITTEE_ASSIGNED,
            "สถานะที่ 2"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            petition.getId(),
            StatusType.MEETING_SCHEDULED,
            "สถานะที่ 3"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            petition.getId(),
            StatusType.RESULT_REVISION,
            "สถานะที่ 4"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            petition.getId(),
            StatusType.MEETING_SCHEDULED,
            "สถานะที่ 5 - นัดประชุมครั้งที่ 2"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            petition.getId(),
            StatusType.RESULT_APPROVED,
            "สถานะที่ 6"
        );
        
        Thread.sleep(10);
        
        petitionService.addStatus(
            petition.getId(),
            StatusType.COMPLETED,
            "สถานะที่ 7"
        );
        
        LocalDateTime endTime = LocalDateTime.now();
        
        // Step 3: Retrieve status history
        List<PetitionStatus> statusHistory = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(petition.getId());
        
        // Step 4: Verify correct number of statuses (1 initial + 6 added = 7 total)
        assertThat(statusHistory).hasSize(7);
        
        // Step 5: Verify statuses are in chronological order
        assertThat(statusHistory.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        assertThat(statusHistory.get(1).getStatusType()).isEqualTo(StatusType.COMMITTEE_ASSIGNED);
        assertThat(statusHistory.get(2).getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        assertThat(statusHistory.get(3).getStatusType()).isEqualTo(StatusType.RESULT_REVISION);
        assertThat(statusHistory.get(4).getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        assertThat(statusHistory.get(5).getStatusType()).isEqualTo(StatusType.RESULT_APPROVED);
        assertThat(statusHistory.get(6).getStatusType()).isEqualTo(StatusType.COMPLETED);
        
        // Step 6: Verify timestamps are strictly increasing
        for (int i = 0; i < statusHistory.size() - 1; i++) {
            LocalDateTime currentTime = statusHistory.get(i).getCreatedAt();
            LocalDateTime nextTime = statusHistory.get(i + 1).getCreatedAt();
            
            assertThat(currentTime).isBefore(nextTime);
            assertThat(currentTime).isAfter(startTime.minusSeconds(1));
            assertThat(nextTime).isBefore(endTime.plusSeconds(1));
        }
        
        // Step 7: Verify notes are preserved in order
        assertThat(statusHistory.get(0).getNote()).isEqualTo("รับคำร้องเข้าระบบ");
        assertThat(statusHistory.get(1).getNote()).isEqualTo("สถานะที่ 2");
        assertThat(statusHistory.get(2).getNote()).isEqualTo("สถานะที่ 3");
        assertThat(statusHistory.get(3).getNote()).isEqualTo("สถานะที่ 4");
        assertThat(statusHistory.get(4).getNote()).isEqualTo("สถานะที่ 5 - นัดประชุมครั้งที่ 2");
        assertThat(statusHistory.get(5).getNote()).isEqualTo("สถานะที่ 6");
        assertThat(statusHistory.get(6).getNote()).isEqualTo("สถานะที่ 7");
        
        // Step 8: Verify history through service method
        Petition retrievedPetition = petitionService.getPetitionWithHistory(petition.getId());
        assertThat(retrievedPetition).isNotNull();
        
        // Step 9: Verify getUserPetitions also returns correct history
        List<Petition> userPetitions = petitionService.getUserPetitions(testUser.getId());
        assertThat(userPetitions).hasSize(1);
        
        Petition petitionFromList = userPetitions.get(0);
        List<PetitionStatus> historyFromList = petitionStatusRepository
            .findByPetitionIdOrderByCreatedAtAsc(petitionFromList.getId());
        assertThat(historyFromList).hasSize(7);
        
        // Verify order is maintained
        for (int i = 0; i < historyFromList.size() - 1; i++) {
            assertThat(historyFromList.get(i).getCreatedAt())
                .isBefore(historyFromList.get(i + 1).getCreatedAt());
        }
    }

    /**
     * Integration Test: Multiple users can have independent petition workflows
     * 
     * This test validates that the system correctly handles multiple users
     * with their own independent petition workflows.
     * 
     * **Validates: Requirements 1.2, 1.3, 5.1**
     */
    @Test
    void multipleUsers_CanHaveIndependentPetitionWorkflows() {
        // Step 1: Create second user
        UserDtls secondUser = new UserDtls();
        secondUser.setTitle("นาง");
        secondUser.setName("สมหญิง ดีมาก");
        secondUser.setEmail("somying" + java.util.UUID.randomUUID() + "@test.com");
        secondUser.setMobileNumber("0823456789");
        secondUser.setPassword("password");
        secondUser.setRole("ROLE_USER");
        secondUser.setIsEnable(true);
        secondUser.setAccountNonLocked(true);
        secondUser = userRepository.save(secondUser);
        
        // Step 2: First user creates petition
        Petition user1Petition = petitionService.createPetition(
            testUser.getId(),
            "คำร้องของผู้ใช้ที่ 1",
            "รายละเอียดคำร้องผู้ใช้ที่ 1"
        );
        
        assertThat(user1Petition).isNotNull();
        
        // Step 3: Second user creates petition (should succeed independently)
        Petition user2Petition = petitionService.createPetition(
            secondUser.getId(),
            "คำร้องของผู้ใช้ที่ 2",
            "รายละเอียดคำร้องผู้ใช้ที่ 2"
        );
        
        assertThat(user2Petition).isNotNull();
        assertThat(user2Petition.getId()).isNotEqualTo(user1Petition.getId());
        
        // Step 4: Verify first user cannot submit another petition
        boolean user1CanSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(user1CanSubmit).isFalse();
        
        // Step 5: Verify second user cannot submit another petition
        boolean user2CanSubmit = petitionService.canUserSubmitPetition(secondUser.getId());
        assertThat(user2CanSubmit).isFalse();
        
        // Step 6: Complete first user's petition
        petitionService.addStatus(
            user1Petition.getId(),
            StatusType.COMPLETED,
            "เสร็จสิ้น"
        );
        
        // Step 7: Verify first user can now submit new petition
        user1CanSubmit = petitionService.canUserSubmitPetition(testUser.getId());
        assertThat(user1CanSubmit).isTrue();
        
        // Step 8: Verify second user still cannot submit (their petition is still active)
        user2CanSubmit = petitionService.canUserSubmitPetition(secondUser.getId());
        assertThat(user2CanSubmit).isFalse();
        
        // Step 9: Verify each user sees only their own petitions
        List<Petition> user1Petitions = petitionService.getUserPetitions(testUser.getId());
        assertThat(user1Petitions).hasSize(1);
        assertThat(user1Petitions.get(0).getUser().getId()).isEqualTo(testUser.getId());
        
        List<Petition> user2Petitions = petitionService.getUserPetitions(secondUser.getId());
        assertThat(user2Petitions).hasSize(1);
        assertThat(user2Petitions.get(0).getUser().getId()).isEqualTo(secondUser.getId());
    }
}
