package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

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
 * Property-Based Test for Status History Integrity
 * 
 * **Validates: Requirements 5.1**
 * 
 * Property 6: Status history contains only actual records
 * For any petition, the status history should contain exactly the statuses that 
 * were explicitly added, with no additional or missing entries.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class StatusHistoryIntegrityPropertyTest {

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
     * Property Test: Status history contains only actual records
     * 
     * Tests that for any petition with a sequence of status changes, the status 
     * history contains exactly the statuses that were explicitly added, with no 
     * additional or missing entries.
     * 
     * This test runs 100 iterations with randomly generated status sequences.
     */
    @Test
    void statusHistoryContainsOnlyActualRecords() {
        // Create arbitrary generator for status sequences
        Arbitrary<List<StatusType>> statusSequenceArbitrary = generateStatusSequences();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            List<StatusType> statusSequence = statusSequenceArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // When: A petition is created (automatically adds RECEIVED status)
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description for test petition"
            );
            
            // Track expected statuses (starting with RECEIVED which is auto-added)
            List<StatusType> expectedStatuses = new ArrayList<>();
            expectedStatuses.add(StatusType.RECEIVED);
            
            // And: Additional statuses are added to the petition
            for (StatusType statusType : statusSequence) {
                petitionService.addStatus(petition.getId(), statusType, "Status update");
                expectedStatuses.add(statusType);
            }
            
            // Then: Retrieve the petition and access status history within transaction
            Petition retrievedPetition = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            List<PetitionStatus> actualHistory = retrievedPetition.getStatusHistory();
            
            // Verify: Status history size matches expected size
            assertThat(actualHistory)
                .as("Status history should contain exactly %d entries", expectedStatuses.size())
                .hasSize(expectedStatuses.size());
            
            // Verify: Each status in history matches the expected status at that position
            for (int j = 0; j < expectedStatuses.size(); j++) {
                assertThat(actualHistory.get(j).getStatusType())
                    .as("Status at position %d should be %s", j, expectedStatuses.get(j))
                    .isEqualTo(expectedStatuses.get(j));
            }
            
            // Verify: No duplicate status IDs (each record is unique)
            List<Long> statusIds = actualHistory.stream()
                .map(PetitionStatus::getId)
                .toList();
            assertThat(statusIds)
                .as("All status records should have unique IDs")
                .doesNotHaveDuplicates();
            
            // Verify: All statuses reference the correct petition
            assertThat(actualHistory)
                .as("All statuses should reference petition ID %d", petition.getId())
                .allMatch(status -> status.getPetition().getId().equals(petition.getId()));
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Empty additional status sequence
     * 
     * Tests that a petition with no additional statuses (only the initial RECEIVED)
     * has exactly one status in its history.
     * 
     * This test runs 100 iterations.
     */
    @Test
    void petitionWithOnlyInitialStatusHasOneRecord() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // When: A petition is created (automatically adds RECEIVED status)
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description for test petition"
            );
            
            // Then: Retrieve the petition and access status history within transaction
            Petition retrievedPetition = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            List<PetitionStatus> actualHistory = retrievedPetition.getStatusHistory();
            
            // Verify: Status history contains exactly one entry
            assertThat(actualHistory)
                .as("Petition with no additional statuses should have exactly 1 status")
                .hasSize(1);
            
            // Verify: The single status is RECEIVED
            assertThat(actualHistory.get(0).getStatusType())
                .as("Initial status should be RECEIVED")
                .isEqualTo(StatusType.RECEIVED);
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Status history integrity after multiple retrievals
     * 
     * Tests that retrieving a petition multiple times always returns the same
     * status history, ensuring data consistency.
     * 
     * This test runs 50 iterations with randomly generated status sequences.
     */
    @Test
    void statusHistoryConsistentAcrossMultipleRetrievals() {
        // Create arbitrary generator for status sequences
        Arbitrary<List<StatusType>> statusSequenceArbitrary = generateStatusSequences();
        
        // Run property test for 50 iterations
        for (int i = 0; i < 50; i++) {
            List<StatusType> statusSequence = statusSequenceArbitrary.sample();
            
            // Given: A user and petition exist
            UserDtls user = createTestUser(i);
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description"
            );
            
            // And: Multiple statuses are added
            for (StatusType statusType : statusSequence) {
                petitionService.addStatus(petition.getId(), statusType, "Update");
            }
            
            // When: Petition is retrieved multiple times
            Petition retrieval1 = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            Petition retrieval2 = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            Petition retrieval3 = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            
            // Then: All retrievals should have the same status history size
            int expectedSize = statusSequence.size() + 1; // +1 for initial RECEIVED
            assertThat(retrieval1.getStatusHistory()).hasSize(expectedSize);
            assertThat(retrieval2.getStatusHistory()).hasSize(expectedSize);
            assertThat(retrieval3.getStatusHistory()).hasSize(expectedSize);
            
            // And: Status types should match across all retrievals
            for (int j = 0; j < expectedSize; j++) {
                StatusType type1 = retrieval1.getStatusHistory().get(j).getStatusType();
                StatusType type2 = retrieval2.getStatusHistory().get(j).getStatusType();
                StatusType type3 = retrieval3.getStatusHistory().get(j).getStatusType();
                
                assertThat(type1).isEqualTo(type2).isEqualTo(type3);
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Status history integrity with all possible status types
     * 
     * Tests that status history correctly records all different status types
     * including terminal statuses (REJECTED, COMPLETED).
     * 
     * This test runs 50 iterations with sequences containing all status types.
     */
    @Test
    void statusHistoryRecordsAllStatusTypes() {
        // Create arbitrary generator that includes all status types
        Arbitrary<StatusType> allStatusTypesArbitrary = Arbitraries.of(StatusType.values());
        
        // Run property test for 50 iterations
        for (int i = 0; i < 50; i++) {
            // Given: A user and petition exist
            UserDtls user = createTestUser(i);
            Petition petition = petitionService.createPetition(
                user.getId(),
                "Test Petition " + i,
                "Description"
            );
            
            // Generate a random sequence of 3-5 status types
            int sequenceLength = 3 + (i % 3); // 3, 4, or 5
            List<StatusType> statusSequence = new ArrayList<>();
            for (int j = 0; j < sequenceLength; j++) {
                statusSequence.add(allStatusTypesArbitrary.sample());
            }
            
            // Track expected statuses
            List<StatusType> expectedStatuses = new ArrayList<>();
            expectedStatuses.add(StatusType.RECEIVED);
            
            // When: Statuses are added
            for (StatusType statusType : statusSequence) {
                petitionService.addStatus(petition.getId(), statusType, "Status update " + statusType);
                expectedStatuses.add(statusType);
            }
            
            // Then: Retrieve and verify status history
            Petition retrievedPetition = petitionRepository.findByIdWithStatusHistory(petition.getId()).orElseThrow();
            List<PetitionStatus> actualHistory = retrievedPetition.getStatusHistory();
            
            // Verify: All expected statuses are present in order
            assertThat(actualHistory).hasSize(expectedStatuses.size());
            for (int j = 0; j < expectedStatuses.size(); j++) {
                assertThat(actualHistory.get(j).getStatusType())
                    .isEqualTo(expectedStatuses.get(j));
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Generates arbitrary status sequences for property testing.
     * Sequences can be empty or contain 1-5 status types.
     */
    private Arbitrary<List<StatusType>> generateStatusSequences() {
        // Define possible status types (excluding RECEIVED as it's auto-added)
        Arbitrary<StatusType> statusTypeArbitrary = Arbitraries.of(
            StatusType.COMMITTEE_ASSIGNED,
            StatusType.MEETING_SCHEDULED,
            StatusType.RESULT_APPROVED,
            StatusType.RESULT_REVISION,
            StatusType.REJECTED,
            StatusType.COMPLETED
        );
        
        // Generate sequences of 0-5 statuses
        return Arbitraries.integers().between(0, 5)
            .flatMap(size -> {
                if (size == 0) {
                    return Arbitraries.just(new ArrayList<>());
                }
                
                List<StatusType> sequence = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    sequence.add(statusTypeArbitrary.sample());
                }
                return Arbitraries.just(sequence);
            });
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
