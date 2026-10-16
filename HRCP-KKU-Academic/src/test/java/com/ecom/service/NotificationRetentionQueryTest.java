package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.repository.NotificationRepository;
import com.ecom.support.AbstractFlowTest;

/**
 * การล้างแจ้งเตือนตามอายุ ใช้ query จริง (docs/PLAN-bughunt-decisions.md ข้อ 6)
 *
 * <p>เดิมแจ้งเตือนอายุเกิน 180 วันถูกลบทั้งหมดแม้ผู้ใช้ติดดาวไว้ และถังขยะนับอายุจากวันที่สร้าง
 * แจ้งเตือนเก่าที่เพิ่งลบจึงหายจากถังในคืนเดียวกัน แทนที่จะอยู่ในถังตามจำนวนวันที่ตั้งไว้
 */
@DisplayName("ล้างแจ้งเตือนตามอายุ: เก็บที่ติดดาว และถังขยะนับจากวันที่ลบ")
class NotificationRetentionQueryTest extends AbstractFlowTest {

    @Autowired
    private NotificationRepository notifications;

    private Notification saved(UserDtls to, int daysOld, boolean starred) {
        Notification n = new Notification(to, null, NotificationType.ACADEMIC_STATUS_UPDATE, "ข้อความ");
        n.setCreatedAt(LocalDateTime.now().minusDays(daysOld));
        n.setIsStarred(starred);
        return notifications.saveAndFlush(n);
    }

    @Test
    @DisplayName("อายุเกิน 180 วัน: ที่ติดดาวยังอยู่ ที่ไม่ติดดาวถูกลบ")
    void starredNotificationsOutliveTheAncientPurge() {
        UserDtls user = data.applicant();
        Notification starred = saved(user, 200, true);
        Notification plain = saved(user, 200, false);

        notifications.deleteAncientNotificationsBefore(LocalDateTime.now().minusDays(180));

        assertThat(notifications.findById(starred.getId())).isPresent();
        assertThat(notifications.findById(plain.getId())).isEmpty();
    }

    @Test
    @DisplayName("ถังขยะนับจากวันที่ลบ: อายุ 100 วันแต่เพิ่งลบวันนี้ ยังอยู่ในถัง")
    void theTrashCountsFromTheDayItWasDeleted() {
        UserDtls user = data.applicant();
        Notification justTrashed = saved(user, 100, false);
        justTrashed.setIsDeleted(true);
        notifications.saveAndFlush(justTrashed);

        Notification longTrashed = saved(user, 100, false);
        longTrashed.setIsDeleted(true);
        longTrashed.setDeletedAt(LocalDateTime.now().minusDays(70));
        notifications.saveAndFlush(longTrashed);

        notifications.deleteDeletedNotificationsBefore(LocalDateTime.now().minusDays(60));

        assertThat(notifications.findById(justTrashed.getId())).as("เพิ่งลบวันนี้").isPresent();
        assertThat(notifications.findById(longTrashed.getId())).as("อยู่ในถังมา 70 วัน").isEmpty();
    }

    @Test
    @DisplayName("กู้คืนจากถังแล้ว วันที่ลบถูกล้าง")
    void restoringClearsTheDeletionDate() {
        Notification n = saved(data.applicant(), 1, false);
        n.setIsDeleted(true);
        assertThat(n.getDeletedAt()).isNotNull();

        n.setIsDeleted(false);

        assertThat(n.getDeletedAt()).isNull();
    }
}
