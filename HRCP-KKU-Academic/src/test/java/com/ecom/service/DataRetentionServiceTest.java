package com.ecom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ecom.academic.service.AdminStorageService;
import com.ecom.academic.service.UserStorageService;
import com.ecom.external.repository.FsFacultyChangeRepository;
import com.ecom.repository.AdminLogRepository;
import com.ecom.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class DataRetentionServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AdminLogRepository adminLogRepository;

    @Mock
    private FsFacultyChangeRepository fsFacultyChangeRepository;

    @Mock
    private AdminStorageService adminStorageService;

    @Mock
    private UserStorageService userStorageService;

    private DataRetentionService retentionService;

    @BeforeEach
    void setUp() {
        retentionService = new DataRetentionService(
                notificationRepository,
                adminLogRepository,
                fsFacultyChangeRepository,
                adminStorageService,
                userStorageService);

        // Inject default test properties
        ReflectionTestUtils.setField(retentionService, "notificationDeletedDays", 60);
        ReflectionTestUtils.setField(retentionService, "notificationAncientDays", 180);
        ReflectionTestUtils.setField(retentionService, "adminLogDays", 365);
        ReflectionTestUtils.setField(retentionService, "facultySyncDays", 180);
        ReflectionTestUtils.setField(retentionService, "storageTrashDays", 30);
    }

    @Test
    @DisplayName("ตัดรอบการแจ้งเตือนที่ถูกกดลบ (isDeleted=true) เกิน 60 วัน")
    void purgesSoftDeletedNotificationsOlderThanCutoff() {
        when(notificationRepository.deleteDeletedNotificationsBefore(any(LocalDateTime.class))).thenReturn(42);

        int purged = retentionService.purgeSoftDeletedNotifications(60);

        assertEquals(42, purged);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).deleteDeletedNotificationsBefore(captor.capture());
        assertTrue(captor.getValue().isBefore(LocalDateTime.now().minusDays(59)));
    }

    @Test
    @DisplayName("ตัดรอบ AdminLog โดยรักษากฎหมาย พ.ร.บ.คอมพิวเตอร์ ขั้นต่ำ 90 วัน เสมอ")
    void enforcesComputerCrimeActMinimumSafeguardForAdminLogs() {
        when(adminLogRepository.deleteLogsBefore(any(LocalDateTime.class))).thenReturn(15);

        // Even if requested 30 days, the service must enforce Math.max(90, days)
        int purged = retentionService.purgeOldAdminLogs(30);

        assertEquals(15, purged);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(adminLogRepository).deleteLogsBefore(captor.capture());
        // Cutoff should be approximately 90 days ago, not 30 days
        assertTrue(captor.getValue().isBefore(LocalDateTime.now().minusDays(89)));
    }

    @Test
    @DisplayName("ตัดรอบประวัติการซิงค์ข้อมูลคณะที่เสร็จสิ้นแล้ว (ไม่ใช่ PENDING) เกิน 180 วัน")
    void purgesProcessedFacultySyncLogsOlderThanCutoff() {
        when(fsFacultyChangeRepository.deleteProcessedChangesBefore(any(LocalDateTime.class))).thenReturn(8);

        int purged = retentionService.purgeOldFacultySyncLogs(180);

        assertEquals(8, purged);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fsFacultyChangeRepository).deleteProcessedChangesBefore(captor.capture());
        assertTrue(captor.getValue().isBefore(LocalDateTime.now().minusDays(179)));
    }

    @Test
    @DisplayName("รัน Full Retention Cycle และสรุปผลรวมถูกต้องครบถ้วน")
    void runsFullRetentionCycleAndAggregatesReport() {
        when(notificationRepository.deleteDeletedNotificationsBefore(any(LocalDateTime.class))).thenReturn(10);
        when(notificationRepository.deleteAncientNotificationsBefore(any(LocalDateTime.class))).thenReturn(5);
        when(adminLogRepository.deleteLogsBefore(any(LocalDateTime.class))).thenReturn(20);
        when(fsFacultyChangeRepository.deleteProcessedChangesBefore(any(LocalDateTime.class))).thenReturn(3);
        when(adminStorageService.purgeOldTrash(30)).thenReturn(4);
        when(userStorageService.purgeOldTrash(30)).thenReturn(2);

        var report = retentionService.runFullRetentionCycle();

        assertNotNull(report);
        assertEquals(10, report.deletedNotificationsPurged());
        assertEquals(5, report.ancientNotificationsPurged());
        assertEquals(20, report.adminLogsPurged());
        assertEquals(3, report.facultySyncLogsPurged());
        assertEquals(4, report.adminStorageTrashPurged());
        assertEquals(2, report.userStorageTrashPurged());
        assertEquals(38, report.totalDatabaseRecordsPurged());
        assertEquals(6, report.totalStorageFilesPurged());
        assertTrue(report.durationMs() >= 0);
        assertNotNull(report.executionTimestamp());
    }
}
