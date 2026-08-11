package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.ecom.model.AdminLog;
import com.ecom.model.UserDtls;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.UserRepository;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * Property-Based Test for Deletion Audit Logging
 * 
 * **Validates: Requirements 3.4, 4.4, 6.3, 6.4, 6.5**
 * 
 * Property 5: Deletion Audit Logging
 * For any successful account deletion operation, the system should create an audit 
 * log entry containing the administrator's email, name, timestamp, IP address, 
 * appropriate action type (DELETE_USER_ACCOUNT or DELETE_ADMIN_ACCOUNT), and the 
 * deleted account's details.
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
public class DeletionAuditLoggingPropertyTest {

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
     * Property Test: User Account Deletion Audit Logging
     * 
     * Tests that for any successful user account deletion, the system creates an 
     * audit log entry with all required information including administrator details,
     * timestamp, IP address, action type DELETE_USER_ACCOUNT, and deleted account details.
     * 
     * This test runs 100 iterations with randomly generated admin and user data.
     */
    @Test
    void userAccountDeletionAuditLogging() {
        // Create arbitrary generator for admin and user data
        Arbitrary<AdminUserData> adminUserDataArbitrary = validAdminUserData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            AdminUserData data = adminUserDataArbitrary.sample();
            
            // Given: An administrator account exists
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            
            // And: A user account exists in the database
            UserDtls user = createTestAccount("ROLE_USER", data.getUserEmail(), 
                data.getUserName(), i * 2 + 1);
            Integer userId = user.getId();
            String userEmail = user.getEmail();
            
            // Record the time before deletion for timestamp validation
            // widened by 1ms: the timestamp column rounds to microseconds
            LocalDateTime beforeDeletion = LocalDateTime.now().minus(1, java.time.temporal.ChronoUnit.MILLIS);
            
            // When: Administrator deletes the user account
            Boolean deleted = userService.deleteUserById(userId);
            assertThat(deleted).isTrue();
            
            // And: The deletion is logged
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "DELETE_USER_ACCOUNT",
                "ลบบัญชีผู้ใช้ ID:" + userId + " (" + userEmail + ")",
                data.getIpAddress()
            );
            
            LocalDateTime afterDeletion = LocalDateTime.now().plus(1, java.time.temporal.ChronoUnit.MILLIS);
            
