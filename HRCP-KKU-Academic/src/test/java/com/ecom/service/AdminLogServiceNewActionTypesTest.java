package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.model.AdminLog;
import com.ecom.repository.AdminLogRepository;

/**
 * Test class to verify AdminLogService correctly handles new action types
 * for the admin-user-management feature.
 * 
 * Tests that the service accepts and persists the following action types:
 * - EDIT_USER_ACCOUNT
 * - EDIT_ADMIN_ACCOUNT
 * - DELETE_USER_ACCOUNT
 * - DELETE_ADMIN_ACCOUNT
 */
@SpringBootTest
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:adminlogdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "app.audit-log.async=false"
})
@Transactional
public class AdminLogServiceNewActionTypesTest {

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private AdminLogRepository adminLogRepository;

    @Test
    public void testEditUserAccountActionType() {
        // Given
        String adminEmail = "admin@test.com";
        String adminName = "Test Admin";
        String action = "EDIT_USER_ACCOUNT";
        String details = "แก้ไขบัญชีผู้ใช้ ID:123 (user@test.com)";
        String ipAddress = "192.168.1.1";

        // When
        adminLogService.log(adminEmail, adminName, action, details, ipAddress);

        // Then
        List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
        assertThat(logs).isNotEmpty();
        AdminLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("EDIT_USER_ACCOUNT");
        assertThat(log.getAdminEmail()).isEqualTo(adminEmail);
        assertThat(log.getAdminName()).isEqualTo(adminName);
        assertThat(log.getDetails()).isEqualTo(details);
        assertThat(log.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    public void testEditAdminAccountActionType() {
        // Given
        String adminEmail = "admin@test.com";
        String adminName = "Test Admin";
        String action = "EDIT_ADMIN_ACCOUNT";
        String details = "แก้ไขบัญชีแอดมิน ID:456 (admin2@test.com)";
        String ipAddress = "192.168.1.2";

        // When
        adminLogService.log(adminEmail, adminName, action, details, ipAddress);

        // Then
        List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
        assertThat(logs).isNotEmpty();
        AdminLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("EDIT_ADMIN_ACCOUNT");
        assertThat(log.getAdminEmail()).isEqualTo(adminEmail);
        assertThat(log.getAdminName()).isEqualTo(adminName);
        assertThat(log.getDetails()).isEqualTo(details);
        assertThat(log.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    public void testDeleteUserAccountActionType() {
        // Given
        String adminEmail = "admin@test.com";
        String adminName = "Test Admin";
        String action = "DELETE_USER_ACCOUNT";
        String details = "ลบบัญชีผู้ใช้ ID:789 (deleted@test.com)";
        String ipAddress = "192.168.1.3";

        // When
        adminLogService.log(adminEmail, adminName, action, details, ipAddress);

        // Then
        List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
        assertThat(logs).isNotEmpty();
        AdminLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("DELETE_USER_ACCOUNT");
        assertThat(log.getAdminEmail()).isEqualTo(adminEmail);
        assertThat(log.getAdminName()).isEqualTo(adminName);
        assertThat(log.getDetails()).isEqualTo(details);
        assertThat(log.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    public void testDeleteAdminAccountActionType() {
        // Given
        String adminEmail = "admin@test.com";
        String adminName = "Test Admin";
        String action = "DELETE_ADMIN_ACCOUNT";
        String details = "ลบบัญชีแอดมิน ID:101 (oldadmin@test.com)";
        String ipAddress = "192.168.1.4";

        // When
        adminLogService.log(adminEmail, adminName, action, details, ipAddress);

        // Then
        List<AdminLog> logs = adminLogRepository.findByAdminEmailOrderByTimestampDesc(adminEmail);
        assertThat(logs).isNotEmpty();
        AdminLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("DELETE_ADMIN_ACCOUNT");
        assertThat(log.getAdminEmail()).isEqualTo(adminEmail);
        assertThat(log.getAdminName()).isEqualTo(adminName);
        assertThat(log.getDetails()).isEqualTo(details);
        assertThat(log.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    public void testAllNewActionTypesCanBeFiltered() {
        // Given - Create logs with all new action types
        String adminEmail = "admin@test.com";
        String adminName = "Test Admin";
        
        adminLogService.log(adminEmail, adminName, "EDIT_USER_ACCOUNT", "Edit user", "192.168.1.1");
        adminLogService.log(adminEmail, adminName, "EDIT_ADMIN_ACCOUNT", "Edit admin", "192.168.1.1");
        adminLogService.log(adminEmail, adminName, "DELETE_USER_ACCOUNT", "Delete user", "192.168.1.1");
        adminLogService.log(adminEmail, adminName, "DELETE_ADMIN_ACCOUNT", "Delete admin", "192.168.1.1");

        // When - Search by each action type
        List<AdminLog> editUserLogs = adminLogService.searchByAction("EDIT_USER_ACCOUNT");
        List<AdminLog> editAdminLogs = adminLogService.searchByAction("EDIT_ADMIN_ACCOUNT");
        List<AdminLog> deleteUserLogs = adminLogService.searchByAction("DELETE_USER_ACCOUNT");
        List<AdminLog> deleteAdminLogs = adminLogService.searchByAction("DELETE_ADMIN_ACCOUNT");

        // Then - Each action type should be found
        assertThat(editUserLogs).isNotEmpty();
        assertThat(editAdminLogs).isNotEmpty();
        assertThat(deleteUserLogs).isNotEmpty();
        assertThat(deleteAdminLogs).isNotEmpty();
        
        // Verify counts
        assertThat(adminLogService.countByAction("EDIT_USER_ACCOUNT")).isGreaterThan(0);
        assertThat(adminLogService.countByAction("EDIT_ADMIN_ACCOUNT")).isGreaterThan(0);
        assertThat(adminLogService.countByAction("DELETE_USER_ACCOUNT")).isGreaterThan(0);
        assertThat(adminLogService.countByAction("DELETE_ADMIN_ACCOUNT")).isGreaterThan(0);
    }
}
