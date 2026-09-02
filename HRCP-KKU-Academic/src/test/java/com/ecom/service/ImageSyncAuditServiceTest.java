package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ImageSyncAuditServiceTest {

    @TempDir
    Path tempUploadDir;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.ecom.academic.service.AdminStorageService adminStorageService;

    private ProfileImageStorage storage;
    private ImageSyncAuditService auditService;

    @BeforeEach
    void setUp() {
        storage = new ProfileImageStorage(tempUploadDir.toString());
        auditService = new ImageSyncAuditService(storage, userRepository, adminStorageService);
    }

    @Test
    @DisplayName("ตรวจสอบและลบไฟล์ Orphan (ไฟล์บนดิสก์ที่ไม่มีใน DB) ออกได้สำเร็จ")
    void detectsAndPurgesOrphanFiles() throws Exception {
        // Files on disk:
        // 1. default.png (protected)
        // 2. active_user.jpg (in DB)
        // 3. orphan_old.png (NOT in DB)
        Files.writeString(tempUploadDir.resolve("default.png"), "default");
        Files.writeString(tempUploadDir.resolve("active_user.jpg"), "active");
        Files.writeString(tempUploadDir.resolve("orphan_old.png"), "orphan");

        UserDtls user = new UserDtls();
        user.setId(1);
        user.setEmail("test@kku.ac.th");
        user.setProfileImage("active_user.jpg");

        when(userRepository.findAll()).thenReturn(List.of(user));

        ImageSyncAuditService.ImageAuditReport report = auditService.auditAndSync(true, true);

        assertThat(report.orphanFilesFound()).contains("orphan_old.png");
        assertThat(report.orphanFilesPurged()).contains("orphan_old.png");
        assertThat(Files.exists(tempUploadDir.resolve("orphan_old.png"))).isFalse();
        assertThat(Files.exists(tempUploadDir.resolve("active_user.jpg"))).isTrue();
        assertThat(Files.exists(tempUploadDir.resolve("default.png"))).isTrue();
    }

    @Test
    @DisplayName("ตรวจพบและซ่อมแซม Missing Image ใน DB ให้กลับไปเป็น default.png")
    void autoHealsMissingImageReferencesInDb() throws Exception {
        UserDtls brokenUser = new UserDtls();
        brokenUser.setId(2);
        brokenUser.setEmail("broken@kku.ac.th");
        brokenUser.setProfileImage("missing_file_on_disk.jpg");

        when(userRepository.findAll()).thenReturn(List.of(brokenUser));

        ImageSyncAuditService.ImageAuditReport report = auditService.auditAndSync(true, true);

        assertThat(report.missingDbFilesFound()).hasSize(1);
        assertThat(report.missingDbFilesHealed()).contains("missing_file_on_disk.jpg");
        assertThat(brokenUser.getProfileImage()).isEqualTo("default.png");
        verify(userRepository).save(any(UserDtls.class));
    }

    @Test
    @DisplayName("รันอัตโนมัติตอน Application Startup (onApplicationStartup) เพื่อล้างไฟล์ตกค้างทั้งหมด")
    void runsOnStartupAndCleansLegacyOrphans() throws Exception {
        Files.writeString(tempUploadDir.resolve("legacy_leftover.png"), "leftover");
        when(userRepository.findAll()).thenReturn(List.of());

        auditService.onApplicationStartup();

        assertThat(Files.exists(tempUploadDir.resolve("legacy_leftover.png"))).isFalse();
    }

    @Test
    @DisplayName("รันอัตโนมัติตามรอบ Scheduled Daily Cron เพื่อทำความสะอาดรายวัน")
    void runsOnScheduledDailyCron() throws Exception {
        Files.writeString(tempUploadDir.resolve("daily_junk.png"), "junk");
        when(userRepository.findAll()).thenReturn(List.of());

        auditService.scheduledDailyCleanup();

        assertThat(Files.exists(tempUploadDir.resolve("daily_junk.png"))).isFalse();
    }
}
