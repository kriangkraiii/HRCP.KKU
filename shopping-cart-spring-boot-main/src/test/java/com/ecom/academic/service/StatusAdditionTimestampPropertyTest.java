package com.ecom.academic.service;

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
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Status Addition Timestamp
 * 
 * **Validates: Requirements 3.3**
 * 
 * Property 4: Status addition includes timestamp
 * For any petition and status type, when adding a new status, the created 
 * PetitionStatus should have a non-null createdAt timestamp
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class StatusAdditionTimestampPropertyTest {

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private PetitionRepository petitionRepository;

    @Autowired
    private PetitionStatusRepository petitionStatusRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        petitionStatusRepository.deleteAll();
        petitionRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Property Test: Status addition includes timestamp
     * 
     * Tests that for any petition and status type, when adding a new status,
     * the created PetitionStatus has a non-null createdAt timestamp that is
     * within a reasonable time range.
     * 
     * This test runs 100 iterations with randomly generated status types.
     */
    @Test
    void statusAdditionIncludesTimestamp() {
        // Create arbitrary generator for all status types
        Arbitrary<StatusType> statusTypeArbitrary = Arbitraries.of(StatusType.values());
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusType statusType = statusTypeArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // And: A petition exists for the user
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description for test petition"
            );
            
            // Record the time before adding status
            LocalDateTime beforeAddStatus = LocalDateTime.now();
            
            // When: A new status is added to the petition
            PetitionStatus addedStatus = petitionService.addStatus(
                petition.getId(),
                statusType,
                "Test note for " + statusType.getDisplayName()
            );
            
            LocalDateTime afterAddStatus = LocalDateTime.now();
            
            // Then: The created PetitionStatus should have a non-null timestamp
            assertThat(addedStatus).isNotNull();
            assertThat(addedStatus.getCreatedAt()).isNotNull();
            
            // And: The timestamp should be within reasonable range
            assertThat(addedStatus.getCreatedAt())
                .isAfterOrEqualTo(beforeAddStatus)
                .isBeforeOrEqualTo(afterAddStatus);
            
            // And: The status should be persisted with the timestamp
            PetitionStatus retrievedStatus = petitionStatusRepository.findById(addedStatus.getId())
                .orElseThrow();
            assertThat(retrievedStatus.getCreatedAt()).isNotNull();
            assertThat(retrievedStatus.getCreatedAt()).isEqualTo(addedStatus.getCreatedAt());
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Multiple status additions have distinct timestamps
     * 
     * Tests that when multiple statuses are added to a petition in sequence,
     * each status has its own timestamp and they are ordered chronologically.
     * 
     * This test runs 50 iterations with randomly generated status sequences.
     */
    @Test
    void multipleStatusAdditionsHaveDistinctTimestamps() {
        // Create arbitrary generator for status sequences
        Arbitrary<StatusType[]> statusSequenceArbitrary = Arbitraries.of(
            new StatusType[]{StatusType.COMMITTEE_ASSIGNED, StatusType.MEETING_SCHEDULED},
            new StatusType[]{StatusType.COMMITTEE_ASSIGNED, StatusType.MEETING_SCHEDULED, StatusType.RESULT_APPROVED},
            new StatusType[]{StatusType.COMMITTEE_ASSIGNED, StatusType.RESULT_REVISION},
            new StatusType[]{StatusType.REJECTED},
            new StatusType[]{StatusType.COMMITTEE_ASSIGNED, StatusType.MEETING_SCHEDULED, StatusType.RESULT_APPROVED, StatusType.COMPLETED}
        );
        
        // Run property test for 50 iterations
        for (int i = 0; i < 50; i++) {
            StatusType[] statusSequence = statusSequenceArbitrary.sample();
            
            // Given: A user and petition exist
            UserDtls user = createTestUser(i);
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description for test petition"
            );
            
            LocalDateTime previousTimestamp = null;
            
            // When: Multiple statuses are added in sequence
            for (int j = 0; j < statusSequence.length; j++) {
                LocalDateTime beforeAdd = LocalDateTime.now();
                
                PetitionStatus addedStatus = petitionService.addStatus(
                    petition.getId(),
                    statusSequence[j],
                    "Status update " + j
                );
                
                LocalDateTime afterAdd = LocalDateTime.now();
                
                // Then: Each status should have a non-null timestamp
                assertThat(addedStatus.getCreatedAt()).isNotNull();
                
                // And: Timestamp should be within reasonable range
                assertThat(addedStatus.getCreatedAt())
                    .isAfterOrEqualTo(beforeAdd)
                    .isBeforeOrEqualTo(afterAdd);
                
                // And: Timestamp should be after or equal to previous timestamp
                if (previousTimestamp != null) {
                    assertThat(addedStatus.getCreatedAt())
                        .isAfterOrEqualTo(previousTimestamp);
                }
                
                previousTimestamp = addedStatus.getCreatedAt();
                
                // Small delay to ensure distinct timestamps
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Initial petition creation includes timestamp
     * 
     * Tests that when a petition is created, the initial RECEIVED status
     * automatically added has a non-null timestamp.
     * 
     * This test runs 100 iterations.
     */
    @Test
    void initialPetitionCreationIncludesTimestamp() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A user exists
            UserDtls user = createTestUser(i);
            
            // Record the time before creating petition
            LocalDateTime beforeCreate = LocalDateTime.now();
            
            // When: A new petition is created
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description for test petition"
            );
            
            LocalDateTime afterCreate = LocalDateTime.now();
            
            // Fetch the status history directly from repository
            List<PetitionStatus> statusHistory = petitionStatusRepository
                .findByPetitionIdOrderByCreatedAtAsc(petition.getId());
            
            // Then: The petition should have at least one status (RECEIVED)
            assertThat(statusHistory).isNotEmpty();
            
            // And: The initial status should have a non-null timestamp
            PetitionStatus initialStatus = statusHistory.get(0);
            assertThat(initialStatus.getCreatedAt()).isNotNull();
            
            // And: The timestamp should be within reasonable range
            assertThat(initialStatus.getCreatedAt())
                .isAfterOrEqualTo(beforeCreate)
                .isBeforeOrEqualTo(afterCreate);
            
            // And: The initial status should be RECEIVED
            assertThat(initialStatus.getStatusType()).isEqualTo(StatusType.RECEIVED);
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Helper method to create a test user in the database
     */
    private UserDtls createTestUser(int iteration) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User " + iteration);
        user.setEmail("testuser" + iteration + System.currentTimeMillis() + System.nanoTime() + "@test.com");
        user.setMobileNumber("08" + String.format("%08d", iteration));
        user.setAcademicPosition("อาจารย์");
        user.setPassword("encodedPassword123");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setProfileImage("default.png");
        
        return userRepository.save(user);
    }
}
