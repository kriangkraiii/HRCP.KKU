package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.AcademicRequest;
import com.ecom.academic.model.RequestStatus;
import com.ecom.academic.service.AcademicEmailService;
import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.repository.UserRepository;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:inactivenotiftestdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false"
})
@Transactional
class InactiveUserSecurityNotificationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("เมื่อบัญชีผู้ใช้เป็น Inactive (isEnable=false) ระบบต้องไม่บันทึกการแจ้งเตือน (In-App Notification) ใดๆ")
    void inactiveUserDoesNotReceiveNotification() {
        UserDtls inactiveUser = new UserDtls();
        inactiveUser.setEmail("inactive_user_test@kku.ac.th");
        inactiveUser.setFirstName("ทดสอบ");
        inactiveUser.setLastName("ปิดใช้งาน");
        inactiveUser.setIsEnable(false); // INACTIVE
        inactiveUser.setRole("ROLE_USER");
        inactiveUser = userRepository.save(inactiveUser);

        Notification notif = notificationService.sendNotification(
                inactiveUser, null, "หัวข้อทดสอบ", "ข้อความ", "/link", NotificationType.SYSTEM, false);

        assertThat(notif).isNull();
        assertThat(notificationRepository.findAllActive(inactiveUser, java.time.LocalDateTime.now(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("เมื่อแอดมินเป็น Inactive (isEnable=false) ระบบต้องข้ามไม่ส่งแจ้งเตือน (notifyAdmins)")
    void inactiveAdminSkippedInNotifyAdmins() {
        UserDtls inactiveAdmin = new UserDtls();
        inactiveAdmin.setEmail("inactive_admin_test@kku.ac.th");
        inactiveAdmin.setFirstName("แอดมิน");
        inactiveAdmin.setLastName("ปิดใช้งาน");
        inactiveAdmin.setIsEnable(false); // INACTIVE
        inactiveAdmin.setRole("ROLE_ADMIN");
        inactiveAdmin = userRepository.save(inactiveAdmin);

        notificationService.notifyAdmins(null, "แจ้งเตือนระบบ", "ข้อความ", "/link", NotificationType.SYSTEM, false);

        assertThat(notificationRepository.findAllActive(inactiveAdmin, java.time.LocalDateTime.now(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isEqualTo(0);
    }
}
