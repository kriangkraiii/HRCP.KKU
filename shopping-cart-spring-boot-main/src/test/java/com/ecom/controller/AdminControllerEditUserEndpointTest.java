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
 * Unit tests for AdminController's edit user endpoints
 * 
 * Tests Requirements: 1.1, 1.2, 1.3, 1.4, 6.1
 * 
 * Test Coverage:
 * - GET /admin/edit-user returns correct view with user data
 * - POST /admin/update-user with valid data updates user
 * - POST /admin/update-user with duplicate email returns error
 * - POST /admin/update-user with invalid email format returns error
 * - Audit logging is called with correct parameters
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class AdminControllerEditUserEndpointTest {

    @Autowired
    private AdminController adminController;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    private UserDtls testUser;
    private MockHttpSession session;
    private MockPrincipal mockPrincipal;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        adminLogRepository.deleteAll();
        session = new MockHttpSession();
        
        // Setup test user
        testUser = new UserDtls();
        testUser.setTitle("นาย");
        testUser.setName("Test User");
        testUser.setEmail("test@example.com");
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
        UserDtls adminUser = new UserDtls();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setPassword("encodedPassword");
        adminUser.setRole("ROLE_ADMIN");
        adminUser.setIsEnable(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setFailedAttempt(0);
        adminUser.setProfileImage("default.png");
        userRepository.save(adminUser);

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
     * Test GET /admin/edit-user returns correct view with user data
     * Validates Requirement 1.1: Display form pre-populated with current account details
     */
    @Test
    void testGetEditUser_ReturnsCorrectViewWithUserData() {
        // Given: A user exists in the system
        Model model = new ExtendedModelMap();

        // When: Admin requests edit user page
        String viewName = adminController.loadEditUser(testUser.getId(), model);

        // Then: Returns correct view
        assertThat(viewName).isEqualTo("admin/edit_user");
        
        // Verify user was added to model
        assertThat(model.containsAttribute("user")).isTrue();
        UserDtls modelUser = (UserDtls) model.getAttribute("user");
        assertThat(modelUser).isNotNull();
        assertThat(modelUser.getId()).isEqualTo(testUser.getId());
        assertThat(modelUser.getEmail()).isEqualTo(testUser.getEmail());
    }

    /**
     * Test POST /admin/update-user with valid data updates user
     * Validates Requirements 1.2, 1.3: Validate input and persist changes
     */
    @Test
    void testPostUpdateUser_WithValidData_UpdatesUser() {
        // Given: Valid update data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setTitle("ดร.");
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("test@example.com");
        updateRequest.setMobileNumber("0987654321");
        updateRequest.setAcademicPosition("รองศาสตราจารย์");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits valid update
        String result = adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Redirects with success message
        assertThat(result).isEqualTo("redirect:/admin/users?type=1");
        assertThat(session.getAttribute("succMsg")).isEqualTo("อัพเดทข้อมูลผู้ใช้สำเร็จ");

        // Verify user was updated in database
        UserDtls updated = userService.getUserById(testUser.getId());
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getTitle()).isEqualTo("ดร.");
        assertThat(updated.getMobileNumber()).isEqualTo("0987654321");
        assertThat(updated.getAcademicPosition()).isEqualTo("รองศาสตราจารย์");
    }

    /**
     * Test POST /admin/update-user with duplicate email returns error
     * Validates Requirement 1.4: Reject update if email already exists
     */
    @Test
    void testPostUpdateUser_WithDuplicateEmail_ReturnsError() {
        // Given: Another user with different email exists
        UserDtls anotherUser = new UserDtls();
        anotherUser.setName("Another User");
        anotherUser.setEmail("another@example.com");
        anotherUser.setPassword("encodedPassword");
        anotherUser.setRole("ROLE_USER");
        anotherUser.setIsEnable(true);
        anotherUser.setAccountNonLocked(true);
        anotherUser.setFailedAttempt(0);
        anotherUser.setProfileImage("default.png");
        anotherUser = userRepository.save(anotherUser);

        // When: Try to update testUser with anotherUser's email
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setName("Test User");
        updateRequest.setEmail("another@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        String result = adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-user?id=" + testUser.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("อีเมลนี้มีในระบบแล้ว");

        // Verify user email was not changed
        UserDtls unchanged = userService.getUserById(testUser.getId());
        assertThat(unchanged.getEmail()).isEqualTo("test@example.com");
    }

    /**
     * Test POST /admin/update-user with invalid email format returns error
     * Validates Requirement 7.1: Validate email format
     */
    @Test
    void testPostUpdateUser_WithInvalidEmailFormat_ReturnsError() {
        // Given: Invalid email format
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setName("Test User");
        updateRequest.setEmail("invalid-email");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits update with invalid email
        String result = adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-user?id=" + testUser.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("รูปแบบอีเมลไม่ถูกต้อง");
    }

    /**
     * Test POST /admin/update-user with empty name returns error
     * Validates Requirement 7.2: Validate required fields
     */
    @Test
    void testPostUpdateUser_WithEmptyName_ReturnsError() {
        // Given: Empty name field
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setName("");
        updateRequest.setEmail("test@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin submits update with empty name
        String result = adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Redirects with error message
        assertThat(result).isEqualTo("redirect:/admin/edit-user?id=" + testUser.getId());
        assertThat(session.getAttribute("errorMsg")).isEqualTo("กรุณากรอกชื่อ");
    }

    /**
     * Test audit logging is called with correct parameters
     * Validates Requirement 6.1: Create audit log entry for user account edits
     */
    @Test
    void testPostUpdateUser_CallsAuditLogging_WithCorrectParameters() {
        // Given: Valid update data
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("test@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin successfully updates user
        adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Audit log is created with correct parameters
        List<AdminLog> logs = adminLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        
        AdminLog log = logs.get(0);
        assertThat(log.getAdminEmail()).isEqualTo("admin@example.com");
        assertThat(log.getAdminName()).isEqualTo("Admin User");
        assertThat(log.getAction()).isEqualTo("EDIT_USER_ACCOUNT");
        assertThat(log.getDetails()).contains("แก้ไขบัญชีผู้ใช้ ID:" + testUser.getId());
        assertThat(log.getIpAddress()).isNotNull();
    }

    /**
     * Test POST /admin/update-user allows same user to keep their email
     * Validates that email uniqueness check excludes current user
     */
    @Test
    void testPostUpdateUser_WithSameEmail_AllowsUpdate() {
        // Given: User keeps their own email
        UserDtls updateRequest = new UserDtls();
        updateRequest.setId(testUser.getId());
        updateRequest.setName("Updated Name");
        updateRequest.setEmail("test@example.com");

        MockMultipartFile emptyFile = new MockMultipartFile("img", "", "image/jpeg", new byte[0]);

        // When: Admin updates user with same email
        String result = adminController.updateUser(updateRequest, emptyFile, 1, session, mockPrincipal);

        // Then: Update succeeds
        assertThat(result).isEqualTo("redirect:/admin/users?type=1");
        assertThat(session.getAttribute("succMsg")).isEqualTo("อัพเดทข้อมูลผู้ใช้สำเร็จ");

        // Verify user was updated
        UserDtls updated = userService.getUserById(testUser.getId());
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getEmail()).isEqualTo("test@example.com");
    }
}