            // Then: An audit log entry should be created
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).isNotEmpty();
            
            AdminLog log = logs.get(0);
            
            // Verify all required fields are present and correct
            assertThat(log.getAdminEmail()).isEqualTo(admin.getEmail());
            assertThat(log.getAdminName()).isEqualTo(admin.getName());
            assertThat(log.getAction()).isEqualTo("DELETE_USER_ACCOUNT");
            assertThat(log.getDetails()).contains("ลบบัญชีผู้ใช้");
            assertThat(log.getDetails()).contains("ID:" + userId);
            assertThat(log.getDetails()).contains(userEmail);
            assertThat(log.getIpAddress()).isEqualTo(data.getIpAddress());
            
            // Verify timestamp is within reasonable range
            assertThat(log.getTimestamp()).isNotNull();
            assertThat(log.getTimestamp()).isAfterOrEqualTo(beforeDeletion);
            assertThat(log.getTimestamp()).isBeforeOrEqualTo(afterDeletion);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Admin Account Deletion Audit Logging
     * 
     * Tests that for any successful admin account deletion, the system creates an 
     * audit log entry with all required information including administrator details,
     * timestamp, IP address, action type DELETE_ADMIN_ACCOUNT, and deleted account details.
     * 
     * This test runs 100 iterations with randomly generated admin data.
     */
    @Test
    void adminAccountDeletionAuditLogging() {
        // Create arbitrary generator for admin data
        Arbitrary<AdminUserData> adminUserDataArbitrary = validAdminUserData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            AdminUserData data = adminUserDataArbitrary.sample();
            
            // Given: An administrator account exists (the one performing the deletion)
            UserDtls performingAdmin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            
            // And: Another admin account exists in the database (to be deleted)
            UserDtls adminToDelete = createTestAccount("ROLE_ADMIN", data.getUserEmail(), 
                data.getUserName(), i * 2 + 1);
            Integer adminId = adminToDelete.getId();
            String adminEmail = adminToDelete.getEmail();
            
            // Record the time before deletion for timestamp validation
            // widened by 1ms: the timestamp column rounds to microseconds
            LocalDateTime beforeDeletion = LocalDateTime.now().minus(1, java.time.temporal.ChronoUnit.MILLIS);
            
            // When: Administrator deletes the admin account
            Boolean deleted = userService.deleteUserById(adminId);
            assertThat(deleted).isTrue();
            
            // And: The deletion is logged
            adminLogService.log(
                performingAdmin.getEmail(),
                performingAdmin.getName(),
                "DELETE_ADMIN_ACCOUNT",
                "ลบบัญชีแอดมิน ID:" + adminId + " (" + adminEmail + ")",
                data.getIpAddress()
            );
            
            LocalDateTime afterDeletion = LocalDateTime.now().plus(1, java.time.temporal.ChronoUnit.MILLIS);
            
            // Then: An audit log entry should be created
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(performingAdmin.getEmail());
            assertThat(logs).isNotEmpty();
            
            AdminLog log = logs.get(0);
            
            // Verify all required fields are present and correct
            assertThat(log.getAdminEmail()).isEqualTo(performingAdmin.getEmail());
            assertThat(log.getAdminName()).isEqualTo(performingAdmin.getName());
            assertThat(log.getAction()).isEqualTo("DELETE_ADMIN_ACCOUNT");
            assertThat(log.getDetails()).contains("ลบบัญชีแอดมิน");
            assertThat(log.getDetails()).contains("ID:" + adminId);
            assertThat(log.getDetails()).contains(adminEmail);
            assertThat(log.getIpAddress()).isEqualTo(data.getIpAddress());
            
            // Verify timestamp is within reasonable range
            assertThat(log.getTimestamp()).isNotNull();
            assertThat(log.getTimestamp()).isAfterOrEqualTo(beforeDeletion);
            assertThat(log.getTimestamp()).isBeforeOrEqualTo(afterDeletion);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Property Test: Multiple Deletions Create Multiple Audit Logs
     * 
     * Tests that multiple deletion operations create separate audit log entries,
     * ensuring each deletion is properly tracked.
     * 
     * This test runs 50 iterations with randomly generated data.
     */
    @Test
    void multipleDeletionsCreateMultipleAuditLogs() {
        // Create arbitrary generator for admin data
        Arbitrary<AdminUserData> adminUserDataArbitrary = validAdminUserData();
        
        // Run property test for 50 iterations (fewer because we create multiple accounts per iteration)
        for (int i = 0; i < 50; i++) {
            AdminUserData data = adminUserDataArbitrary.sample();
            
            // Given: An administrator account exists
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 10);
            
            // And: Multiple user accounts exist
            UserDtls user1 = createTestAccount("ROLE_USER", "user1" + i + "@test.com", 
                "User One " + i, i * 10 + 1);
            UserDtls user2 = createTestAccount("ROLE_USER", "user2" + i + "@test.com", 
                "User Two " + i, i * 10 + 2);
            
            // When: Administrator deletes both user accounts
            userService.deleteUserById(user1.getId());
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "DELETE_USER_ACCOUNT",
                "ลบบัญชีผู้ใช้ ID:" + user1.getId() + " (" + user1.getEmail() + ")",
                data.getIpAddress()
            );
            
            userService.deleteUserById(user2.getId());
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "DELETE_USER_ACCOUNT",
                "ลบบัญชีผู้ใช้ ID:" + user2.getId() + " (" + user2.getEmail() + ")",
                data.getIpAddress()
            );
            
            // Then: Two separate audit log entries should exist
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).hasSize(2);
            
            // Verify both logs have the correct action type
            assertThat(logs).allMatch(log -> log.getAction().equals("DELETE_USER_ACCOUNT"));
            
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
     * Property Test: Audit Log Persists After Account Deletion
     * 
     * Tests that audit logs remain in the database even after the associated
     * account has been deleted, ensuring audit trail integrity.
     * 
     * This test runs 100 iterations with randomly generated data.
     */
    @Test
    void auditLogPersistsAfterAccountDeletion() {
        // Create arbitrary generator for admin and user data
        Arbitrary<AdminUserData> adminUserDataArbitrary = validAdminUserData();
        
        // Run property test for 100 iterations
        for (int i = 0; i < 100; i++) {
            AdminUserData data = adminUserDataArbitrary.sample();
            
            // Given: An administrator and user account exist
            UserDtls admin = createTestAccount("ROLE_ADMIN", data.getAdminEmail(), 
                data.getAdminName(), i * 2);
            UserDtls user = createTestAccount("ROLE_USER", data.getUserEmail(), 
                data.getUserName(), i * 2 + 1);
            Integer userId = user.getId();
            
            // When: Administrator deletes the user account and logs it
            userService.deleteUserById(userId);
            adminLogService.log(
                admin.getEmail(),
                admin.getName(),
                "DELETE_USER_ACCOUNT",
                "ลบบัญชีผู้ใช้ ID:" + userId + " (" + user.getEmail() + ")",
                data.getIpAddress()
            );
            
            // Then: The user account should be deleted
            UserDtls deletedUser = userService.getUserById(userId);
            assertThat(deletedUser).isNull();
            
            // But: The audit log should still exist
            List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(admin.getEmail());
            assertThat(logs).isNotEmpty();
            assertThat(logs.get(0).getAction()).isEqualTo("DELETE_USER_ACCOUNT");
            assertThat(logs.get(0).getDetails()).contains("ID:" + userId);
            
            // Clean up
            adminLogRepository.deleteAll();
            userRepository.deleteAll();
        }
    }

    /**
     * Provides arbitrary valid admin and user data for property testing
     */
    private Arbitrary<AdminUserData> validAdminUserData() {
        // A valid name has a non-blank first and last part, which is what the
        // application validates. Generating bare strings (" ", "abc") produced
        // data the app is supposed to reject.
        Arbitrary<String> nameParts = Arbitraries.strings()
            .alpha()
            .ofMinLength(2)
            .ofMaxLength(20);
        Arbitrary<String> names = Combinators.combine(nameParts, nameParts)
            .as((first, last) -> first + " " + last);
        
        Arbitrary<String> emailPrefixes = Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(3)
            .ofMaxLength(20)
            .map(String::toLowerCase);
        
        Arbitrary<String> ipAddresses = Arbitraries.of(
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "203.0.113.1",
            "198.51.100.1",
            "127.0.0.1"
        );
        
        return Combinators.combine(names, names, emailPrefixes, emailPrefixes, ipAddresses)
            .as((adminName, userName, adminEmailPrefix, userEmailPrefix, ipAddress) -> 
                new AdminUserData(
                    adminName,
                    adminEmailPrefix + "@admin.com",
                    userName,
                    userEmailPrefix + "@user.com",
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
     * Data class to hold admin and user information for property testing
     */
    public static class AdminUserData {
        private final String adminName;
        private final String adminEmail;
        private final String userName;
        private final String userEmail;
        private final String ipAddress;

        public AdminUserData(String adminName, String adminEmail, String userName, 
                           String userEmail, String ipAddress) {
            this.adminName = adminName;
            this.adminEmail = adminEmail;
            this.userName = userName;
            this.userEmail = userEmail;
            this.ipAddress = ipAddress;
        }

        public String getAdminName() {
            return adminName;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public String getUserName() {
            return userName;
        }

        public String getUserEmail() {
            return userEmail;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }
}
