package com.ecom.academic.service;

import com.ecom.academic.model.UserFile;
import com.ecom.academic.repository.AdminFileRepository;
import com.ecom.academic.repository.AdminFolderRepository;
import com.ecom.academic.repository.UserFileRepository;
import com.ecom.academic.repository.UserFolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Storage Quota (512MB) & Extensions (PDF, DOCX, DOC, ZIP) Tests")
class StorageQuotaAndExtensionsTest {

    private UserStorageService userStorageService;
    private AdminStorageService adminStorageService;
    private UserFolderRepository userFolderRepo;
    private UserFileRepository userFileRepo;
    private AdminFolderRepository adminFolderRepo;
    private AdminFileRepository adminFileRepo;

    @BeforeEach
    void setUp() {
        userFolderRepo = mock(UserFolderRepository.class);
        userFileRepo = mock(UserFileRepository.class);
        adminFolderRepo = mock(AdminFolderRepository.class);
        adminFileRepo = mock(AdminFileRepository.class);

        // Standard 512MB quota and pdf,doc,docx,zip extensions
        userStorageService = new UserStorageService(
                userFolderRepo, userFileRepo, 512L * 1024L * 1024L, "pdf,doc,docx,zip");

        adminStorageService = new AdminStorageService(
                adminFolderRepo, adminFileRepo, 10L * 1024L * 1024L * 1024L, "pdf,doc,docx,zip");
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "document.docx", "old_doc.doc", "archive.zip", "UPPER.PDF", "Mixed.DocX", "FILE.ZIP"})
    @DisplayName("UserStorage: Allowed extensions (PDF, DOCX, DOC, ZIP) should pass validation")
    void userStorage_allowedExtensions_shouldPass(String filename) {
        assertDoesNotThrow(() -> userStorageService.validateFileType(filename));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sheet.xlsx", "table.xls", "data.csv", "slides.pptx", "pres.ppt", "note.txt", "doc.rtf", "open.odt", "image.png", "script.sh", "app.exe", "nofileextension"})
    @DisplayName("UserStorage: Disallowed extensions should be rejected with IllegalArgumentException")
    void userStorage_disallowedExtensions_shouldBeRejected(String filename) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                userStorageService.validateFileType(filename));
        assertTrue(ex.getMessage().contains("ไม่อนุญาตให้อัปโหลดไฟล์ประเภท"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin_report.pdf", "manual.docx", "legacy.doc", "bundle.zip", "DOC.ZIP"})
    @DisplayName("AdminStorage: Allowed extensions (PDF, DOCX, DOC, ZIP) should pass validation")
    void adminStorage_allowedExtensions_shouldPass(String filename) {
        assertDoesNotThrow(() -> adminStorageService.validateFileType(filename));
    }

    @ParameterizedTest
    @ValueSource(strings = {"budget.xlsx", "raw.csv", "keynote.pptx", "log.txt", "graphic.jpg"})
    @DisplayName("AdminStorage: Disallowed extensions should be rejected")
    void adminStorage_disallowedExtensions_shouldBeRejected(String filename) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adminStorageService.validateFileType(filename));
        assertTrue(ex.getMessage().contains("ไม่อนุญาตให้อัปโหลดไฟล์"));
    }

    @Test
    @DisplayName("UserStorage: Quota under 512MB should pass")
    void userStorage_quotaUnder512MB_shouldPass() {
        UserFile existingFile = new UserFile();
        existingFile.setFileSize(100L * 1024L * 1024L); // 100MB
        when(userFileRepo.findByOwnerIdAndIsDeletedFalse(1)).thenReturn(List.of(existingFile));

        long incomingBytes = 50L * 1024L * 1024L; // 50MB incoming -> 150MB total <= 512MB
        assertDoesNotThrow(() -> userStorageService.validateStorageQuota(incomingBytes, 1));
    }

    @Test
    @DisplayName("UserStorage: Quota exceeding 512MB should be rejected")
    void userStorage_quotaExceeding512MB_shouldThrow() {
        UserFile existingFile = new UserFile();
        existingFile.setFileSize(500L * 1024L * 1024L); // 500MB
        when(userFileRepo.findByOwnerIdAndIsDeletedFalse(1)).thenReturn(List.of(existingFile));

        long incomingBytes = 20L * 1024L * 1024L; // 20MB incoming -> 520MB total > 512MB
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                userStorageService.validateStorageQuota(incomingBytes, 1));
        assertTrue(ex.getMessage().contains("พื้นที่เก็บข้อมูลเต็ม"));
    }

    @Test
    @DisplayName("UserStorage: Max storage bytes should be 512 MB (536870912 bytes)")
    void userStorage_maxStorageBytes_shouldBe512MB() {
        assertEquals(536870912L, userStorageService.getMaxStorageBytes());
    }
}
