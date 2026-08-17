package com.ecom.academic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
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
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

/**
 * Unit tests for PetitionRepository custom query methods
 * 
 * Tests the following repository methods:
 * - findActivePetitionByUserId()
 * - findAllByUserIdWithStatusHistory()
 * 
 * **Validates: Requirements 1.1, 1.3, 3.4**
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class PetitionRepositoryTest {

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
        testUser.setEmail("test" + java.util.UUID.randomUUID() + "@test.com");
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
     * Test: findActivePetitionByUserId returns empty when user has petition with REJECTED status
     * 
     * **Validates: Requirements 1.1, 1.3**
     */
    @Test
    void findActivePetitionByUserId_ReturnsEmpty_WhenPetitionIsRejected() {
        // Given: A petition with REJECTED status
        Petition petition = createPetition(testUser, "Test Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.REJECTED);

        // When: Finding active petition
        Optional<Petition> result = petitionRepository.findActivePetitionByUserId(testUser.getId());

        // Then: No active petition found
        assertThat(result).isEmpty();
    }

    /**
     * Test: findActivePetitionByUserId returns empty when user has petition with COMPLETED status
     * 
     * **Validates: Requirements 1.1, 1.3**
     */
    @Test
    void findActivePetitionByUserId_ReturnsEmpty_WhenPetitionIsCompleted() {
        // Given: A petition with COMPLETED status
        Petition petition = createPetition(testUser, "Test Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMMITTEE_ASSIGNED);
        addStatus(petition, StatusType.COMPLETED);

        // When: Finding active petition
        Optional<Petition> result = petitionRepository.findActivePetitionByUserId(testUser.getId());

        // Then: No active petition found
        assertThat(result).isEmpty();
    }

    /**
     * Test: findActivePetitionByUserId returns petition when user has active petition
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void findActivePetitionByUserId_ReturnsPetition_WhenPetitionIsActive() {
        // Given: A petition with active status (RECEIVED)
        Petition petition = createPetition(testUser, "Active Petition");
        addStatus(petition, StatusType.RECEIVED);

        // When: Finding active petition
        Optional<Petition> result = petitionRepository.findActivePetitionByUserId(testUser.getId());

        // Then: Active petition is found
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(petition.getId());
        assertThat(result.get().getTitle()).isEqualTo("Active Petition");
    }

    /**
     * Test: findActivePetitionByUserId returns petition with COMMITTEE_ASSIGNED status
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void findActivePetitionByUserId_ReturnsPetition_WhenStatusIsCommitteeAssigned() {
        // Given: A petition with COMMITTEE_ASSIGNED status
        Petition petition = createPetition(testUser, "Committee Petition");
        addStatus(petition, StatusType.RECEIVED);
        addStatus(petition, StatusType.COMMITTEE_ASSIGNED);

        // When: Finding active petition
        Optional<Petition> result = petitionRepository.findActivePetitionByUserId(testUser.getId());

        // Then: Active petition is found
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(petition.getId());
    }

    /**
     * Test: findActivePetitionByUserId returns empty when user has no petitions
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    void findActivePetitionByUserId_ReturnsEmpty_WhenUserHasNoPetitions() {
        // Given: User with no petitions

        // When: Finding active petition
        Optional<Petition> result = petitionRepository.findActivePetitionByUserId(testUser.getId());

        // Then: No petition found
        assertThat(result).isEmpty();
    }

    /**
     * Test: findByPetitionIdOrderByCreatedAtAsc returns statuses ordered by time
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void findByPetitionIdOrderByCreatedAtAsc_ReturnsStatusesOrderedByTime() throws InterruptedException {
        // Given: A petition with multiple statuses added at different times
        Petition petition = createPetition(testUser, "Test Petition");
        
        PetitionStatus status1 = addStatus(petition, StatusType.RECEIVED);
        Thread.sleep(10); // Ensure different timestamps
        
        PetitionStatus status2 = addStatus(petition, StatusType.COMMITTEE_ASSIGNED);
        Thread.sleep(10);
        
        PetitionStatus status3 = addStatus(petition, StatusType.MEETING_SCHEDULED);

        // When: Finding statuses by petition ID
        List<PetitionStatus> statuses = petitionStatusRepository.findByPetitionIdOrderByCreatedAtAsc(petition.getId());

        // Then: Statuses are ordered by creation time (oldest first)
        assertThat(statuses).hasSize(3);
        assertThat(statuses.get(0).getId()).isEqualTo(status1.getId());
        assertThat(statuses.get(0).getStatusType()).isEqualTo(StatusType.RECEIVED);
        
        assertThat(statuses.get(1).getId()).isEqualTo(status2.getId());
        assertThat(statuses.get(1).getStatusType()).isEqualTo(StatusType.COMMITTEE_ASSIGNED);
        
        assertThat(statuses.get(2).getId()).isEqualTo(status3.getId());
        assertThat(statuses.get(2).getStatusType()).isEqualTo(StatusType.MEETING_SCHEDULED);
        
        // Verify timestamps are in ascending order
        assertThat(statuses.get(0).getCreatedAt()).isBefore(statuses.get(1).getCreatedAt());
        assertThat(statuses.get(1).getCreatedAt()).isBefore(statuses.get(2).getCreatedAt());
    }

    /**
     * Test: findAllByUserIdWithStatusHistory loads status history eagerly
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void findAllByUserIdWithStatusHistory_LoadsStatusHistoryEagerly() {
        // Given: Multiple petitions with status history
        Petition petition1 = createPetition(testUser, "Petition 1");
        addStatus(petition1, StatusType.RECEIVED);
        addStatus(petition1, StatusType.COMMITTEE_ASSIGNED);

        Petition petition2 = createPetition(testUser, "Petition 2");
        addStatus(petition2, StatusType.RECEIVED);

        // When: Finding all petitions with status history
        List<Petition> petitions = petitionRepository.findAllByUserIdWithStatusHistory(testUser.getId());

        // Then: All petitions are loaded with status history
        assertThat(petitions).hasSize(2);
        
        // Verify status history is loaded (not lazy)
        Petition loadedPetition1 = petitions.stream()
            .filter(p -> p.getTitle().equals("Petition 1"))
            .findFirst()
            .orElseThrow();
        assertThat(loadedPetition1.getStatusHistory()).hasSize(2);
        
        Petition loadedPetition2 = petitions.stream()
            .filter(p -> p.getTitle().equals("Petition 2"))
            .findFirst()
            .orElseThrow();
        assertThat(loadedPetition2.getStatusHistory()).hasSize(1);
    }

    /**
     * Test: findAllByUserIdWithStatusHistory returns petitions ordered by creation date descending
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    void findAllByUserIdWithStatusHistory_OrdersByCreatedAtDescending() throws InterruptedException {
        // Given: Multiple petitions created at different times
        Petition petition1 = createPetition(testUser, "First Petition");
        addStatus(petition1, StatusType.RECEIVED);
        
        Thread.sleep(10);
        
        Petition petition2 = createPetition(testUser, "Second Petition");
        addStatus(petition2, StatusType.RECEIVED);

        // When: Finding all petitions
        List<Petition> petitions = petitionRepository.findAllByUserIdWithStatusHistory(testUser.getId());

        // Then: Petitions are ordered by creation date descending (newest first)
        assertThat(petitions).hasSize(2);
        assertThat(petitions.get(0).getTitle()).isEqualTo("Second Petition");
        assertThat(petitions.get(1).getTitle()).isEqualTo("First Petition");
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
}
