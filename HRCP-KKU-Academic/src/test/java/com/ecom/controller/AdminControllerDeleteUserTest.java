package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

/**
 * Unit tests for AdminController's delete-user endpoint
 * 
 * Tests Requirements: 3.2, 3.4, 3.5, 6.3
 * 
 * Test Coverage:
 * - GET /admin/delete-user removes user and logs action
 * - Deletion of non-existent account returns error
 * - Deletion prevention when not allowed
 * - Audit logging with correct parameters
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "app.audit-log.async=false"
})
public class AdminControllerDeleteUserTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    private UserDtls testUser;
    private UserDtls testAdmin;
    private MockHttpSession session;
    private MockPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        adminLogRepository.deleteAll();
        session = new MockHttpSession();

        // Setup test user to delete
        testUser = new UserDtls();
        testUser.setTitle("นาย");
        testUser.setName("Test User");
        testUser.setEmail("user@example.com");
        testUser.setMobileNumber("0812345678");
        testUser.setAcademicPosition("อาจารย์");
        testUser.setPassword("encodedPassword");
        testUser.setRole("ROLE_USER");
        testUser.setIsEnable(true);
        testUser.setAccountNonLocked(true);
        testUser.setFailedAttempt(0);
        testUser.setProfileImage("default.png");
        testUser = userRepository.save(testUser);

        // Setup admin user for principal
        testAdmin = new UserDtls();
        testAdmin.setName("Admin User");
        testAdmin.setEmail("admin@example.com");
        testAdmin.setPassword("encodedPassword");
        testAdmin.setRole("ROLE_ADMIN");
        testAdmin.setIsEnable(true);
        testAdmin.setAccountNonLocked(true);
        testAdmin.setFailedAttempt(0);
        testAdmin.setProfileImage("default.png");
        testAdmin = userRepository.save(testAdmin);

        mockPrincipal = new MockPrincipal("admin@example.com");
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        adminLogRepository.deleteAll();
    }

    // Helper class for mock Principal
    private static class MockPrincipal implements java.security.Principal {
        private final String name;

        public MockPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * Test GET /admin/delete-user removes user and logs action
     * Validates Requirements 3.2, 3.4, 6.3: Delete user and create audit log
     */
    @Test
    void testDeleteUser_WithValidUser_ShouldDeleteAndLog() {
        // Given: A valid user to delete
        Integer userId = testUser.getId();
        String userEmail = testUser.getEmail();

        // When: Admin deletes the user
        String result = adminController.deleteUser(userId, 1, "user@example.com", session, mockPrincipal);

        // Then: User should be deleted and action logged
        assertThat(result).isEqualTo("redirect:/admin/users?type=1");
        assertThat(session.getAttribute("succMsg")).isEqualTo("ลบบัญชีผู้ใช้สำเร็จ");

        // Verify user was deleted from database
        UserDtls deletedUser = userService.getUserById(userId);
        assertThat(deletedUser).isNull();

        // Verify audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isNotEmpty();

        AdminLog log = logs.get(0);
        assertThat(log.getAdminEmail()).isEqualTo("admin@example.com");
        assertThat(log.getAdminName()).isEqualTo("Admin User");
        assertThat(log.getAction()).isEqualTo("DELETE_USER_ACCOUNT");
        assertThat(log.getDetails()).contains("ลบบัญชีผู้ใช้ ID:" + userId);
        assertThat(log.getDetails()).contains(userEmail);
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test deletion of non-existent account returns error
     * Validates Requirement 3.5: Handle deletion failures
     */
    @Test
    void testDeleteUser_WhenUserNotFound_ShouldReturnError() {
        // Given: User does not exist
        Integer nonExistentId = 999;

        // When: Admin tries to delete non-existent user
        String result = adminController.deleteUser(nonExistentId, 1, "nonexistent@example.com", session, mockPrincipal);

        // Then: Should return error message
        assertThat(result).isEqualTo("redirect:/admin/users?type=1");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("ไม่พบบัญชีที่ระบุ");

        // Verify no audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    /**
     * Test deletion prevention when not allowed
     * Validates that canDeleteUser check is enforced
     */
    @Test
    void testDeleteUser_WhenDeletionNotAllowed_ShouldReturnError() {
        // Given: Admin tries to delete their own account (self-deletion)
        Integer adminId = testAdmin.getId();

        // When: Admin tries to delete their own account
        String result = adminController.deleteUser(adminId, 2, "admin@example.com", session, mockPrincipal);

        // Then: Should return error message
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("ไม่สามารถลบบัญชีนี้ได้");

        // Verify admin account still exists
        UserDtls admin = userService.getUserById(adminId);
        assertThat(admin).isNotNull();
        assertThat(admin.getEmail()).isEqualTo("admin@example.com");

        // Verify no audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    /**
     * Test audit logging contains all required information
     * Validates Requirement 6.3: Audit log with administrator details, timestamp, and IP
     */
    @Test
    void testDeleteUser_AuditLogContainsRequiredInformation() {
        // Given: A valid user to delete
        Integer userId = testUser.getId();
        String userEmail = testUser.getEmail();

        // When: Admin deletes the user
        adminController.deleteUser(userId, 1, "user@example.com", session, mockPrincipal);

        // Then: Audit log contains all required information
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).hasSize(1);

        AdminLog log = logs.get(0);
        // Administrator's email
        assertThat(log.getAdminEmail()).isEqualTo("admin@example.com");
        // Administrator's name
        assertThat(log.getAdminName()).isEqualTo("Admin User");
        // Action type
        assertThat(log.getAction()).isEqualTo("DELETE_USER_ACCOUNT");
        // Details of modification (user ID and email)
        assertThat(log.getDetails()).contains("ลบบัญชีผู้ใช้");
        assertThat(log.getDetails()).contains("ID:" + userId);
        assertThat(log.getDetails()).contains(userEmail);
        // Timestamp
        assertThat(log.getTimestamp()).isNotNull();
        // IP address
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test multiple users can be deleted sequentially
     * Validates that deletion works correctly for multiple operations
     */
    @Test
    void testDeleteUser_MultipleUsers_CanBeDeletedSequentially() {
        // Given: Multiple users exist
        UserDtls user2 = new UserDtls();
        user2.setName("User 2");
        user2.setEmail("user2@example.com");
        user2.setPassword("encodedPassword");
        user2.setRole("ROLE_USER");
        user2.setIsEnable(true);
        user2.setAccountNonLocked(true);
        user2.setFailedAttempt(0);
        user2.setProfileImage("default.png");
        user2 = userRepository.save(user2);

        // When: Admin deletes both users
        adminController.deleteUser(testUser.getId(), 1, "user@example.com", session, mockPrincipal);
        adminController.deleteUser(user2.getId(), 1, "user2@example.com", session, mockPrincipal);

        // Then: Both users should be deleted
        assertThat(userService.getUserById(testUser.getId())).isNull();
        assertThat(userService.getUserById(user2.getId())).isNull();

        // Verify two audit logs were created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(log -> log.getAction().equals("DELETE_USER_ACCOUNT"));
    }
}
