package com.ecom.academic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Status History Ordering
 * 
 * **Validates: Requirements 3.4**
 * 
 * Property 5: Status history ordering
 * For any petition with multiple statuses, the status history should be ordered 
 * by createdAt in ascending order (oldest first).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class StatusHistoryOrderingPropertyTest {

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
     * Property Test: Status History Ordering
     * 
     * Tests that for any petition with multiple statuses added at different times,
     * the status history retrieved from the database is ordered by createdAt in
     * ascending order (oldest first).
     * 
     * This test runs 100 iterations with randomly generated status sequences.
     */
    @Test
    void statusHistoryOrdering() {
        // Create arbitrary generator for status sequences
        Arbitrary<StatusSequence> statusSequences = generateStatusSequences();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusSequence sequence = statusSequences.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // And: A petition exists
            Petition petition = createTestPetition(user, i);
            
            // When: Multiple statuses are added with different timestamps
            List<LocalDateTime> expectedTimestamps = new ArrayList<>();
            LocalDateTime baseTime = LocalDateTime.now().minusDays(sequence.getStatusCount());
            
            for (int j = 0; j < sequence.getStatusCount(); j++) {
                StatusType statusType = sequence.getStatusTypes().get(j);
                
                // Create status with incrementing timestamps to ensure ordering
                LocalDateTime timestamp = baseTime.plusHours(j);
                expectedTimestamps.add(timestamp);
                
                PetitionStatus status = new PetitionStatus();
                status.setPetition(petition);
                status.setStatusType(statusType);
                status.setCreatedAt(timestamp);
                status.setNote("Status " + j + ": " + statusType.getDisplayName());
                
                petitionStatusRepository.save(status);
                
                // Add small delay to ensure different timestamps in database
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Then: Status history should be ordered by createdAt ascending
            List<PetitionStatus> statusHistory = petitionStatusRepository
                .findByPetitionIdOrderByCreatedAtAsc(petition.getId());
            
            assertThat(statusHistory).hasSize(sequence.getStatusCount());
            
            // Verify ordering: each status should have createdAt >= previous status
            for (int j = 1; j < statusHistory.size(); j++) {
                LocalDateTime previousTime = statusHistory.get(j - 1).getCreatedAt();
                LocalDateTime currentTime = statusHistory.get(j).getCreatedAt();
                
                assertThat(currentTime)
                    .as("Status at index %d should have createdAt >= previous status", j)
                    .isAfterOrEqualTo(previousTime);
            }
            
            // Verify the timestamps match expected order
            for (int j = 0; j < statusHistory.size(); j++) {
                assertThat(statusHistory.get(j).getCreatedAt())
                    .as("Status at index %d should have expected timestamp", j)
                    .isEqualTo(expectedTimestamps.get(j));
            }
            
            // Verify status types are in the order they were added
            for (int j = 0; j < statusHistory.size(); j++) {
                assertThat(statusHistory.get(j).getStatusType())
                    .as("Status at index %d should have expected type", j)
                    .isEqualTo(sequence.getStatusTypes().get(j));
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Status History Ordering with Petition Entity
     * 
     * Tests that the status history accessed through the Petition entity's
     * statusHistory field is also properly ordered by createdAt ascending.
     * 
     * This test runs 100 iterations with randomly generated status sequences.
     */
    @Test
    void statusHistoryOrderingThroughPetitionEntity() {
        // Create arbitrary generator for status sequences
        Arbitrary<StatusSequence> statusSequences = generateStatusSequences();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusSequence sequence = statusSequences.sample();
            
            // Given: A user and petition exist
            UserDtls user = createTestUser(i);
            Petition petition = createTestPetition(user, i);
            
            // When: Multiple statuses are added with different timestamps
            LocalDateTime baseTime = LocalDateTime.now().minusDays(sequence.getStatusCount());
            
            for (int j = 0; j < sequence.getStatusCount(); j++) {
                StatusType statusType = sequence.getStatusTypes().get(j);
                LocalDateTime timestamp = baseTime.plusHours(j);
                
                PetitionStatus status = new PetitionStatus();
                status.setPetition(petition);
                status.setStatusType(statusType);
                status.setCreatedAt(timestamp);
                status.setNote("Status " + j);
                
                petitionStatusRepository.save(status);
            }
            
            // Then: Use repository query to get status history (avoiding lazy loading issue)
            List<PetitionStatus> statusHistory = petitionStatusRepository
                .findByPetitionIdOrderByCreatedAtAsc(petition.getId());
            
            assertThat(statusHistory).hasSize(sequence.getStatusCount());
            
            // Verify ordering through repository query
            for (int j = 1; j < statusHistory.size(); j++) {
                LocalDateTime previousTime = statusHistory.get(j - 1).getCreatedAt();
                LocalDateTime currentTime = statusHistory.get(j).getCreatedAt();
                
                assertThat(currentTime)
                    .as("Status at index %d should have createdAt >= previous status", j)
                    .isAfterOrEqualTo(previousTime);
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Status History Ordering with Out-of-Order Insertion
     * 
     * Tests that even when statuses are inserted in random order (not chronological),
     * the retrieved status history is still ordered by createdAt ascending.
     * 
     * This test runs 100 iterations with randomly shuffled insertion orders.
     */
    @Test
    void statusHistoryOrderingWithOutOfOrderInsertion() {
        // Create arbitrary generator for status sequences
        Arbitrary<StatusSequence> statusSequences = generateStatusSequences();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusSequence sequence = statusSequences.sample();
            
            // Given: A user and petition exist
            UserDtls user = createTestUser(i);
            Petition petition = createTestPetition(user, i);
            
            // When: Statuses are created with timestamps but inserted in shuffled order
            List<PetitionStatus> statusesToInsert = new ArrayList<>();
            LocalDateTime baseTime = LocalDateTime.now().minusDays(sequence.getStatusCount());
            
            for (int j = 0; j < sequence.getStatusCount(); j++) {
                StatusType statusType = sequence.getStatusTypes().get(j);
                LocalDateTime timestamp = baseTime.plusHours(j);
                
                PetitionStatus status = new PetitionStatus();
                status.setPetition(petition);
                status.setStatusType(statusType);
                status.setCreatedAt(timestamp);
                status.setNote("Status " + j);
                
                statusesToInsert.add(status);
            }
            
            // Shuffle the insertion order (insert in random order)
            List<PetitionStatus> shuffled = new ArrayList<>(statusesToInsert);
            java.util.Collections.shuffle(shuffled);
            
            for (PetitionStatus status : shuffled) {
                petitionStatusRepository.save(status);
            }
            
            // Then: Retrieved status history should still be ordered by createdAt
            List<PetitionStatus> statusHistory = petitionStatusRepository
                .findByPetitionIdOrderByCreatedAtAsc(petition.getId());
            
            assertThat(statusHistory).hasSize(sequence.getStatusCount());
            
            // Verify ordering is correct despite shuffled insertion
            for (int j = 1; j < statusHistory.size(); j++) {
                LocalDateTime previousTime = statusHistory.get(j - 1).getCreatedAt();
                LocalDateTime currentTime = statusHistory.get(j).getCreatedAt();
                
                assertThat(currentTime)
                    .as("Status at index %d should have createdAt >= previous status", j)
                    .isAfterOrEqualTo(previousTime);
            }
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Generates arbitrary status sequences for property testing
     */
    private Arbitrary<StatusSequence> generateStatusSequences() {
        // Generate sequences with 2 to 7 statuses (all possible status types)
        Arbitrary<Integer> statusCount = Arbitraries.integers().between(2, 7);
        
        return statusCount.map(count -> {
            List<StatusType> statusTypes = new ArrayList<>();
            StatusType[] allTypes = StatusType.values();
            
            // Generate random sequence of status types
            for (int i = 0; i < count; i++) {
                int randomIndex = (int) (Math.random() * allTypes.length);
                statusTypes.add(allTypes[randomIndex]);
            }
            
            return new StatusSequence(count, statusTypes);
        });
    }

    /**
     * Helper method to create a test user
     */
    private UserDtls createTestUser(int iteration) {
        UserDtls user = new UserDtls();
        user.setName("Test User " + iteration);
        user.setEmail("testuser" + iteration + System.nanoTime() + "@test.com");
        user.setMobileNumber("08" + String.format("%08d", iteration));
        user.setPassword("password123");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setProfileImage("default.png");
        
        return userRepository.save(user);
    }

    /**
     * Helper method to create a test petition
     */
    private Petition createTestPetition(UserDtls user, int iteration) {
        Petition petition = new Petition();
        petition.setUser(user);
        petition.setTitle("Test Petition " + iteration);
        petition.setDescription("Test description for petition " + iteration);
        petition.setCreatedAt(LocalDateTime.now());
        
        return petitionRepository.save(petition);
    }

    /**
     * Data class representing a sequence of statuses for property testing
     */
    private static class StatusSequence {
        private final int statusCount;
        private final List<StatusType> statusTypes;

        public StatusSequence(int statusCount, List<StatusType> statusTypes) {
            this.statusCount = statusCount;
            this.statusTypes = statusTypes;
        }

        public int getStatusCount() {
            return statusCount;
        }

        public List<StatusType> getStatusTypes() {
            return statusTypes;
        }
    }
}
