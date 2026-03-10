package com.ecom.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.ecom.model.UserDtls;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Petition.isActive() method
 * 
 * **Validates: Requirements 2.4**
 * 
 * Property 3: Completed status is not active
 * For any petition with current status COMPLETED, the isActive() method should return false
 */
public class PetitionIsActivePropertyTest {

    /**
     * Property Test: Completed status is not active
     * 
     * Tests that for any petition with COMPLETED status, isActive() returns false.
     * This test runs 100 iterations with randomly generated petition data.
     */
    @Test
    void completedStatusIsNotActive() {
        // Create arbitrary generator for status types
        Arbitrary<StatusType> statusTypeArbitrary = Arbitraries.of(StatusType.values());
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusType statusType = statusTypeArbitrary.sample();
            
            // Given: A petition with a specific status
            Petition petition = createTestPetition();
            PetitionStatus status = new PetitionStatus();
            status.setPetition(petition);
            status.setStatusType(statusType);
            status.setCreatedAt(LocalDateTime.now());
            
            petition.getStatusHistory().add(status);
            
            // When: Checking if petition is active
            boolean isActive = petition.isActive();
            
            // Then: Petition should be active only if status is not REJECTED or COMPLETED
            if (statusType == StatusType.COMPLETED || statusType == StatusType.REJECTED) {
                assertThat(isActive).isFalse();
            } else {
                assertThat(isActive).isTrue();
            }
        }
    }

    /**
     * Property Test: Completed status specifically is not active
     * 
     * Tests that any petition with COMPLETED status always returns false for isActive().
     * This test runs 100 iterations to ensure consistency.
     */
    @Test
    void completedStatusAlwaysReturnsFalse() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A petition with COMPLETED status
            Petition petition = createTestPetition();
            PetitionStatus status = new PetitionStatus();
            status.setPetition(petition);
            status.setStatusType(StatusType.COMPLETED);
            status.setCreatedAt(LocalDateTime.now());
            
            petition.getStatusHistory().add(status);
            
            // When: Checking if petition is active
            boolean isActive = petition.isActive();
            
            // Then: Petition should not be active
            assertThat(isActive).isFalse();
        }
    }

    /**
     * Property Test: Rejected status is not active
     * 
     * Tests that any petition with REJECTED status always returns false for isActive().
     * This test runs 100 iterations to ensure consistency.
     */
    @Test
    void rejectedStatusAlwaysReturnsFalse() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A petition with REJECTED status
            Petition petition = createTestPetition();
            PetitionStatus status = new PetitionStatus();
            status.setPetition(petition);
            status.setStatusType(StatusType.REJECTED);
            status.setCreatedAt(LocalDateTime.now());
            
            petition.getStatusHistory().add(status);
            
            // When: Checking if petition is active
            boolean isActive = petition.isActive();
            
            // Then: Petition should not be active
            assertThat(isActive).isFalse();
        }
    }

    /**
     * Property Test: Active statuses return true
     * 
     * Tests that petitions with non-terminal statuses (not COMPLETED or REJECTED)
     * always return true for isActive().
     */
    @Test
    void activeStatusesReturnTrue() {
        StatusType[] activeStatuses = {
            StatusType.RECEIVED,
            StatusType.COMMITTEE_ASSIGNED,
            StatusType.MEETING_SCHEDULED,
            StatusType.RESULT_APPROVED,
            StatusType.RESULT_REVISION
        };
        
        // Run property test for each active status
        for (StatusType statusType : activeStatuses) {
            for (int i = 0; i < 20; i++) {
                // Given: A petition with an active status
                Petition petition = createTestPetition();
                PetitionStatus status = new PetitionStatus();
                status.setPetition(petition);
                status.setStatusType(statusType);
                status.setCreatedAt(LocalDateTime.now());
                
                petition.getStatusHistory().add(status);
                
                // When: Checking if petition is active
                boolean isActive = petition.isActive();
                
                // Then: Petition should be active
                assertThat(isActive).isTrue();
            }
        }
    }

    /**
     * Property Test: Empty status history returns false
     * 
     * Tests that a petition with no status history returns false for isActive().
     */
    @Test
    void emptyStatusHistoryReturnsFalse() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A petition with no status history
            Petition petition = createTestPetition();
            
            // When: Checking if petition is active
            boolean isActive = petition.isActive();
            
            // Then: Petition should not be active
            assertThat(isActive).isFalse();
        }
    }

    /**
     * Property Test: Most recent status determines active state
     * 
     * Tests that only the most recent status in the history determines
     * whether the petition is active, regardless of previous statuses.
     */
    @Test
    void mostRecentStatusDeterminesActiveState() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A petition with multiple statuses
            Petition petition = createTestPetition();
            
            // Add some active statuses
            PetitionStatus status1 = new PetitionStatus();
            status1.setPetition(petition);
            status1.setStatusType(StatusType.RECEIVED);
            status1.setCreatedAt(LocalDateTime.now().minusDays(3));
            petition.getStatusHistory().add(status1);
            
            PetitionStatus status2 = new PetitionStatus();
            status2.setPetition(petition);
            status2.setStatusType(StatusType.COMMITTEE_ASSIGNED);
            status2.setCreatedAt(LocalDateTime.now().minusDays(2));
            petition.getStatusHistory().add(status2);
            
            // Add a terminal status as the most recent
            PetitionStatus status3 = new PetitionStatus();
            status3.setPetition(petition);
            status3.setStatusType(StatusType.COMPLETED);
            status3.setCreatedAt(LocalDateTime.now());
            petition.getStatusHistory().add(status3);
            
            // When: Checking if petition is active
            boolean isActive = petition.isActive();
            
            // Then: Petition should not be active (based on most recent status)
            assertThat(isActive).isFalse();
        }
    }

    /**
     * Helper method to create a test petition
     */
    private Petition createTestPetition() {
        UserDtls user = new UserDtls();
        user.setId(1);
        user.setName("Test User");
        user.setEmail("test@example.com");
        
        Petition petition = new Petition();
        petition.setId(1L);
        petition.setUser(user);
        petition.setTitle("Test Petition");
        petition.setDescription("Test Description");
        petition.setCreatedAt(LocalDateTime.now());
        
        return petition;
    }
}
