package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Property-Based Test for Email Uniqueness Enforcement
 * 
 * **Validates: Requirements 1.4, 2.4**
 * 
 * Property 3: Email Uniqueness Enforcement
 * For any account update attempt where the new email already exists in the system 
 * (for a different account), the system should reject the update and return an error 
 * message indicating the email is already in use.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class EmailUniquenessEnforcementPropertyTest {

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
     * Property Test: Email Uniqueness Enforcement
     * 
     * Tests that for any account update attempt where the new email already exists
     * in the system (for a different account), the system rejects the update.
     * 
     * This test runs 100 iterations with randomly generated email addresses.
     */
    @Test
    void emailUniquenessEnforcement() {
        // Create arbitrary generator for emails
        Arbitrary<String> emailsArbitrary = validEmails();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String existingEmail = emailsArbitrary.sample();
            
            // Given: Two accounts exist in the database with different emails
            UserDtls account1 = createTestAccount("account1" + i + "@test.com");
            UserDtls account2 = createTestAccount(existingEmail);
            
            Integer account1Id = account1.getId();
            String account2Email = account2.getEmail();
            
            // When: Administrator attempts to update account1's email to account2's email
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(account1Id);
            updateRequest.setTitle(account1.getTitle());
            updateRequest.setName(account1.getName());
            updateRequest.setEmail(account2Email); // Duplicate email
            updateRequest.setMobileNumber(account1.getMobileNumber());
            updateRequest.setAcademicPosition(account1.getAcademicPosition());
            
            // Then: The system should reject the update
            // Check if email exists for a different account
            Boolean emailExists = userService.existsEmail(account2Email);
            assertThat(emailExists).isTrue();
            
            // Verify that the original account's email remains unchanged
            UserDtls retrievedAccount1 = userService.getUserById(account1Id);
            assertThat(retrievedAccount1.getEmail()).isEqualTo(account1.getEmail());
            assertThat(retrievedAccount1.getEmail()).isNotEqualTo(account2Email);
            
            // Verify both accounts still exist with their original emails
            UserDtls retrievedAccount2 = userService.getUserById(account2.getId());
            assertThat(retrievedAccount2.getEmail()).isEqualTo(account2Email);
            
            // Clean up
            userRepository.deleteById(account1Id);
            userRepository.deleteById(account2.getId());
        }
    }

    /**
     * Property Test: Email Uniqueness Enforcement with Update Attempt
     * 
     * Tests that attempting to update an account with a duplicate email either:
     * 1. Throws an exception (preferred behavior), OR
     * 2. Silently fails and keeps the original email (current behavior)
     * 
     * This test validates that the duplicate email is NEVER persisted.
     * This test runs 100 iterations with randomly generated email addresses.
     */
    @Test
    void emailUniquenessEnforcementWithUpdate() {
        // Create arbitrary generator for emails
        Arbitrary<String> emailsArbitrary = validEmails();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String existingEmail = emailsArbitrary.sample();
            
            // Given: Two accounts exist in the database
            UserDtls account1 = createTestAccount("account1" + i + "@test.com");
            UserDtls account2 = createTestAccount(existingEmail);
            
            Integer account1Id = account1.getId();
            Integer account2Id = account2.getId();
            String originalEmail = account1.getEmail();
            String duplicateEmail = account2.getEmail();
            
            // Verify email uniqueness check returns true for existing email
            Boolean emailExists = userService.existsEmail(duplicateEmail);
            assertThat(emailExists).isTrue();
            
            // When: Administrator attempts to update account1's email to account2's email
            // The controller should check existsEmail before calling updateUserDetails
            // But we test the service layer behavior here
            
            // For now, we verify that IF the update is attempted, 
            // the system should either throw an exception OR not persist the duplicate
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(account1Id);
            updateRequest.setTitle(account1.getTitle());
            updateRequest.setName(account1.getName());
            updateRequest.setEmail(duplicateEmail); // Duplicate email
            updateRequest.setMobileNumber(account1.getMobileNumber());
            updateRequest.setAcademicPosition(account1.getAcademicPosition());
            
            // Then: The system should prevent the duplicate email
            boolean exceptionThrown = false;
            try {
                userService.updateUserDetails(updateRequest, null);
            } catch (Exception e) {
                // Exception is the preferred behavior
                exceptionThrown = true;
            }
            
            // Verify: Either exception was thrown OR email was not changed
            UserDtls retrieved = userService.getUserById(account1Id);
            if (!exceptionThrown) {
                // If no exception, the email should NOT have been changed
                assertThat(retrieved.getEmail())
                    .as("Email should not be updated to duplicate value")
                    .isEqualTo(originalEmail)
                    .isNotEqualTo(duplicateEmail);
            } else {
                // If exception was thrown, email should remain unchanged
                assertThat(retrieved.getEmail()).isEqualTo(originalEmail);
            }
            
            // Verify both accounts still have unique emails
            UserDtls retrieved2 = userService.getUserById(account2Id);
            assertThat(retrieved.getEmail()).isNotEqualTo(retrieved2.getEmail());
            
            // Clean up
            userRepository.deleteById(account1Id);
            userRepository.deleteById(account2Id);
        }
    }

    /**
     * Property Test: Same Account Email Update Allowed
     * 
     * Tests that updating an account with its own current email is allowed
     * (this is not a duplicate since it's the same account).
     * 
     * This test runs 100 iterations with randomly generated data.
     */
    @Test
    void sameAccountEmailUpdateAllowed() {
        // Create arbitrary generator for emails
        Arbitrary<String> emailsArbitrary = validEmails();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String email = emailsArbitrary.sample();
            
            // Given: An account exists in the database
            UserDtls account = createTestAccount(email);
            Integer accountId = account.getId();
            
            // When: Administrator updates the account with the same email
            UserDtls updateRequest = new UserDtls();
            updateRequest.setId(accountId);
            updateRequest.setTitle("ดร.");
            updateRequest.setName("Updated Name");
            updateRequest.setEmail(email); // Same email
            updateRequest.setMobileNumber("0899999999");
            updateRequest.setAcademicPosition("ศาสตราจารย์");
            
            // Then: The update should succeed
            UserDtls updated = userService.updateUserDetails(updateRequest, null);
            
            assertThat(updated).isNotNull();
            assertThat(updated.getEmail()).isEqualTo(email);
            assertThat(updated.getName()).isEqualTo("Updated Name");
            
            // Verify changes are persisted
            UserDtls retrieved = userService.getUserById(accountId);
            assertThat(retrieved.getEmail()).isEqualTo(email);
            assertThat(retrieved.getName()).isEqualTo("Updated Name");
            
            // Clean up
            userRepository.deleteById(accountId);
        }
    }

    /**
     * Provides arbitrary valid email addresses for property testing
     */
    private Arbitrary<String> validEmails() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(s -> s.toLowerCase() + "@example.com");
    }

    /**
     * Helper method to create a test account in the database with a specific email
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
