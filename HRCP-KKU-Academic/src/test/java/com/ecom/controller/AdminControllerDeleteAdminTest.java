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
 * Unit tests for AdminController's delete-admin endpoint
 * 
 * Tests Requirements: 4.2, 4.4, 4.5, 6.4
 * 
 * Test Coverage:
 * - GET /admin/delete-admin removes admin and logs action
 * - Self-deletion prevention returns error
 * - Deletion of non-existent account returns error
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
public class AdminControllerDeleteAdminTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    private UserDtls testAdmin1;
    private UserDtls testAdmin2;
    private MockHttpSession session;
    private MockPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        adminLogRepository.deleteAll();
        session = new MockHttpSession();

        // Setup first admin (the one performing actions)
        testAdmin1 = new UserDtls();
        testAdmin1.setTitle("นาย");
        testAdmin1.setName("Admin User 1");
        testAdmin1.setEmail("admin1@example.com");
        testAdmin1.setMobileNumber("0812345678");
        testAdmin1.setAcademicPosition("ผู้ช่วยศาสตราจารย์");
        testAdmin1.setPassword("encodedPassword");
        testAdmin1.setRole("ROLE_ADMIN");
        testAdmin1.setIsEnable(true);
        testAdmin1.setAccountNonLocked(true);
        testAdmin1.setFailedAttempt(0);
        testAdmin1.setProfileImage("default.png");
        testAdmin1 = userRepository.save(testAdmin1);

        // Setup second admin (to be deleted)
        testAdmin2 = new UserDtls();
        testAdmin2.setTitle("นาง");
        testAdmin2.setName("Admin User 2");
        testAdmin2.setEmail("admin2@example.com");
        testAdmin2.setMobileNumber("0823456789");
        testAdmin2.setAcademicPosition("รองศาสตราจารย์");
        testAdmin2.setPassword("encodedPassword");
        testAdmin2.setRole("ROLE_ADMIN");
        testAdmin2.setIsEnable(true);
        testAdmin2.setAccountNonLocked(true);
        testAdmin2.setFailedAttempt(0);
        testAdmin2.setProfileImage("default.png");
        testAdmin2 = userRepository.save(testAdmin2);

        mockPrincipal = new MockPrincipal("admin1@example.com");
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
     * Test GET /admin/delete-admin removes admin and logs action
     * Validates Requirements 4.2, 4.4, 6.4: Delete admin and create audit log
     */
    @Test
    void testDeleteAdmin_WithValidAdmin_ShouldDeleteAndLog() {
        // Given: A valid admin to delete
        Integer adminId = testAdmin2.getId();
        String adminEmail = testAdmin2.getEmail();

        // When: Admin deletes another admin
        String result = adminController.deleteAdmin(adminId, 2, "admin2@example.com", session, mockPrincipal);

        // Then: Admin should be deleted and action logged
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("succMsg")).isEqualTo("ลบบัญชีแอดมินสำเร็จ");

        // Verify admin was deleted from database
        UserDtls deletedAdmin = userService.getUserById(adminId);
        assertThat(deletedAdmin).isNull();

        // Verify audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isNotEmpty();

        AdminLog log = logs.get(0);
        assertThat(log.getAdminEmail()).isEqualTo("admin1@example.com");
        assertThat(log.getAdminName()).isEqualTo("Admin User 1");
        assertThat(log.getAction()).isEqualTo("DELETE_ADMIN_ACCOUNT");
        assertThat(log.getDetails()).contains("ลบบัญชีแอดมิน ID:" + adminId);
        assertThat(log.getDetails()).contains(adminEmail);
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test self-deletion prevention returns error
     * Validates Requirement 4.5: Prevent administrators from deleting their own account
     */
    @Test
    void testDeleteAdmin_WhenSelfDeletion_ShouldReturnError() {
        // Given: Admin tries to delete their own account
        Integer adminId = testAdmin1.getId();

        // When: Admin tries to delete their own account
        String result = adminController.deleteAdmin(adminId, 2, "admin1@example.com", session, mockPrincipal);

        // Then: Should return error message
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("ไม่สามารถลบบัญชีของตัวเองได้");

        // Verify admin account still exists
        UserDtls admin = userService.getUserById(adminId);
        assertThat(admin).isNotNull();
        assertThat(admin.getEmail()).isEqualTo("admin1@example.com");

        // Verify no audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    /**
     * Test deletion of non-existent account returns error
     * Validates that proper error handling occurs for invalid admin IDs
     */
    @Test
    void testDeleteAdmin_WhenAdminNotFound_ShouldReturnError() {
        // Given: Admin does not exist
        Integer nonExistentId = 999;

        // When: Admin tries to delete non-existent admin
        String result = adminController.deleteAdmin(nonExistentId, 2, "nonexistent@example.com", session, mockPrincipal);

        // Then: Should return error message
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("errorMsg")).isEqualTo("ไม่พบบัญชีที่ระบุ");

        // Verify no audit log was created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    /**
     * Test audit logging contains all required information
     * Validates Requirement 6.4: Audit log with administrator details, timestamp, and IP
     */
    @Test
    void testDeleteAdmin_AuditLogContainsRequiredInformation() {
        // Given: A valid admin to delete
        Integer adminId = testAdmin2.getId();
        String adminEmail = testAdmin2.getEmail();

        // When: Admin deletes another admin
        adminController.deleteAdmin(adminId, 2, "admin2@example.com", session, mockPrincipal);

        // Then: Audit log contains all required information
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).hasSize(1);

        AdminLog log = logs.get(0);
        // Administrator's email
        assertThat(log.getAdminEmail()).isEqualTo("admin1@example.com");
        // Administrator's name
        assertThat(log.getAdminName()).isEqualTo("Admin User 1");
        // Action type
        assertThat(log.getAction()).isEqualTo("DELETE_ADMIN_ACCOUNT");
        // Details of modification (admin ID and email)
        assertThat(log.getDetails()).contains("ลบบัญชีแอดมิน");
        assertThat(log.getDetails()).contains("ID:" + adminId);
        assertThat(log.getDetails()).contains(adminEmail);
        // Timestamp
        assertThat(log.getTimestamp()).isNotNull();
        // IP address
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test multiple admins can be deleted sequentially
     * Validates that deletion works correctly for multiple operations
     */
    @Test
    void testDeleteAdmin_MultipleAdmins_CanBeDeletedSequentially() {
        // Given: Multiple admins exist
        UserDtls admin3 = new UserDtls();
        admin3.setName("Admin User 3");
        admin3.setEmail("admin3@example.com");
        admin3.setPassword("encodedPassword");
        admin3.setRole("ROLE_ADMIN");
        admin3.setIsEnable(true);
        admin3.setAccountNonLocked(true);
        admin3.setFailedAttempt(0);
        admin3.setProfileImage("default.png");
        admin3 = userRepository.save(admin3);

        // When: Admin deletes both other admins
        adminController.deleteAdmin(testAdmin2.getId(), 2, "admin2@example.com", session, mockPrincipal);
        adminController.deleteAdmin(admin3.getId(), 2, "admin3@example.com", session, mockPrincipal);

        // Then: Both admins should be deleted
        assertThat(userService.getUserById(testAdmin2.getId())).isNull();
        assertThat(userService.getUserById(admin3.getId())).isNull();

        // Verify the performing admin still exists
        assertThat(userService.getUserById(testAdmin1.getId())).isNotNull();

        // Verify two audit logs were created
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).hasSize(2);
        assertThat(logs).allMatch(log -> log.getAction().equals("DELETE_ADMIN_ACCOUNT"));
    }

    /**
     * Test that error message is specific for self-deletion
     * Validates Requirement 4.5: Clear warning message for self-deletion attempts
     */
    @Test
    void testDeleteAdmin_SelfDeletionErrorMessage_IsSpecific() {
        // Given: Admin tries to delete their own account
        Integer adminId = testAdmin1.getId();

        // When: Admin tries self-deletion
        adminController.deleteAdmin(adminId, 2, "admin1@example.com", session, mockPrincipal);

        // Then: Error message should specifically mention self-deletion
        String errorMsg = (String) session.getAttribute("errorMsg");
        assertThat(errorMsg).isEqualTo("ไม่สามารถลบบัญชีของตัวเองได้");
        assertThat(errorMsg).contains("ตัวเอง"); // Contains "self" in Thai
    }
}
