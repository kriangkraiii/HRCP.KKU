package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

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
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Unit tests for PetitionService
 * 
 * Tests the following service methods:
 * - canUserSubmitPetition()
 * - getActivePetition()
 * 
 * **Validates: Requirements 1.1**
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class PetitionServiceTest {

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
        testUser.setName("Test User");
        testUser.setEmail("test" + System.currentTimeMillis() + "@test.com");
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
     * Test: canUserSubmitPetition returns true when user has no petitions
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void canUserSubmitPetition_ReturnsTrue_WhenUserHasNoPetitions() {
        // Given: User with no petitions

        // When: Checking if user can submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());

        // Then: User can submit petition
        assertThat(canSubmit).isTrue();
    }

    /**
     * Test: canUserSubmitPetition returns false when user has active petition
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void canUserSubmitPetition_ReturnsFalse_WhenUserHasActivePetition() {
        // Given: User with an active petition (RECEIVED status)
        Petition petition = createPetition(testUser, "Active Petition");
        addStatus(petition, StatusType.RECEIVED);

        // When: Checking if user can submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());

        // Then: User cannot submit petition
        assertThat(canSubmit).isFalse();
    }

    /**
     * Test: canUserSubmitPetition returns true when user's petition is REJECTED
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void canUserSubmitPetition_ReturnsTrue_WhenPetitionIsRejected() {
        // Given: User with a rejected petition
        Petition petition = createPetition(testUser, "Rejected Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.REJECTED);

        // When: Checking if user can submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());

        // Then: User can submit new petition
        assertThat(canSubmit).isTrue();
    }

    /**
     * Test: canUserSubmitPetition returns true when user's petition is COMPLETED
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void canUserSubmitPetition_ReturnsTrue_WhenPetitionIsCompleted() {
        // Given: User with a completed petition
        Petition petition = createPetition(testUser, "Completed Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMMITTEE_ASSIGNED);
        addStatus(petition, StatusType.COMPLETED);

        // When: Checking if user can submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());

        // Then: User can submit new petition
        assertThat(canSubmit).isTrue();
    }

    /**
     * Test: canUserSubmitPetition returns false when user has petition in COMMITTEE_ASSIGNED status
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void canUserSubmitPetition_ReturnsFalse_WhenPetitionIsInProgress() {
        // Given: User with a petition in progress (COMMITTEE_ASSIGNED status)
        Petition petition = createPetition(testUser, "In Progress Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMMITTEE_ASSIGNED);

        // When: Checking if user can submit petition
        boolean canSubmit = petitionService.canUserSubmitPetition(testUser.getId());

        // Then: User cannot submit new petition
        assertThat(canSubmit).isFalse();
    }

    /**
     * Test: getActivePetition returns empty when user has no petitions
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void getActivePetition_ReturnsEmpty_WhenUserHasNoPetitions() {
        // Given: User with no petitions

        // When: Getting active petition
        Optional<Petition> activePetition = petitionService.getActivePetition(testUser.getId());

        // Then: No active petition found
        assertThat(activePetition).isEmpty();
    }

    /**
     * Test: getActivePetition returns petition when user has active petition
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void getActivePetition_ReturnsPetition_WhenUserHasActivePetition() {
        // Given: User with an active petition
        Petition petition = createPetition(testUser, "Active Petition");
        addStatus(petition, StatusType.RECEIVED);

        // When: Getting active petition
        Optional<Petition> activePetition = petitionService.getActivePetition(testUser.getId());

        // Then: Active petition is found
        assertThat(activePetition).isPresent();
        assertThat(activePetition.get().getId()).isEqualTo(petition.getId());
        assertThat(activePetition.get().getTitle()).isEqualTo("Active Petition");
    }

    /**
     * Test: getActivePetition returns empty when user's petition is REJECTED
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void getActivePetition_ReturnsEmpty_WhenPetitionIsRejected() {
        // Given: User with a rejected petition
        Petition petition = createPetition(testUser, "Rejected Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.REJECTED);

        // When: Getting active petition
        Optional<Petition> activePetition = petitionService.getActivePetition(testUser.getId());

        // Then: No active petition found
        assertThat(activePetition).isEmpty();
    }

    /**
     * Test: getActivePetition returns empty when user's petition is COMPLETED
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void getActivePetition_ReturnsEmpty_WhenPetitionIsCompleted() {
        // Given: User with a completed petition
        Petition petition = createPetition(testUser, "Completed Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMPLETED);

        // When: Getting active petition
        Optional<Petition> activePetition = petitionService.getActivePetition(testUser.getId());

        // Then: No active petition found
        assertThat(activePetition).isEmpty();
    }

    // Helper methods

    private Petition createPetition(UserDtls user, String title) {
        Petition petition = new Petition();
        petition.setUser(user);
        petition.setTitle(title);
        petition.setDescription("Test description");
        petition.setCreatedAt(LocalDateTime.now());
        return petitionRepository.save(petition);
    }

    private PetitionStatus addStatus(Petition petition, StatusType statusType) {
        PetitionStatus status = new PetitionStatus();
        status.setPetition(petition);
        status.setStatusType(statusType);
        status.setCreatedAt(LocalDateTime.now());
        status.setNote("Test note for " + statusType.getDisplayName());
        return petitionStatusRepository.save(status);
    }


    /**
     * Test: createPetition successfully creates petition when user has no active petition
     *
     * **Validates: Requirements 1.2, 1.3, 1.4**
     */
    @Test
    void createPetition_Success_WhenUserHasNoActivePetition() {
        // Given: User with no petitions
        String title = "New Petition";
        String description = "Petition description";

        // When: Creating a new petition
        Petition createdPetition = petitionService.createPetition(testUser.getId(), title, description);

        // Then: Petition is created successfully
        assertThat(createdPetition).isNotNull();
        assertThat(createdPetition.getId()).isNotNull();
        assertThat(createdPetition.getTitle()).isEqualTo(title);
        assertThat(createdPetition.getDescription()).isEqualTo(description);
        assertThat(createdPetition.getUser().getId()).isEqualTo(testUser.getId());
        assertThat(createdPetition.getCreatedAt()).isNotNull();
    }

    /**
     * Test: createPetition automatically adds RECEIVED status
     *
     * **Validates: Requirements 1.2, 1.3, 1.4**
     */
    /**
     * Test: createPetition automatically adds RECEIVED status
     *
     * **Validates: Requirements 1.2, 1.3, 1.4**
     */
    @Test
    void createPetition_AddsReceivedStatus_Automatically() {
        // Given: User with no petitions
        String title = "New Petition";
        String description = "Petition description";

        // When: Creating a new petition
        Petition createdPetition = petitionService.createPetition(testUser.getId(), title, description);

        // Then: RECEIVED status is added automatically
        // Manually fetch status history using the repository to avoid lazy initialization
        java.util.List<PetitionStatus> statusHistory = petitionStatusRepository.findByPetitionIdOrderByCreatedAtAsc(createdPetition.getId());

        assertThat(statusHistory).isNotEmpty();

        PetitionStatus initialStatus = statusHistory.get(0);
        assertThat(initialStatus.getStatusType()).isEqualTo(StatusType.RECEIVED);
        assertThat(initialStatus.getNote()).isEqualTo("รับคำร้องเข้าระบบ");
        assertThat(initialStatus.getCreatedAt()).isNotNull();
    }

    /**
     * Test: createPetition throws exception when user has active petition
     *
     * **Validates: Requirements 1.2**
     */
    @Test
    void createPetition_ThrowsException_WhenUserHasActivePetition() {
        // Given: User with an active petition
        Petition existingPetition = createPetition(testUser, "Existing Petition");
        addStatus(existingPetition, StatusType.RECEIVED);

        // When & Then: Attempting to create new petition throws exception
        org.junit.jupiter.api.Assertions.assertThrows(
            com.ecom.exception.ActivePetitionExistsException.class,
            () -> petitionService.createPetition(testUser.getId(), "New Petition", "Description"),
            "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่"
        );
    }

    /**
     * Test: createPetition succeeds when user's previous petition is REJECTED
     *
     * **Validates: Requirements 1.3**
     */
    @Test
    void createPetition_Success_WhenPreviousPetitionIsRejected() {
        // Given: User with a rejected petition
        Petition rejectedPetition = createPetition(testUser, "Rejected Petition");
        addStatus(rejectedPetition, StatusType.RECEIVED);
        addStatus(rejectedPetition, StatusType.REJECTED);

        // When: Creating a new petition
        Petition newPetition = petitionService.createPetition(testUser.getId(), "New Petition", "Description");

        // Then: New petition is created successfully
        assertThat(newPetition).isNotNull();
        assertThat(newPetition.getId()).isNotEqualTo(rejectedPetition.getId());
        assertThat(newPetition.getTitle()).isEqualTo("New Petition");
    }

    /**
     * Test: createPetition succeeds when user's previous petition is COMPLETED
     *
     * **Validates: Requirements 1.3**
     */
    @Test
    void createPetition_Success_WhenPreviousPetitionIsCompleted() {
        // Given: User with a completed petition
        Petition completedPetition = createPetition(testUser, "Completed Petition");
        addStatus(completedPetition, StatusType.RECEIVED);
        addStatus(completedPetition, StatusType.COMPLETED);

        // When: Creating a new petition
        Petition newPetition = petitionService.createPetition(testUser.getId(), "New Petition", "Description");

        // Then: New petition is created successfully
        assertThat(newPetition).isNotNull();
        assertThat(newPetition.getId()).isNotEqualTo(completedPetition.getId());
        assertThat(newPetition.getTitle()).isEqualTo("New Petition");
    }

    /**
     * Test: createPetition throws exception when user is not found
     *
     * **Validates: Requirements 1.2**
     */
    @Test
    void createPetition_ThrowsException_WhenUserNotFound() {
        // Given: Non-existent user ID
        Integer nonExistentUserId = 99999;

        // When & Then: Attempting to create petition throws exception
        org.junit.jupiter.api.Assertions.assertThrows(
            com.ecom.exception.UserNotFoundException.class,
            () -> petitionService.createPetition(nonExistentUserId, "New Petition", "Description")
        );
    }

    /**
     * Test: addStatus successfully adds status with timestamp
     *
     * **Validates: Requirements 3.3**
     */
    @Test
    void addStatus_Success_CreatesStatusWithTimestamp() {
        // Given: A petition
        Petition petition = createPetition(testUser, "Test Petition");
        
        // When: Adding a status
        PetitionStatus status = petitionService.addStatus(
            petition.getId(), 
            StatusType.COMMITTEE_ASSIGNED, 
            "Committee has been assigned"
        );

        // Then: Status is created with timestamp
        assertThat(status).isNotNull();
        assertThat(status.getId()).isNotNull();
        assertThat(status.getStatusType()).isEqualTo(StatusType.COMMITTEE_ASSIGNED);
        assertThat(status.getNote()).isEqualTo("Committee has been assigned");
        assertThat(status.getCreatedAt()).isNotNull();
        assertThat(status.getPetition().getId()).isEqualTo(petition.getId());
    }

    /**
     * Test: addStatus persists status to database
     *
     * **Validates: Requirements 3.3**
     */
    @Test
    void addStatus_Success_PersistsToDatabase() {
        // Given: A petition
        Petition petition = createPetition(testUser, "Test Petition");
        
        // When: Adding a status
        PetitionStatus status = petitionService.addStatus(
            petition.getId(), 
            StatusType.MEETING_SCHEDULED, 
            "Meeting scheduled for next week"
        );

        // Then: Status is persisted and can be retrieved
        Optional<PetitionStatus> retrievedStatus = petitionStatusRepository.findById(status.getId());
        assertThat(retrievedStatus).isPresent();
        assertThat(retrievedStatus.get().getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        assertThat(retrievedStatus.get().getNote()).isEqualTo("Meeting scheduled for next week");
        assertThat(retrievedStatus.get().getCreatedAt()).isNotNull();
    }

    /**
     * Test: addStatus throws exception when petition not found
     *
     * **Validates: Requirements 3.3**
     */
    @Test
    void addStatus_ThrowsException_WhenPetitionNotFound() {
        // Given: Non-existent petition ID
        Long nonExistentPetitionId = 99999L;

        // When & Then: Attempting to add status throws exception
        org.junit.jupiter.api.Assertions.assertThrows(
            com.ecom.exception.PetitionNotFoundException.class,
            () -> petitionService.addStatus(nonExistentPetitionId, StatusType.RECEIVED, "Test note")
        );
    }

    /**
     * Test: addStatus can add multiple statuses to same petition
     *
     * **Validates: Requirements 3.3**
     */
    @Test
    void addStatus_Success_AddsMultipleStatuses() {
        // Given: A petition
        Petition petition = createPetition(testUser, "Test Petition");
        
        // When: Adding multiple statuses
        PetitionStatus status1 = petitionService.addStatus(
            petition.getId(), 
            StatusType.RECEIVED, 
            "Petition received"
        );
        
        PetitionStatus status2 = petitionService.addStatus(
            petition.getId(), 
            StatusType.COMMITTEE_ASSIGNED, 
            "Committee assigned"
        );
        
        PetitionStatus status3 = petitionService.addStatus(
            petition.getId(), 
            StatusType.MEETING_SCHEDULED, 
            "Meeting scheduled"
        );

        // Then: All statuses are created with timestamps
        assertThat(status1.getCreatedAt()).isNotNull();
        assertThat(status2.getCreatedAt()).isNotNull();
        assertThat(status3.getCreatedAt()).isNotNull();
        
        // And: All statuses belong to the same petition
        assertThat(status1.getPetition().getId()).isEqualTo(petition.getId());
        assertThat(status2.getPetition().getId()).isEqualTo(petition.getId());
        assertThat(status3.getPetition().getId()).isEqualTo(petition.getId());
        
        // And: Statuses can be retrieved from database
        java.util.List<PetitionStatus> statusHistory = 
            petitionStatusRepository.findByPetitionIdOrderByCreatedAtAsc(petition.getId());
        assertThat(statusHistory).hasSize(3);
    }

    /**
     * Test: getPetitionWithHistory returns petition with status history
     *
     * **Validates: Requirements 5.1**
     */
    @Test
    void getPetitionWithHistory_Success_ReturnsPetitionWithHistory() {
        // Given: A petition with multiple statuses
        Petition petition = createPetition(testUser, "Test Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMMITTEE_ASSIGNED);
        addStatus(petition, StatusType.MEETING_SCHEDULED);

        // When: Getting petition with history
        Petition retrievedPetition = petitionService.getPetitionWithHistory(petition.getId());

        // Then: Petition is returned with all details
        assertThat(retrievedPetition).isNotNull();
        assertThat(retrievedPetition.getId()).isEqualTo(petition.getId());
        assertThat(retrievedPetition.getTitle()).isEqualTo("Test Petition");
        assertThat(retrievedPetition.getDescription()).isEqualTo("Test description");
    }

    /**
     * Test: getPetitionWithHistory throws exception when petition not found
     *
     * **Validates: Requirements 5.1**
     */
    @Test
    void getPetitionWithHistory_ThrowsException_WhenPetitionNotFound() {
        // Given: Non-existent petition ID
        Long nonExistentPetitionId = 99999L;

        // When & Then: Attempting to get petition throws exception
        org.junit.jupiter.api.Assertions.assertThrows(
            com.ecom.exception.PetitionNotFoundException.class,
            () -> petitionService.getPetitionWithHistory(nonExistentPetitionId)
        );
    }

    /**
     * Test: getUserPetitions returns all petitions for user
     *
     * **Validates: Requirements 5.1**
     */
    @Test
    void getUserPetitions_Success_ReturnsAllUserPetitions() {
        // Given: User with multiple petitions
        Petition petition1 = createPetition(testUser, "First Petition");
        addStatus(petition1, StatusType.RECEIVED);
        
        Petition petition2 = createPetition(testUser, "Second Petition");
        addStatus(petition2, StatusType.RECEIVED);
        addStatus(petition2, StatusType.COMMITTEE_ASSIGNED);
        
        Petition petition3 = createPetition(testUser, "Third Petition");
        addStatus(petition3, StatusType.RECEIVED);
        addStatus(petition3, StatusType.REJECTED);

        // When: Getting all user petitions
        java.util.List<Petition> userPetitions = petitionService.getUserPetitions(testUser.getId());

        // Then: All petitions are returned
        assertThat(userPetitions).hasSize(3);
        assertThat(userPetitions).extracting(Petition::getTitle)
            .containsExactlyInAnyOrder("First Petition", "Second Petition", "Third Petition");
    }

    /**
     * Test: getUserPetitions returns empty list when user has no petitions
     *
     * **Validates: Requirements 5.1**
     */
    @Test
    void getUserPetitions_ReturnsEmpty_WhenUserHasNoPetitions() {
        // Given: User with no petitions

        // When: Getting all user petitions
        java.util.List<Petition> userPetitions = petitionService.getUserPetitions(testUser.getId());

        // Then: Empty list is returned
        assertThat(userPetitions).isEmpty();
    }

    /**
     * Test: getUserPetitions returns petitions ordered by creation date descending
     *
     * **Validates: Requirements 5.1**
     */
    @Test
    void getUserPetitions_Success_OrderedByCreatedAtDescending() throws InterruptedException {
        // Given: User with multiple petitions created at different times
        Petition petition1 = createPetition(testUser, "First Petition");
        addStatus(petition1, StatusType.RECEIVED);
        
        Thread.sleep(10); // Small delay to ensure different timestamps
        
        Petition petition2 = createPetition(testUser, "Second Petition");
        addStatus(petition2, StatusType.RECEIVED);
        
        Thread.sleep(10); // Small delay to ensure different timestamps
        
        Petition petition3 = createPetition(testUser, "Third Petition");
        addStatus(petition3, StatusType.RECEIVED);

        // When: Getting all user petitions
        java.util.List<Petition> userPetitions = petitionService.getUserPetitions(testUser.getId());

        // Then: Petitions are ordered by creation date descending (newest first)
        assertThat(userPetitions).hasSize(3);
        assertThat(userPetitions.get(0).getTitle()).isEqualTo("Third Petition");
        assertThat(userPetitions.get(1).getTitle()).isEqualTo("Second Petition");
        assertThat(userPetitions.get(2).getTitle()).isEqualTo("First Petition");
    }

}
