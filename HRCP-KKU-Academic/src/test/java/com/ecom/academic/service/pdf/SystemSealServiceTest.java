package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ecom.support.TestCertificates;

/** ใบรับรองของระบบหาเองที่ secrets/ ข้าง app.jar — ไฟล์ที่ deploy.yml สร้าง ไม่ต้องตั้งค่าอะไร */
@DisplayName("SystemSealService: หาใบรับรองของระบบข้าง app.jar")
class SystemSealServiceTest {

    @TempDir
    Path appHome;

    private void deployWrote(String password) throws Exception {
        Path secrets = Files.createDirectories(appHome.resolve("secrets"));
        Files.write(secrets.resolve("system-seal.p12"), TestCertificates.validP12("HRCP e-Signature Seal"));
        Files.writeString(secrets.resolve("system-seal.p12.password"), password);
    }

    @Test
    @DisplayName("ไม่ได้ตั้งค่า: ใช้ไฟล์และรหัสที่ deploy.yml เขียนไว้ข้าง app.jar")
    void usesTheFilesNextToTheJar() throws Exception {
        deployWrote(TestCertificates.PIN + "\r\n");

        SystemSealService seal = new SystemSealService("", "", appHome);

        assertThat(seal.isConfigured()).isTrue();
        assertThat(seal.open()).isNotNull();
    }

    @Test
    @DisplayName("ยังไม่มีไฟล์: ปิดทางสำรอง")
    void offWithoutTheFile() {
        assertThat(new SystemSealService("", "", appHome).isConfigured()).isFalse();
    }

    @Test
    @DisplayName("ตั้งค่าไว้เอง: ใช้ค่าที่ตั้ง ไม่ใช้ไฟล์ข้าง app.jar")
    void explicitSettingsWin() throws Exception {
        deployWrote("wrong");
        Path elsewhere = Files.createTempFile(appHome, "other", ".p12");
        Files.write(elsewhere, TestCertificates.validP12("Other Seal"));

        SystemSealService seal = new SystemSealService(elsewhere.toString(), TestCertificates.PIN, appHome);

        assertThat(seal.open()).isNotNull();
    }
}
