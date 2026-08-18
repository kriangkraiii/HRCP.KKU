package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileImageStorageTest {

    @TempDir
    Path tempUploadDir;

    private ProfileImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ProfileImageStorage(tempUploadDir.toString());
    }

    @Test
    @DisplayName("ห้ามลบไฟล์ default.png หรือไฟล์ระบบที่อยู่ใน Whitelist")
    void protectedFilesMustNeverBeDeleted() throws Exception {
        // Create dummy default.png and logo_cphr.jpg in temp upload dir
        Path defaultImg = tempUploadDir.resolve("default.png");
        Path logoImg = tempUploadDir.resolve("cphr_new.png");
        Files.writeString(defaultImg, "dummy");
        Files.writeString(logoImg, "dummy");

        assertThat(storage.isProtected("default.png")).isTrue();
        assertThat(storage.isProtected("cphr_new.png")).isTrue();
        assertThat(storage.isProtected("kku_logo.png")).isTrue();

        // Attempt deletion
        boolean deletedDefault = storage.deleteIfPresent("default.png");
        boolean deletedLogo = storage.deleteIfPresent("cphr_new.png");

        assertThat(deletedDefault).isFalse();
        assertThat(deletedLogo).isFalse();
        assertThat(Files.exists(defaultImg)).isTrue();
        assertThat(Files.exists(logoImg)).isTrue();
    }

    @Test
    @DisplayName("ลบไฟล์รูปภาพผู้ใช้ทั่วไปที่ไม่ใช่ Whitelist ได้สำเร็จ")
    void customUserImageCanBeDeleted() throws Exception {
        Path userImg = tempUploadDir.resolve("abc123_custom_avatar.jpg");
        Files.writeString(userImg, "test-image-content");

        assertThat(Files.exists(userImg)).isTrue();
        assertThat(storage.isProtected("abc123_custom_avatar.jpg")).isFalse();

        boolean deleted = storage.deleteIfPresent("abc123_custom_avatar.jpg");

        assertThat(deleted).isTrue();
        assertThat(Files.exists(userImg)).isFalse();
    }

    @Test
    @DisplayName("ดึงรายชื่อไฟล์รูปภาพทั้งหมดในโฟลเดอร์ได้อย่างถูกต้อง")
    void listsAllStoredImages() throws Exception {
        Files.writeString(tempUploadDir.resolve("user1.jpg"), "1");
        Files.writeString(tempUploadDir.resolve("user2.png"), "2");

        var list = storage.listAllStoredImages();
        assertThat(list).contains("user1.jpg", "user2.png");
    }
}
