package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.academic.model.Petition;
import com.ecom.academic.model.StatusType;
import com.ecom.academic.repository.PetitionRepository;
import com.ecom.academic.repository.PetitionStatusRepository;
import com.ecom.exception.ActivePetitionExistsException;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Active Petition Blocking
 * 
 * **Validates: Requirements 1.2**
 * 
 * Property 1: Active petition blocking
 * For any user with an active petition (status not REJECTED or COMPLETED), 
 * attempting to create a new petition should throw ActivePetitionExistsException
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class ActivePetitionBlockingPropertyTest {

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
     * Property Test: Active petition blocking
     * 
     * Tests that for any user with an active petition (status not REJECTED or COMPLETED),
     * attempting to create a new petition throws ActivePetitionExistsException.
     * 
     * This test runs 100 iterations with randomly generated active status types.
     */
    @Test
    void activePetitionBlocksNewSubmission() {
        // Create arbitrary generator for active status types (not REJECTED or COMPLETED)
        Arbitrary<StatusType> activeStatusArbitrary = Arbitraries.of(
            StatusType.RECEIVED,
            StatusType.COMMITTEE_ASSIGNED,
            StatusType.MEETING_SCHEDULED,
            StatusType.RESULT_APPROVED,
            StatusType.RESULT_REVISION
        );
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusType activeStatus = activeStatusArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // And: The user has a petition with an active status
            Petition existingPetition = petitionService.createPetition(
                user.getId(),
                "Existing Petition " + i,
                "Description for existing petition"
            );
            
            // Change status to the randomly selected active status (if not RECEIVED)
            if (activeStatus != StatusType.RECEIVED) {
                petitionService.addStatus(existingPetition.getId(), activeStatus, "Status update");
            }
            
            // When: User attempts to create a new petition
            // Then: ActivePetitionExistsException should be thrown
            final Integer userId = user.getId();
            final int iteration = i;
            assertThatThrownBy(() -> 
                petitionService.createPetition(
                    userId,
                    "New Petition " + iteration,
                    "Description for new petition"
                )
            )
            .isInstanceOf(ActivePetitionExistsException.class)
            .hasMessageContaining("ไม่สามารถยื่นคำร้องใหม่ได้")
            .hasMessageContaining("มีคำร้องที่กำลังดำเนินการอยู่");
            
            // And: User should still have only one petition
            assertThat(petitionRepository.findAllByUserIdWithStatusHistory(user.getId()))
                .hasSize(1);
            
            // And: canUserSubmitPetition should return false
            assertThat(petitionService.canUserSubmitPetition(user.getId())).isFalse();
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Terminal status allows new submission
     * 
     * **Validates: Requirements 1.3**
     * 
     * Tests that users with petitions in terminal status (REJECTED or COMPLETED)
     * can submit new petitions successfully.
     * 
     * This test runs 100 iterations with randomly generated terminal status types.
     */
    @Test
    void terminalStatusAllowsNewSubmission() {
        // Create arbitrary generator for terminal status types
        Arbitrary<StatusType> terminalStatusArbitrary = Arbitraries.of(
            StatusType.REJECTED,
            StatusType.COMPLETED
        );
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            StatusType terminalStatus = terminalStatusArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // And: The user has a petition with a terminal status
            Petition existingPetition = petitionService.createPetition(
                user.getId(),
                "Existing Petition " + i,
                "Description for existing petition"
            );
            
            // Change status to terminal status
            petitionService.addStatus(existingPetition.getId(), terminalStatus, "Terminal status");
            
            // When: User attempts to create a new petition
            Petition newPetition = petitionService.createPetition(
                user.getId(),
                "New Petition " + i,
                "Description for new petition"
            );
            
            // Then: New petition should be created successfully
            assertThat(newPetition).isNotNull();
            assertThat(newPetition.getId()).isNotNull();
            assertThat(newPetition.getTitle()).isEqualTo("New Petition " + i);
            
            // And: User should now have two petitions
            assertThat(petitionRepository.findAllByUserIdWithStatusHistory(user.getId()))
                .hasSize(2);
            
            // And: canUserSubmitPetition should return false (because new petition is active)
            assertThat(petitionService.canUserSubmitPetition(user.getId())).isFalse();
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: No petition allows submission
     * 
     * Tests that users with no petitions can submit new petitions successfully.
     * 
     * This test runs 100 iterations.
     */
    @Test
    void noPetitionAllowsSubmission() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: A user exists with no petitions
            UserDtls user = createTestUser(i);
            
            // When: User attempts to create a petition
            Petition newPetition = petitionService.createPetition(
                user.getId(),
                "First Petition " + i,
                "Description for first petition"
            );
            
            // Then: Petition should be created successfully
            assertThat(newPetition).isNotNull();
            assertThat(newPetition.getId()).isNotNull();
            assertThat(newPetition.getTitle()).isEqualTo("First Petition " + i);
            
            // And: User should have exactly one petition
            assertThat(petitionRepository.findAllByUserIdWithStatusHistory(user.getId()))
                .hasSize(1);
            
            // And: canUserSubmitPetition should return false (because petition is active)
            assertThat(petitionService.canUserSubmitPetition(user.getId())).isFalse();
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Multiple active status transitions still block submission
     * 
     * Tests that even after multiple status changes, as long as the current status
     * is active, new petition submission is blocked.
     * 
     * This test runs 50 iterations with randomly generated status sequences.
     */
    @Test
    void multipleActiveStatusTransitionsStillBlock() {
        // Create arbitrary generator for active status sequences
        Arbitrary<StatusType[]> statusSequenceArbitrary = Arbitraries.of(
            new StatusType[]{StatusType.RECEIVED, StatusType.COMMITTEE_ASSIGNED},
            new StatusType[]{StatusType.RECEIVED, StatusType.COMMITTEE_ASSIGNED, StatusType.MEETING_SCHEDULED},
            new StatusType[]{StatusType.RECEIVED, StatusType.MEETING_SCHEDULED, StatusType.RESULT_APPROVED},
            new StatusType[]{StatusType.RECEIVED, StatusType.COMMITTEE_ASSIGNED, StatusType.RESULT_REVISION}
        );
        
        // Run property test for 50 iterations
        for (int i = 0; i < 50; i++) {
            StatusType[] statusSequence = statusSequenceArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestUser(i);
            
            // And: The user has a petition that went through multiple status changes
            Petition existingPetition = petitionService.createPetition(
                user.getId(),
                "Existing Petition " + i,
                "Description for existing petition"
            );
            
            // Add multiple status changes (skip first as it's already RECEIVED)
            for (int j = 1; j < statusSequence.length; j++) {
                petitionService.addStatus(existingPetition.getId(), statusSequence[j], "Status update " + j);
            }
            
            // When: User attempts to create a new petition
            // Then: ActivePetitionExistsException should be thrown
            final Integer userId = user.getId();
            final int iteration = i;
            assertThatThrownBy(() -> 
                petitionService.createPetition(
                    userId,
                    "New Petition " + iteration,
                    "Description for new petition"
                )
            )
            .isInstanceOf(ActivePetitionExistsException.class);
            
            // And: User should have exactly one petition (new petition was blocked)
            assertThat(petitionRepository.findAllByUserIdWithStatusHistory(user.getId()))
                .hasSize(1);
            
            // Clean up
            petitionStatusRepository.deleteAll();
            petitionRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Active to terminal to active sequence
     * 
     * Tests the complete lifecycle: user creates petition (active), it becomes terminal,
     * user creates new petition (active), and is blocked from creating another.
     * 
     * This test runs 50 iterations.
     */
    @Test
    void activeToTerminalToActiveSequence() {
        // Create arbitrary generator for terminal status
        Arbitrary<StatusType> terminalStatusArbitrary = Arbitraries.of(
            StatusType.REJECTED,
            StatusType.COMPLETED
        );
        
        // Run property test for 50 iterations
        for (int i = 0; i < 50; i++) {
            StatusType terminalStatus = terminalStatusArbitrary.sample();
            
            // Given: A user exists
            UserDtls user = createTestUser(i);
            
            // When: User creates first petition (active)
            Petition firstPetition = petitionService.createPetition(
                user.getId(),
                "First Petition " + i,
                "First description"
            );
            
            // And: First petition becomes terminal
            petitionService.addStatus(firstPetition.getId(), terminalStatus, "Terminal");
            
            // And: User creates second petition (active)
            Petition secondPetition = petitionService.createPetition(
                user.getId(),
                "Second Petition " + i,
                "Second description"
            );
            
            // Then: User should be blocked from creating third petition
            final Integer userId = user.getId();
            final int iteration = i;
            assertThatThrownBy(() -> 
                petitionService.createPetition(
                    userId,
                    "Third Petition " + iteration,
                    "Third description"
                )
            )
            .isInstanceOf(ActivePetitionExistsException.class);
            
            // And: User should have exactly two petitions
            assertThat(petitionRepository.findAllByUserIdWithStatusHistory(user.getId()))
                .hasSize(2);
            
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
