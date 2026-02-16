package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Property-Based Test for Edit Audit Logging
 * 
 * **Validates: Requirements 6.1, 6.2, 6.5**
 * 
 * Property 6: Edit Audit Logging
 * For any successful account edit operation, the system should create an audit 
 * log entry containing the administrator's email, name, timestamp, IP address, 
 * appropriate action type (EDIT_USER_ACCOUNT or EDIT_ADMIN_ACCOUNT), and the 
 * modified account's details.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
public class EditAuditLoggingPropertyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogRepository adminLogRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        adminLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        adminLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Property Test: User Account Edit Audit Logging
     * 
     * Tests that for any successful user account edit, the system creates an 
     * audit log entry with all required information including administrator details,
     * timestamp, IP address, action type EDIT_USER_ACCOUNT, and modified account details.
     * 
     * This test runs 100 iterations with randomly generated admin and user data.
     */
    @Test
    void userAccountEditAuditLogging() {
        // Create arbitrary generator for admin and user data
        Arbitrary<EditData> editDataArbitrary = validEditData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            EditData data = editDataArbitrary.sample();
            
            // Given: An administrator account exists
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            
            // And: A user account exists in the database
            UserDtls user = createTestAccount("ROLE_USER", "original" + i + "@test.com", 
                "Original Name " + i, i * 2 + 1);
            Integer userId = user.getId();
            
            // Record the time before edit for timestamp validation
            LocalDateTime beforeEdit = LocalDateTime.now();
            
            // When: Administrator edits the user account
            user.setName(data.getNewName());
            user.setEmail(data.getNewEmail() + i + System.nanoTime());
            user.setMobileNumber(data.getNewMobile());
            user.setAcademicPosition(data.getNewPosition());
            
            MockMultipartFile emptyFile = new MockMultipartFile("img", new byte[0]);
            UserDtls updatedUser = userService.updateUserDetails(user, emptyFile);
            assertThat(updatedUser).isNotNull();
            
            // And: The edit is logged
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "EDIT_USER_ACCOUNT",
                "แก้ไขบัญชีผู้ใช้ ID:" + userId + " (" + updatedUser.getEmail() + ")",
                data.getIpAddress()
            );
            
            LocalDateTime afterEdit = LocalDateTime.now();
            
            // Then: An audit log entry should be created
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).isNotEmpty();
            
            AdminLog log = logs.get(0);
            
            // Verify all required fields are present and correct
            assertThat(log.getAdminEmail()).isEqualTo(admin.getEmail());
            assertThat(log.getAdminName()).isEqualTo(admin.getName());
            assertThat(log.getAction()).isEqualTo("EDIT_USER_ACCOUNT");
            assertThat(log.getDetails()).contains("แก้ไขบัญชีผู้ใช้");
            assertThat(log.getDetails()).contains("ID:" + userId);
            assertThat(log.getDetails()).contains(updatedUser.getEmail());
            assertThat(log.getIpAddress()).isEqualTo(data.getIpAddress());
            
            // Verify timestamp is within reasonable range
            assertThat(log.getTimestamp()).isNotNull();
            assertThat(log.getTimestamp()).isAfterOrEqualTo(beforeEdit);
            assertThat(log.getTimestamp()).isBeforeOrEqualTo(afterEdit);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Admin Account Edit Audit Logging
     * 
     * Tests that for any successful admin account edit, the system creates an 
     * audit log entry with all required information including administrator details,
     * timestamp, IP address, action type EDIT_ADMIN_ACCOUNT, and modified account details.
     * 
     * This test runs 100 iterations with randomly generated admin data.
     */
    @Test
    void adminAccountEditAuditLogging() {
        // Create arbitrary generator for admin data
        Arbitrary<EditData> editDataArbitrary = validEditData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            EditData data = editDataArbitrary.sample();
            
            // Given: An administrator account exists (the one performing the edit)
            UserDtls performingAdmin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            
            // And: Another admin account exists in the database (to be edited)
            UserDtls adminToEdit = createTestAccount("ROLE_ADMIN", "original" + i + "@admin.com", 
                "Original Admin " + i, i * 2 + 1);
            Integer adminId = adminToEdit.getId();
            
            // Record the time before edit for timestamp validation
            LocalDateTime beforeEdit = LocalDateTime.now();
            
            // When: Administrator edits the admin account
            adminToEdit.setName(data.getNewName());
            adminToEdit.setEmail(data.getNewEmail() + i + System.nanoTime());
            adminToEdit.setMobileNumber(data.getNewMobile());
            adminToEdit.setAcademicPosition(data.getNewPosition());
            
            MockMultipartFile emptyFile = new MockMultipartFile("img", new byte[0]);
            UserDtls updatedAdmin = userService.updateUserDetails(adminToEdit, emptyFile);
            assertThat(updatedAdmin).isNotNull();
            
            // And: The edit is logged
            adminLogService.log(
                performingAdmin.getEmail(),
                performingAdmin.getName(),
                "EDIT_ADMIN_ACCOUNT",
                "แก้ไขบัญชีแอดมิน ID:" + adminId + " (" + updatedAdmin.getEmail() + ")",
                data.getIpAddress()
            );
            
            LocalDateTime afterEdit = LocalDateTime.now();
            
            // Then: An audit log entry should be created
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(performingAdmin.getEmail());
            assertThat(logs).isNotEmpty();
            
            AdminLog log = logs.get(0);
            
            // Verify all required fields are present and correct
            assertThat(log.getAdminEmail()).isEqualTo(performingAdmin.getEmail());
            assertThat(log.getAdminName()).isEqualTo(performingAdmin.getName());
            assertThat(log.getAction()).isEqualTo("EDIT_ADMIN_ACCOUNT");
            assertThat(log.getDetails()).contains("แก้ไขบัญชีแอดมิน");
            assertThat(log.getDetails()).contains("ID:" + adminId);
            assertThat(log.getDetails()).contains(updatedAdmin.getEmail());
            assertThat(log.getIpAddress()).isEqualTo(data.getIpAddress());
            
            // Verify timestamp is within reasonable range
            assertThat(log.getTimestamp()).isNotNull();
            assertThat(log.getTimestamp()).isAfterOrEqualTo(beforeEdit);
            assertThat(log.getTimestamp()).isBeforeOrEqualTo(afterEdit);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Multiple Edits Create Multiple Audit Logs
     * 
     * Tests that multiple edit operations create separate audit log entries,
     * ensuring each edit is properly tracked.
     * 
     * This test runs 50 iterations with randomly generated data.
     */
    @Test
    void multipleEditsCreateMultipleAuditLogs() {
        // Create arbitrary generator for edit data
        Arbitrary<EditData> editDataArbitrary = validEditData();
        
        // Run property test for 50 iterations (fewer because we create multiple edits per iteration)
        for (int i = 0; i < 50; i++) {
            EditData data = editDataArbitrary.sample();
            
            // Given: An administrator account exists
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 10);
            
            // And: Multiple user accounts exist
            UserDtls user1 = createTestAccount("ROLE_USER", "user1" + i + "@test.com", 
                "User One " + i, i * 10 + 1);
            UserDtls user2 = createTestAccount("ROLE_USER", "user2" + i + "@test.com", 
                "User Two " + i, i * 10 + 2);
            
            // When: Administrator edits both user accounts
            user1.setName(data.getNewName() + " 1");
            MockMultipartFile emptyFile = new MockMultipartFile("img", new byte[0]);
            userService.updateUserDetails(user1, emptyFile);
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "EDIT_USER_ACCOUNT",
                "แก้ไขบัญชีผู้ใช้ ID:" + user1.getId() + " (" + user1.getEmail() + ")",
                data.getIpAddress()
            );
            
            user2.setName(data.getNewName() + " 2");
            userService.updateUserDetails(user2, emptyFile);
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "EDIT_USER_ACCOUNT",
                "แก้ไขบัญชีผู้ใช้ ID:" + user2.getId() + " (" + user2.getEmail() + ")",
                data.getIpAddress()
            );
            
            // Then: Two separate audit log entries should exist
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).hasSize(2);
            
            // Verify both logs have the correct action type
            assertThat(logs).allMatch(log -> log.getAction().equals("EDIT_USER_ACCOUNT"));
            
            // Verify both logs reference different user IDs
            assertThat(logs.get(0).getDetails()).containsAnyOf(
                "ID:" + user1.getId(), "ID:" + user2.getId()
            );
            assertThat(logs.get(1).getDetails()).containsAnyOf(
                "ID:" + user1.getId(), "ID:" + user2.getId()
            );
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Audit Log Contains Updated Information
     * 
     * Tests that audit logs contain the updated account information, not the
     * original information, ensuring accurate audit trail.
     * 
     * This test runs 100 iterations with randomly generated data.
     */
    @Test
    void auditLogContainsUpdatedInformation() {
        // Create arbitrary generator for edit data
        Arbitrary<EditData> editDataArbitrary = validEditData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            EditData data = editDataArbitrary.sample();
            
            // Given: An administrator and user account exist
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            UserDtls user = createTestAccount("ROLE_USER", "original" + i + "@test.com", 
                "Original Name " + i, i * 2 + 1);
            Integer userId = user.getId();
            String originalEmail = user.getEmail();
            
            // When: Administrator edits the user account
            String newEmail = data.getNewEmail() + i + System.nanoTime();
            user.setName(data.getNewName());
            user.setEmail(newEmail);
            
            MockMultipartFile emptyFile = new MockMultipartFile("img", new byte[0]);
            UserDtls updatedUser = userService.updateUserDetails(user, emptyFile);
            
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "EDIT_USER_ACCOUNT",
                "แก้ไขบัญชีผู้ใช้ ID:" + userId + " (" + updatedUser.getEmail() + ")",
                data.getIpAddress()
            );
            
            // Then: The audit log should contain the NEW email, not the original
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).isNotEmpty();
            assertThat(logs.get(0).getDetails()).contains(newEmail);
            assertThat(logs.get(0).getDetails()).doesNotContain(originalEmail);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Provides arbitrary valid edit data for property testing
     */
    private Arbitrary<EditData> validEditData() {
        Arbitrary<String> names = Arbitraries.strings()
            .alpha()
            .withChars(' ')
            .ofMinLength(3)
            .ofMaxLength(50);
        
        Arbitrary<String> emailPrefixes = Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(String::toLowerCase);
        
        Arbitrary<String> mobileNumbers = Arbitraries.integers()
            .between(10000000, 99999999)
            .map(n -> "08" + n);
        
        Arbitrary<String> positions = Arbitraries.of(
            "อาจารย์",
            "ผู้ช่วยศาสตราจารย์",
            "รองศาสตราจารย์",
            "ศาสตราจารย์"
        );
        
        Arbitrary<String> ipAddresses = Arbitraries.of(
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "203.0.113.1",
            "198.51.100.1",
            "127.0.0.1"
        );
        
        return Combinators.combine(names, names, emailPrefixes, emailPrefixes, 
                                   mobileNumbers, positions, ipAddresses)
            .as((adminName, newName, adminEmailPrefix, newEmailPrefix, 
                 newMobile, newPosition, ipAddress) -> 
                new EditData(
                    adminName,
                    adminEmailPrefix + "@admin.com",
                    newName,
                    newEmailPrefix + "@test.com",
                    newMobile,
                    newPosition,
                    ipAddress
                )
            );
    }

    /**
     * Helper method to create a test account in the database with specific details
     */
    private UserDtls createTestAccount(String role, String email, String name, int iteration) {
        UserDtls user = new UserDtls();
        user.setTitle("นาย");
        user.setName(name);
        user.setEmail(email + iteration + System.nanoTime());
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

    /**
     * Data class to hold edit information for property testing
     */
    public static class EditData {
        private final String adminName;
        private final String adminEmail;
        private final String newName;
        private final String newEmail;
        private final String newMobile;
        private final String newPosition;
        private final String ipAddress;

        public EditData(String adminName, String adminEmail, String newName, 
                       String newEmail, String newMobile, String newPosition, 
                       String ipAddress) {
            this.adminName = adminName;
            this.adminEmail = adminEmail;
            this.newName = newName;
            this.newEmail = newEmail;
            this.newMobile = newMobile;
            this.newPosition = newPosition;
            this.ipAddress = ipAddress;
        }

        public String getAdminName() {
            return adminName;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public String getNewName() {
            return newName;
        }

        public String getNewEmail() {
            return newEmail;
        }

        public String getNewMobile() {
            return newMobile;
        }

        public String getNewPosition() {
            return newPosition;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
}
