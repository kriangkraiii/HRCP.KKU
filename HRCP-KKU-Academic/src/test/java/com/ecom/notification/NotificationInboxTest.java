package com.ecom.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.ecom.model.Notification;
import com.ecom.model.NotificationType;
import com.ecom.model.UserDtls;
import com.ecom.service.NotificationService;
import com.ecom.support.AbstractFlowTest;

/**
 * The notification inbox: counts, tabs, and — above all — whose is whose.
 *
 * <p>Every mutating method takes the acting user and resolves the notification
 * through {@code findByIdAndRecipient}. That is the only thing standing between
 * one professor and another's inbox, and an id is trivially guessable, so each
 * operation is checked from the wrong account as well as the right one. A method
 * added later that resolves by id alone would pass its own happy-path test and
 * fail here, which is the point.
 */
@DisplayName("การแจ้งเตือน: กล่องข้อความและการแยกของแต่ละคน")
class NotificationInboxTest extends AbstractFlowTest {

    @Autowired
    private NotificationService notifications;

    private Notification notifyOf(UserDtls recipient, String title) {
        return notifications.sendNotification(recipient, null, title, "รายละเอียด",
                "/user/academic/dashboard", NotificationType.ACADEMIC_STATUS_UPDATE, false);
    }

    @Nested
    @DisplayName("การนับและแท็บ")
    class CountsAndTabs {

        @Test
        @DisplayName("แจ้งเตือนใหม่นับเป็นยังไม่อ่าน อ่านแล้วนับลด")
        void unreadCountTracksReading() {
            UserDtls user = data.applicant();
            Notification first = notifyOf(user, "แจ้งเตือนที่หนึ่ง");
            notifyOf(user, "แจ้งเตือนที่สอง");

            assertThat(notifications.getUnreadCount(user)).isEqualTo(2);

            assertThat(notifications.markAsRead(first.getId(), user)).isTrue();
            assertThat(notifications.getUnreadCount(user)).isEqualTo(1);

            assertThat(notifications.markAsUnread(first.getId(), user)).isTrue();
            assertThat(notifications.getUnreadCount(user)).isEqualTo(2);
        }

        @Test
        @DisplayName("อ่านทั้งหมด — เหลือยังไม่อ่านศูนย์")
        void markAllAsReadClearsTheCount() {
            UserDtls user = data.applicant();
            notifyOf(user, "หนึ่ง");
            notifyOf(user, "สอง");
            notifyOf(user, "สาม");

            notifications.markAllAsRead(user);

            assertThat(notifications.getUnreadCount(user)).isZero();
        }

        @Test
        @DisplayName("ติดดาว / ทำเครื่องหมายสำคัญ / เลื่อนแจ้งเตือน — ตัวเลขแต่ละแท็บถูกต้อง")
        void tabCountsReflectEachFlag() {
            UserDtls user = data.applicant();
            Notification starred = notifyOf(user, "ติดดาว");
            Notification important = notifyOf(user, "สำคัญ");
            Notification snoozed = notifyOf(user, "เลื่อนไว้");

            notifications.toggleStar(starred.getId(), user);
            notifications.toggleImportant(important.getId(), user);
            notifications.snooze(snoozed.getId(), "1w", user);

            var counts = notifications.getTabCounts(user);
            assertThat(counts.get("starred")).isEqualTo(1);
            assertThat(counts.get("important")).isEqualTo(1);
            assertThat(counts.get("snoozed")).isEqualTo(1);
        }

        @Test
        @DisplayName("ที่เลื่อนไว้ไม่โผล่ในรายการหลัก และไม่นับเป็นยังไม่อ่าน")
        void snoozedIsHiddenUntilItsTime() {
            UserDtls user = data.applicant();
            Notification snoozed = notifyOf(user, "ไว้ค่อยดู");
            notifyOf(user, "ดูตอนนี้");

            notifications.snooze(snoozed.getId(), "1w", user);

            assertThat(notifications.getUnreadCount(user))
                    .as("ที่เลื่อนไว้ต้องไม่มาเร่งให้อ่าน")
                    .isEqualTo(1);
            assertThat(notifications.getRecentNotifications(user, 10))
                    .extracting(Notification::getTitle)
                    .containsExactly("ดูตอนนี้");

            notifications.unsnooze(snoozed.getId(), user);
            assertThat(notifications.getUnreadCount(user)).isEqualTo(2);
        }

