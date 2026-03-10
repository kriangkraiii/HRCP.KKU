package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Property-Based Test for Database Error Handling
 * 
 * **Validates: Requirements 3.5, 7.5**
 * 
 * Property 9: Database Error Handling
 * For any update or delete operation that encounters a database error,
 * the system should return a user-friendly error message to the administrator
 * and the database state should remain unchanged (transaction rollback).
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class DatabaseErrorHandlingPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        userRepository.deleteAll();
    }

    /**
     * Property Test: Non-Existent User Deletion Handling
     * 
     * Tests that attempting to delete a non-existent user is handled gracefully
     * without throwing exceptions.
     * 
     * This test runs 100 iterations with randomly generated non-existent user IDs.
     */
    @Test
    void nonExistentUserDeletionHandling() {
        // Create arbitrary generator for non-existent user IDs
        Arbitrary<Integer> userIdsArbitrary = Arbitraries.integers().between(99999, 999999);
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            Integer nonExistentUserId = userIdsArbitrary.sample();
            
            // Ensure the user doesn't exist
            Optional<UserDtls> user = userRepository.findById(nonExistentUserId);
            if (user.isPresent()) {
                continue; // Skip if by chance the ID exists
            }
            
            // When: Attempting to delete a non-existent user
            Boolean result = userService.deleteUserById(nonExistentUserId);
            
            // Then: The system should handle it gracefully
            assertThat(result)
                .as("Deleting non-existent user should return false")
                .isFalse();
        }
    }

    /**
     * Property Test: Non-Existent User Update Handling
     * 
     * Tests that attempting to update a non-existent user throws an appropriate error.
     * 
     * This test runs 100 iterations with randomly generated non-existent user IDs.
     */
    @Test
    void nonExistentUserUpdateHandling() {
        // Create arbitrary generator for non-existent user IDs
        Arbitrary<Integer> userIdsArbitrary = Arbitraries.integers().between(99999, 999999);
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            Integer nonExistentUserId = userIdsArbitrary.sample();
            
            // Ensure the user doesn't exist
            Optional<UserDtls> user = userRepository.findById(nonExistentUserId);
            if (user.isPresent()) {
                continue; // Skip if by chance the ID exists
            }
            
            // When: Attempting to update a non-existent user
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(nonExistentUserId);
            updateRequest.setTitle("นาย");
            updateRequest.setName("Test Name");
            updateRequest.setEmail("test@test.com");
            updateRequest.setMobileNumber("0812345678");
            updateRequest.setAcademicPosition("อาจารย์");
            
            // Then: The system should throw an appropriate error
            assertThatThrownBy(() -> userService.updateUserDetails(updateRequest, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
        }
    }

    /**
     * Property Test: Transaction Rollback on Update Failure
     * 
     * Tests that when an update operation fails (e.g., due to constraint violation),
     * the database state remains unchanged.
     * 
     * This test runs 100 iterations with various update scenarios.
     */
    @Test
    void transactionRollbackOnUpdateFailure() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: Two users exist in the database
            UserDtls user1 = createTestAccount("user1_" + i + "@test.com");
            UserDtls user2 = createTestAccount("user2_" + i + "@test.com");
            
            Integer user1Id = user1.getId();
            String originalEmail = user1.getEmail();
            String originalName = user1.getName();
            String duplicateEmail = user2.getEmail();
            
            // When: Attempting to update user1 with user2's email (should fail)
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(user1Id);
            updateRequest.setTitle("ดร.");
            updateRequest.setName("Updated Name");
            updateRequest.setEmail(duplicateEmail); // Duplicate email
            updateRequest.setMobileNumber("0899999999");
            updateRequest.setAcademicPosition("ศาสตราจารย์");
            
            // Then: The update should fail
            boolean updateFailed = false;
            try {
                userService.updateUserDetails(updateRequest, null);
            } catch (Exception e) {
                updateFailed = true;
                // Verify error message is user-friendly
                assertThat(e.getMessage())
                    .as("Error message should be in Thai")
                    .contains("อีเมล");
            }
            
            assertThat(updateFailed)
                .as("Update with duplicate email should fail")
                .isTrue();
            
            // Verify: user1's data remains unchanged (transaction rollback)
            UserDtls retrieved = userService.getUserById(user1Id);
            assertThat(retrieved).isNotNull();
            assertThat(retrieved.getEmail())
                .as("Email should remain unchanged after failed update")
                .isEqualTo(originalEmail)
                .isNotEqualTo(duplicateEmail);
            assertThat(retrieved.getName())
                .as("Name should remain unchanged after failed update")
                .isEqualTo(originalName)
                .isNotEqualTo("Updated Name");
            
            // Clean up
            userRepository.deleteById(user1Id);
            userRepository.deleteById(user2.getId());
        }
    }

    /**
     * Property Test: Successful Operations Don't Throw Errors
     * 
     * Tests that valid update and delete operations complete successfully
     * without throwing exceptions.
     * 
     * This test runs 100 iterations with valid operations.
     */
    @Test
    void successfulOperationsDontThrowErrors() {
        // Create arbitrary generator for names
        Arbitrary<String> namesArbitrary = Arbitraries.strings()
            .alpha()
            .ofMinLength(5)
            .ofMaxLength(20);
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String newName = namesArbitrary.sample();
            
            // Given: A user exists in the database
            UserDtls user = createTestAccount("test" + i + "@test.com");
            Integer userId = user.getId();
            
            // When: Performing a valid update
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(userId);
            updateRequest.setTitle("ดร.");
            updateRequest.setName(newName);
            updateRequest.setEmail(user.getEmail());
            updateRequest.setMobileNumber("0899999999");
            updateRequest.setAcademicPosition("ศาสตราจารย์");
            
            // Then: The update should succeed without throwing exceptions
            UserDtls updated = userService.updateUserDetails(updateRequest, null);
            
            assertThat(updated).isNotNull();
            assertThat(updated.getName()).isEqualTo(newName);
            
            // Verify changes are persisted
            UserDtls retrieved = userService.getUserById(userId);
            assertThat(retrieved.getName()).isEqualTo(newName);
            
            // When: Performing a valid delete
            Boolean deleteResult = userService.deleteUserById(userId);
            
            // Then: The delete should succeed
            assertThat(deleteResult).isTrue();
            
            // Verify user is deleted
            Optional<UserDtls> deletedUser = userRepository.findById(userId);
            assertThat(deletedUser).isEmpty();
        }
    }

    /**
     * Helper method to create a test account in the database
     */
    private UserDtls createTestAccount(String email) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User " + System.nanoTime());
        user.setEmail(email);
        user.setMobileNumber("0812345678");
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
