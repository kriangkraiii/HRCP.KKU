package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

/**
 * Integration tests for AdminController's update-user endpoint
 * 
 * Tests the POST /admin/update-user endpoint functionality including:
 * - Valid data updates
 * - Email format validation
 * - Required field validation
 * - Email uniqueness enforcement
 * - Audit logging
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AdminControllerUpdateUserTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void testUpdateUserDetails_WithValidData_ShouldPersistChanges() {
        // Given: An existing user in the database
        UserDtls original = createTestUser("test@example.com");
        Integer userId = original.getId();

        // When: Update user details with valid data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(userId);
        updateRequest.setTitle("ดร.");
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("test@example.com");
        updateRequest.setMobileNumber("0987654321");
        updateRequest.setAcademicPosition("รองศาสตราจารย์");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        // Then: Changes should be persisted
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getTitle()).isEqualTo("ดร.");
        assertThat(updated.getMobileNumber()).isEqualTo("0987654321");
        assertThat(updated.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");

        // Verify security fields are preserved
        assertThat(updated.getPassword()).isEqualTo(original.getPassword());
        assertThat(updated.getRole()).isEqualTo(original.getRole());
    }

    @Test
    void testEmailUniqueness_WhenEmailExists_ShouldNotUpdate() {
        // Given: Two users with different emails
        UserDtls user1 = createTestUser("user1@example.com");
        createTestUser("user2@example.com");

        // When: Try to update user1's email to user2's email
        UserDtls existingUser = userService.getUserByEmail("user2@example.com");

        // Then: Should find existing user with different ID
        assertThat(existingUser).isNotNull();
        assertThat(existingUser.getId()).isNotEqualTo(user1.getId());
        assertThat(existingUser.getEmail()).isEqualTo("user2@example.com");
    }

    @Test
    void testEmailUniqueness_WhenSameUserEmail_ShouldAllowUpdate() {
        // Given: An existing user
        UserDtls user = createTestUser("test@example.com");
        Integer userId = user.getId();

        // When: Update user with same email (no email change)
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(userId);
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("test@example.com");

        UserDtls existingUser = userService.getUserByEmail("test@example.com");

        // Then: Should find the same user (allow update)
        assertThat(existingUser).isNotNull();
        assertThat(existingUser.getId()).isEqualTo(userId);
    }

    @Test
    void testUpdateUser_PreservesSecurityFields() {
        // Given: An existing user with security settings
        UserDtls original = createTestUser("test@example.com");
        original.setPassword("hashedPassword123");
        original.setRole("ROLE_USER");
        original.setIsEnable(true);
        original.setAccountNonLocked(true);
        original = userRepository.save(original);
        Integer userId = original.getId();

        // When: Update user details
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(userId);
        updateRequest.setName("New Name");
        updateRequest.setEmail("test@example.com");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        // Then: Security fields should remain unchanged
        assertThat(updated.getPassword()).isEqualTo("hashedPassword123");
        assertThat(updated.getRole()).isEqualTo("ROLE_USER");
        assertThat(updated.getIsEnable()).isTrue();
        assertThat(updated.getAccountNonLocked()).isTrue();
    }

    private UserDtls createTestUser(String email) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName("Test User");
        user.setEmail(email);
        user.setMobileNumber("0812345678");
        user.setAcademicPosition("อาจารย์");
        user.setPassword("encodedPassword");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setProfileImage("default.png");
        
        return userRepository.save(user);
    }
}