        @Test
        @DisplayName("ลบแล้วไปอยู่ถังขยะ กู้คืนกลับมาได้ ลบถาวรแล้วหายจริง")
        void deleteRestoreAndPurge() {
            UserDtls user = data.applicant();
            Notification n = notifyOf(user, "จะถูกลบ");

            assertThat(notifications.softDelete(n.getId(), user)).isTrue();
            assertThat(notifications.getUnreadCount(user)).isZero();
            assertThat(notifications.getTabCounts(user).get("trash")).isEqualTo(1);

            assertThat(notifications.restore(n.getId(), user)).isTrue();
            assertThat(notifications.getUnreadCount(user)).isEqualTo(1);

            notifications.softDelete(n.getId(), user);
            assertThat(notifications.permanentDelete(n.getId(), user)).isTrue();
            assertThat(notifications.findByIdAndRecipient(n.getId(), user)).isEmpty();
        }

        @Test
        @DisplayName("ค้นหาในกล่องข้อความ เจอเฉพาะที่ตรงคำค้น")
        void searchFiltersTheInbox() {
            UserDtls user = data.applicant();
            notifyOf(user, "อัปเดตสถานะการประเมิน");
            notifyOf(user, "คำร้องขอตำแหน่งใหม่");

            assertThat(notifications.getNotifications(user, "all", "ตำแหน่ง", PageRequest.of(0, 10)))
                    .extracting(Notification::getTitle)
                    .containsExactly("คำร้องขอตำแหน่งใหม่");
        }
    }

    @Nested
    @DisplayName("การแยกกล่องข้อความของแต่ละคน")
    class Isolation {

        @Test
        @DisplayName("เห็นเฉพาะการแจ้งเตือนของตัวเอง")
        void eachPersonSeesOnlyTheirOwn() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            notifyOf(somchai, "ของสมชาย");
            notifyOf(malee, "ของมาลี");

            assertThat(notifications.getRecentNotifications(somchai, 10))
                    .extracting(Notification::getTitle)
                    .containsExactly("ของสมชาย");
            assertThat(notifications.getUnreadCount(malee)).isEqualTo(1);
        }

        /**
         * The ids are sequential and visible in URLs. If any of these resolved by
         * id alone, one professor could read, star, snooze or delete another's
         * notifications by changing a number.
         */
        @Test
        @DisplayName("แตะการแจ้งเตือนของคนอื่นไม่ได้เลย ไม่ว่าจะเป็นการกระทำใด")
        void noOperationReachesSomebodyElsesNotification() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            Notification maleesNotification = notifyOf(malee, "เรื่องส่วนตัวของมาลี");
            Long id = maleesNotification.getId();

            assertThat(notifications.findByIdAndRecipient(id, somchai)).isEmpty();
            assertThat(notifications.markAsRead(id, somchai)).isFalse();
            assertThat(notifications.markAsUnread(id, somchai)).isFalse();
            assertThat(notifications.toggleStar(id, somchai)).isNull();
            assertThat(notifications.toggleImportant(id, somchai)).isNull();
            assertThat(notifications.snooze(id, "1h", somchai)).isFalse();
            assertThat(notifications.unsnooze(id, somchai)).isFalse();
            assertThat(notifications.softDelete(id, somchai)).isFalse();
            assertThat(notifications.restore(id, somchai)).isFalse();
            assertThat(notifications.permanentDelete(id, somchai)).isFalse();

