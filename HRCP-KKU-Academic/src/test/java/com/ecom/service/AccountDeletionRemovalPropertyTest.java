package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Property-Based Test for Account Deletion Removal
 * 
 * **Validates: Requirements 3.2, 4.2**
 * 
 * Property 4: Account Deletion Removal
 * For any account (user or admin) that exists in the system, when an administrator 
 * confirms deletion, the account should be removed from the database and subsequent 
 * queries for that account ID should return null or not found.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AccountDeletionRemovalPropertyTest {

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
     * Property Test: Account Deletion Removal for User Accounts
     * 
     * Tests that for any user account that exists in the system, when an administrator
     * confirms deletion, the account is removed from the database and subsequent queries
     * return null.
     * 
     * This test runs 100 iterations with randomly generated user data.
     */
    @Test
    void userAccountDeletionRemoval() {
        // Create arbitrary generator for user data
        Arbitrary<String> rolesArbitrary = Arbitraries.of("ROLE_USER");
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String role = rolesArbitrary.sample();
            
            // Given: A user account exists in the database
            UserDtls user = createTestAccount(role, i);
            Integer userId = user.getId();
            
            // Verify the account exists before deletion
            UserDtls retrievedBeforeDeletion = userService.getUserById(userId);
            assertThat(retrievedBeforeDeletion).isNotNull();
            assertThat(retrievedBeforeDeletion.getId()).isEqualTo(userId);
            
            // When: Administrator confirms deletion
            Boolean deletionResult = userService.deleteUserById(userId);
            
            // Then: The deletion should succeed
            assertThat(deletionResult).isTrue();
            
            // And: Subsequent queries for that account ID should return null
            UserDtls retrievedAfterDeletion = userService.getUserById(userId);
            assertThat(retrievedAfterDeletion).isNull();
        }
    }

    /**
     * Property Test: Account Deletion Removal for Admin Accounts
     * 
     * Tests that for any admin account that exists in the system, when an administrator
     * confirms deletion, the account is removed from the database and subsequent queries
     * return null.
     * 
     * This test runs 100 iterations with randomly generated admin data.
     */
    @Test
    void adminAccountDeletionRemoval() {
        // Create arbitrary generator for admin data
        Arbitrary<String> rolesArbitrary = Arbitraries.of("ROLE_ADMIN");
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            String role = rolesArbitrary.sample();
            
            // Given: An admin account exists in the database
            UserDtls admin = createTestAccount(role, i);
            Integer adminId = admin.getId();
            
            // Verify the account exists before deletion
            UserDtls retrievedBeforeDeletion = userService.getUserById(adminId);
            assertThat(retrievedBeforeDeletion).isNotNull();
            assertThat(retrievedBeforeDeletion.getId()).isEqualTo(adminId);
            assertThat(retrievedBeforeDeletion.getRole()).isEqualTo("ROLE_ADMIN");
            
            // When: Administrator confirms deletion
            Boolean deletionResult = userService.deleteUserById(adminId);
            
            // Then: The deletion should succeed
            assertThat(deletionResult).isTrue();
            
            // And: Subsequent queries for that account ID should return null
            UserDtls retrievedAfterDeletion = userService.getUserById(adminId);
            assertThat(retrievedAfterDeletion).isNull();
        }
    }

    /**
     * Property Test: Deletion of Non-Existent Account
     * 
     * Tests that attempting to delete a non-existent account returns false
     * and does not cause errors.
     * 
     * This test runs 100 iterations with randomly generated non-existent IDs.
     */
    @Test
    void nonExistentAccountDeletionHandling() {
        // Create arbitrary generator for non-existent IDs
        Arbitrary<Integer> nonExistentIdsArbitrary = Arbitraries.integers()
            .between(10000, 99999);
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            Integer nonExistentId = nonExistentIdsArbitrary.sample();
            
            // Given: No account exists with this ID
            UserDtls retrieved = userService.getUserById(nonExistentId);
            assertThat(retrieved).isNull();
            
            // When: Administrator attempts to delete the non-existent account
            Boolean deletionResult = userService.deleteUserById(nonExistentId);
            
            // Then: The deletion should return false (not found)
            assertThat(deletionResult).isFalse();
        }
    }

    /**
     * Property Test: Multiple Deletions Idempotency
     * 
     * Tests that deleting an account multiple times is idempotent - the first
     * deletion succeeds, and subsequent deletions return false without errors.
     * 
     * This test runs 100 iterations with randomly generated account data.
     */
    @Test
    void multipleDeletionsIdempotency() {
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            // Given: An account exists in the database
            UserDtls user = createTestAccount("ROLE_USER", i);
            Integer userId = user.getId();
            
            // Verify the account exists
            assertThat(userService.getUserById(userId)).isNotNull();
            
            // When: Administrator deletes the account the first time
            Boolean firstDeletion = userService.deleteUserById(userId);
            
            // Then: The first deletion should succeed
            assertThat(firstDeletion).isTrue();
            assertThat(userService.getUserById(userId)).isNull();
            
            // When: Administrator attempts to delete the same account again
            Boolean secondDeletion = userService.deleteUserById(userId);
            
            // Then: The second deletion should return false (already deleted)
            assertThat(secondDeletion).isFalse();
            
            // And: The account should still not exist
            assertThat(userService.getUserById(userId)).isNull();
        }
    }

    /**
     * Helper method to create a test account in the database with a specific role
     */
    private UserDtls createTestAccount(String role, int iteration) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User " + iteration + " " + System.nanoTime());
        user.setEmail("test" + iteration + System.currentTimeMillis() + System.nanoTime() + "@test.com");
        user.setMobileNumber("08" + String.format("%08d", iteration));
        user.setAcademicPosition("อาจารย์");
        user.setPassword("encodedPassword123");
        user.setRole(role);
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setProfileImage("default.png");
        
        return userRepository.save(user);
    }
}
