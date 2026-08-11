package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;
import com.ecom.service.UserService;

import java.util.List;

/**
 * Unit tests for AdminController's edit admin endpoints
 * 
 * Tests Requirements: 2.1, 2.2, 2.3, 2.4, 6.2
 * 
 * Test Coverage:
 * - GET /admin/edit-admin returns correct view with admin data
 * - POST /admin/update-admin with valid data updates admin
 * - POST /admin/update-admin with duplicate email returns error
 * - Audit logging is called with correct parameters
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
public class AdminControllerEditAdminEndpointTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    private UserDtls testAdmin;
    private UserDtls performingAdmin;
    private MockHttpSession session;
    private MockPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        adminLogRepository.deleteAll();
        session = new MockHttpSession();

        // Setup test admin to be edited
        testAdmin = new UserDtls();
        testAdmin.setTitle("นาย");
        testAdmin.setName("Test Admin");
        testAdmin.setEmail("testadmin@example.com");
        testAdmin.setMobileNumber("0812345678");
        testAdmin.setAcademicPosition("อาจารย์");
        testAdmin.setPassword("encodedPassword");
        testAdmin.setRole("ROLE_ADMIN");
        testAdmin.setIsEnable(true);
        testAdmin.setAccountNonLocked(true);
        testAdmin.setFailedAttempt(0);
        testAdmin.setProfileImage("default.png");
        testAdmin = userRepository.save(testAdmin);

        // Setup performing admin user for principal
        performingAdmin = new UserDtls();
        performingAdmin.setName("Performing Admin");
        performingAdmin.setEmail("admin@example.com");
        performingAdmin.setPassword("encodedPassword");
        performingAdmin.setRole("ROLE_ADMIN");
        performingAdmin.setIsEnable(true);
        performingAdmin.setAccountNonLocked(true);
        performingAdmin.setFailedAttempt(0);
        performingAdmin.setProfileImage("default.png");
        performingAdmin = userRepository.save(performingAdmin);

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
     * Test GET /admin/edit-admin returns correct view with admin data
     * Validates Requirement 2.1: Display form pre-populated with current account
     * details
     */
    @Test
    void testGetEditAdmin_ReturnsCorrectViewWithAdminData() {
        // Given: An admin exists in the system
        Model model = new ExtendedModelMap();

        // When: Admin requests edit admin page
        String viewName = adminController.loadEditAdmin(testAdmin.getId(), model);

        // Then: Returns correct view
        assertThat(viewName).isEqualTo("admin/edit_admin");

        // Verify admin was added to model
        assertThat(model.containsAttribute("admin")).isTrue();
        UserDtls modelAdmin = (UserDtls) model.getAttribute("admin");
        assertThat(modelAdmin).isNotNull();
        assertThat(modelAdmin.getId()).isEqualTo(testAdmin.getId());
        assertThat(modelAdmin.getEmail()).isEqualTo(testAdmin.getEmail());
        assertThat(modelAdmin.getRole()).isEqualTo("ROLE_ADMIN");
    }

    /**
     * Test POST /admin/update-admin with valid data updates admin
     * Validates Requirements 2.2, 2.3: Validate input and persist changes
     */
    @Test
    void testPostUpdateAdmin_WithValidData_UpdatesAdmin() {
        // Given: Valid update data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setTitle("ดร.");
        updateRequest.setName("Updated Admin Name");
        updateRequest.setEmail("testadmin@example.com");
        updateRequest.setMobileNumber("0987654321");
        updateRequest.setAcademicPosition("รองศาสตราจารย์");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits valid update
        String result = adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Redirects with success message
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("succMsg")).isEqualTo("อัพเดทข้อมูลแอดมินสำเร็จ");

        // Verify admin was updated in database
        UserDtls updated = userService.getUserById(testAdmin.getId());
        assertThat(updated.getName()).isEqualTo("Updated Admin Name");
        assertThat(updated.getTitle()).isEqualTo("ดร.");
        assertThat(updated.getMobileNumber()).isEqualTo("0987654321");
        assertThat(updated.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");
        assertThat(updated.getRole()).isEqualTo("ROLE_ADMIN");
    }

    /**
     * Test POST /admin/update-admin with duplicate email returns error
     * Validates Requirement 2.4: Reject update if email already exists
     */
    @Test
    void testPostUpdateAdmin_WithDuplicateEmail_ReturnsError() {
        // Given: Another admin with different email exists
        UserDtls anotherAdmin = new UserDtls();
        anotherAdmin.setName("Another Admin");
        anotherAdmin.setEmail("another@example.com");
        anotherAdmin.setPassword("encodedPassword");
        anotherAdmin.setRole("ROLE_ADMIN");
        anotherAdmin.setIsEnable(true);
        anotherAdmin.setAccountNonLocked(true);
        anotherAdmin.setFailedAttempt(0);
        anotherAdmin.setProfileImage("default.png");
        anotherAdmin = userRepository.save(anotherAdmin);

        // When: Try to update testAdmin with anotherAdmin's email
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("Test Admin");
        updateRequest.setEmail("another@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        String result = adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-admin?id=" + testAdmin.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("อีเมลนี้มีในระบบแล้ว");

        // Verify admin email was not changed
        UserDtls unchanged = userService.getUserById(testAdmin.getId());
        assertThat(unchanged.getEmail()).isEqualTo("testadmin@example.com");
    }

    /**
     * Test audit logging is called with correct parameters
     * Validates Requirement 6.2: Create audit log entry for admin account edits
     */
    @Test
    void testPostUpdateAdmin_CallsAuditLogging_WithCorrectParameters() {
        // Given: Valid update data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("Updated Admin Name");
        updateRequest.setEmail("testadmin@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin successfully updates admin account
        adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Audit log is created with correct parameters
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isNotEmpty();

        AdminLog log = logs.get(0);
        assertThat(log.getAdminEmail()).isEqualTo("admin@example.com");
        assertThat(log.getAdminName()).isEqualTo("Performing Admin");
        assertThat(log.getAction()).isEqualTo("EDIT_ADMIN_ACCOUNT");
        assertThat(log.getDetails()).contains("แก้ไขบัญชีแอดมิน ID:" + testAdmin.getId());
        assertThat(log.getDetails()).contains(testAdmin.getEmail());
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test POST /admin/update-admin allows same admin to keep their email
     * Validates that email uniqueness check excludes current admin
     */
    @Test
    void testPostUpdateAdmin_WithSameEmail_AllowsUpdate() {
        // Given: Admin keeps their own email
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("Updated Admin Name");
        updateRequest.setEmail("testadmin@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin updates admin account with same email
        String result = adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Update succeeds
        assertThat(result).isEqualTo("redirect:/admin/users?type=2");
        assertThat(session.getAttribute("succMsg")).isEqualTo("อัพเดทข้อมูลแอดมินสำเร็จ");

        // Verify admin was updated
        UserDtls updated = userService.getUserById(testAdmin.getId());
        assertThat(updated.getName()).isEqualTo("Updated Admin Name");
        assertThat(updated.getEmail()).isEqualTo("testadmin@example.com");
    }

    /**
     * Test POST /admin/update-admin with invalid email format returns error
     * Validates Requirement 7.1: Validate email format
     */
    @Test
    void testPostUpdateAdmin_WithInvalidEmailFormat_ReturnsError() {
        // Given: Invalid email format
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("Test Admin");
        updateRequest.setEmail("invalid-email");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits update with invalid email
        String result = adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-admin?id=" + testAdmin.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("รูปแบบอีเมลไม่ถูกต้อง");
    }

    /**
     * Test POST /admin/update-admin with empty name returns error
     * Validates Requirement 7.2: Validate required fields
     */
    @Test
    void testPostUpdateAdmin_WithEmptyName_ReturnsError() {
        // Given: Empty name field
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("");
        updateRequest.setEmail("testadmin@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits update with empty name
        String result = adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-admin?id=" + testAdmin.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("กรุณากรอกชื่อและนามสกุล");
    }

    /**
     * Test POST /admin/update-admin preserves security fields
     * Validates that password, role, and security settings remain unchanged
     */
    @Test
    void testPostUpdateAdmin_PreservesSecurityFields() {
        // Given: An existing admin with security settings
        String originalPassword = testAdmin.getPassword();
        String originalRole = testAdmin.getRole();
        Boolean originalEnabled = testAdmin.getIsEnable();
        Boolean originalLocked = testAdmin.getAccountNonLocked();

        // When: Update admin details
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testAdmin.getId());
        updateRequest.setName("New Admin Name");
        updateRequest.setEmail("testadmin@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        adminController.updateAdmin(updateRequest, emptyFile, 2, session, mockPrincipal);

        // Then: Security fields should remain unchanged
        UserDtls updated = userService.getUserById(testAdmin.getId());
        assertThat(updated.getPassword()).isEqualTo(originalPassword);
        assertThat(updated.getRole()).isEqualTo(originalRole);
        assertThat(updated.getIsEnable()).isEqualTo(originalEnabled);
        assertThat(updated.getAccountNonLocked()).isEqualTo(originalLocked);
    }
}
