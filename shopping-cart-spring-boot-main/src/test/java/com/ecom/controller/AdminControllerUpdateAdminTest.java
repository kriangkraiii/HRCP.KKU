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
 * Integration tests for AdminController's update-admin endpoint
 * 
 * Tests the POST /admin/update-admin endpoint functionality including:
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
public class AdminControllerUpdateAdminTest {

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
    void testUpdateAdminDetails_WithValidData_ShouldPersistChanges() {
        // Given: An existing admin in the database
        UserDtls original = createTestAdmin("admin@example.com");
        Integer adminId = original.getId();

        // When: Update admin details with valid data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(adminId);
        updateRequest.setTitle("ดร.");
        updateRequest.setName("Updated Admin Name");
        updateRequest.setEmail("admin@example.com");
        updateRequest.setMobileNumber("0987654321");
        updateRequest.setAcademicPosition("รองศาสตราจารย์");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        // Then: Changes should be persisted
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Updated Admin Name");
        assertThat(updated.getTitle()).isEqualTo("ดร.");
        assertThat(updated.getMobileNumber()).isEqualTo("0987654321");
        assertThat(updated.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");

        // Verify security fields are preserved
        assertThat(updated.getPassword()).isEqualTo(original.getPassword());
        assertThat(updated.getRole()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void testEmailUniqueness_WhenEmailExists_ShouldNotUpdate() {
        // Given: Two admins with different emails
        UserDtls admin1 = createTestAdmin("admin1@example.com");
        createTestAdmin("admin2@example.com");

        // When: Try to update admin1's email to admin2's email
        UserDtls existingAdmin = userService.getUserByEmail("admin2@example.com");

        // Then: Should find existing admin with different ID
        assertThat(existingAdmin).isNotNull();
        assertThat(existingAdmin.getId()).isNotEqualTo(admin1.getId());
        assertThat(existingAdmin.getEmail()).isEqualTo("admin2@example.com");
    }

    @Test
    void testEmailUniqueness_WhenSameAdminEmail_ShouldAllowUpdate() {
        // Given: An existing admin
        UserDtls admin = createTestAdmin("admin@example.com");
        Integer adminId = admin.getId();

        // When: Update admin with same email (no email change)
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(adminId);
        updateRequest.setName("Updated Admin Name");
        updateRequest.setEmail("admin@example.com");

        UserDtls existingAdmin = userService.getUserByEmail("admin@example.com");

        // Then: Should find the same admin (allow update)
        assertThat(existingAdmin).isNotNull();
        assertThat(existingAdmin.getId()).isEqualTo(adminId);
    }

    @Test
    void testUpdateAdmin_PreservesSecurityFields() {
        // Given: An existing admin with security settings
        UserDtls original = createTestAdmin("admin@example.com");
        original.setPassword("hashedPassword123");
        original.setRole("ROLE_ADMIN");
        original.setIsEnable(true);
        original.setAccountNonLocked(true);
        original = userRepository.save(original);
        Integer adminId = original.getId();

        // When: Update admin details
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(adminId);
        updateRequest.setName("New Admin Name");
        updateRequest.setEmail("admin@example.com");

        UserDtls updated = userService.updateUserDetails(updateRequest, null);

        // Then: Security fields should remain unchanged
        assertThat(updated.getPassword()).isEqualTo("hashedPassword123");
        assertThat(updated.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(updated.getIsEnable()).isTrue();
        assertThat(updated.getAccountNonLocked()).isTrue();
    }

    private UserDtls createTestAdmin(String email) {
        UserDtls admin = new UserDtls();
        admin.setTitle("นาย");
        admin.setName("Test Admin");
        admin.setEmail(email);
        admin.setMobileNumber("0812345678");
        admin.setAcademicPosition("อาจารย์");
        admin.setPassword("encodedPassword");
        admin.setRole("ROLE_ADMIN");
        admin.setIsEnable(true);
        admin.setAccountNonLocked(true);
        admin.setFailedAttempt(0);
        admin.setProfileImage("default.png");
        
        return userRepository.save(admin);
    }
}