            assertThat(notifications.getUnreadCount(malee))
                    .as("ของมาลีต้องอยู่ครบ ไม่ถูกแตะต้องเลย")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("ล้างถังขยะของตัวเอง ไม่กระทบของคนอื่น")
        void emptyingTrashIsScopedToTheOwner() {
            UserDtls somchai = data.applicant();
            UserDtls malee = data.otherApplicant();
            Notification mine = notifyOf(somchai, "ของฉัน");
            Notification theirs = notifyOf(malee, "ของเขา");
            notifications.softDelete(mine.getId(), somchai);
            notifications.softDelete(theirs.getId(), malee);

            notifications.emptyTrash(somchai);

            assertThat(notifications.findByIdAndRecipient(mine.getId(), somchai)).isEmpty();
            assertThat(notifications.findByIdAndRecipient(theirs.getId(), malee))
                    .as("ถังขยะของมาลีต้องไม่ถูกล้างไปด้วย")
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("กรณีขอบ")
    class EdgeCases {

        @Test
        @DisplayName("ผู้รับที่ถูกปิดบัญชี — ไม่สร้างการแจ้งเตือนให้")
        void disabledRecipientGetsNothing() {
            UserDtls disabled = data.user("disabled@example.invalid", "ปิด", "บัญชี", "ROLE_USER");
            disabled.setIsEnable(false);
            data.saveUser(disabled);

            assertThat(notifyOf(disabled, "ไม่ควรถูกสร้าง")).isNull();
            assertThat(notifications.getUnreadCount(disabled)).isZero();
        }

        @Test
        @DisplayName("ผู้รับเป็น null — ไม่ล้ม แค่ไม่ทำอะไร")
        void nullRecipientIsHandled() {
            assertThat(notifyOf(null, "ไม่มีผู้รับ")).isNull();
            assertThat(notifications.getUnreadCount(null)).isZero();
            assertThat(notifications.getRecentNotifications(null, 5)).isEmpty();
        }

        @Test
        @DisplayName("ระยะเวลาเลื่อนที่ไม่รู้จัก — ใช้ค่าเริ่มต้น 1 ชั่วโมง ไม่ใช่พัง")
        void unknownSnoozeDurationFallsBackToAnHour() {
            UserDtls user = data.applicant();
            Notification n = notifyOf(user, "เลื่อน");

            assertThat(notifications.snooze(n.getId(), "ไม่รู้จักหน่วยนี้", user)).isTrue();

            Notification reloaded = notifications.findByIdAndRecipient(n.getId(), user).orElseThrow();
            assertThat(reloaded.getSnoozedUntil())
                    .isBetween(LocalDateTime.now().plusMinutes(55),
                            LocalDateTime.now().plusMinutes(65));
        }

        @Test
        @DisplayName("แจ้งแอดมินทุกคน — ข้ามคนที่ปิดบัญชี")
        void notifyAdminsSkipsDisabledOnes() {
            UserDtls activeAdmin = data.admin();
            UserDtls retiredAdmin = data.user("retired-admin@example.invalid",
                    "อดีต", "ผู้ดูแล", "ROLE_ADMIN");
            retiredAdmin.setIsEnable(false);
            data.saveUser(retiredAdmin);

            notifications.notifyAdmins(data.applicant(), "คำร้องใหม่", "มีคำร้องเข้ามา",
                    "/admin/academic/requests", NotificationType.ACADEMIC_NEW_REQUEST, false);

            assertThat(notifications.getUnreadCount(activeAdmin)).isEqualTo(1);
            assertThat(notifications.getUnreadCount(retiredAdmin)).isZero();
        }

        @Test
        @DisplayName("ทุกประเภทการแจ้งเตือนมีชื่อไทยและไอคอนกำกับ")
        void everyTypeIsPresentable() {
            assertThat(List.of(NotificationType.values()))
                    .allSatisfy(type -> {
                        assertThat(type.getThaiLabel()).isNotBlank();
                        assertThat(type.getIconClass()).isNotBlank();
                    });
        }
    }
}
