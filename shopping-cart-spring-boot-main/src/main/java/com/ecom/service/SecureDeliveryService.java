package com.ecom.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/**
 * Secure Digital Delivery Service
 * 
 * สร้างไฟล์ ZIP ที่เข้ารหัสด้วย AES-256 สำหรับทุกการดาวน์โหลด
 * โดยใช้ License Key ของผู้ซื้อเป็นรหัสผ่านในการปลดล็อก
 * 
 * - ผู้ใช้ A ได้ Key "AA11-22BB" → ใช้เปิดได้เฉพาะไฟล์ของตนเอง
 * - ผู้ใช้ B ได้ Key "ZZ99-88YY" → ใช้เปิดได้เฉพาะไฟล์ของตนเอง
 * - ผู้ใช้ B ไม่สามารถใช้ Key ของ A เปิดไฟล์ของตนเองได้
 */
@Service
public class SecureDeliveryService {

    @Value("${game.files.base-path:uploads/game_files}")
    private String gameFilesBasePath;

    /**
     * สร้าง Encrypted ZIP file แบบ AES-256 โดย stream ตรงไปที่ OutputStream
     * ไม่สร้างไฟล์ชั่วคราวบน disk → ประหยัด storage และปลอดภัยกว่า
     * 
     * @param gameFilePath  path ไปยังไฟล์เกม (relative หรือ absolute)
     * @param licenseKey    License Key ของผู้ซื้อ ใช้เป็น password
     * @param outputStream  OutputStream ที่จะส่งไฟล์ ZIP เข้ารหัสออกไป
     * @throws IOException  หากไม่พบไฟล์เกม หรือเกิดข้อผิดพลาดในการสร้าง ZIP
     */
    public void createEncryptedZip(String gameFilePath, String licenseKey, OutputStream outputStream) 
            throws IOException {
        
        // Resolve the game file path
        Path filePath = resolveGameFilePath(gameFilePath);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Game file not found: " + filePath.toString());
        }

        // Read the game file bytes
        byte[] gameFileBytes = Files.readAllBytes(filePath);
        String originalFileName = filePath.getFileName().toString();
        
        // Configure AES-256 encryption parameters
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        zipParameters.setFileNameInZip(originalFileName);

        // Create encrypted ZIP and write directly to output stream
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, licenseKey.toCharArray())) {
            zipOutputStream.putNextEntry(zipParameters);
            zipOutputStream.write(gameFileBytes);
            zipOutputStream.closeEntry();
        }
    }

    /**
     * สร้าง Encrypted ZIP file แบบ AES-256 เป็น byte array
     * ใช้สำหรับกรณีที่ต้องการ byte array กลับไป (เช่น ส่ง email)
     * 
     * @param gameFilePath  path ไปยังไฟล์เกม
     * @param licenseKey    License Key ของผู้ซื้อ
     * @return byte array ของ encrypted ZIP
     * @throws IOException  หากไม่พบไฟล์หรือเกิดข้อผิดพลาด
     */
    public byte[] createEncryptedZipBytes(String gameFilePath, String licenseKey) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        createEncryptedZip(gameFilePath, licenseKey, baos);
        return baos.toByteArray();
    }

    /**
     * ตรวจสอบว่าไฟล์เกมมีอยู่จริงหรือไม่
     */
    public boolean gameFileExists(String gameFilePath) {
        if (gameFilePath == null || gameFilePath.isEmpty()) {
            return false;
        }
        try {
            Path filePath = resolveGameFilePath(gameFilePath);
            return Files.exists(filePath) && Files.isRegularFile(filePath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * คืนชื่อไฟล์สำหรับ ZIP ที่จะส่งให้ผู้ใช้ดาวน์โหลด
     * เช่น "MyGame_Locked.zip"
     */
    public String getLockedZipFileName(String gameTitle) {
        // Sanitize the game title for filename
        String safeName = gameTitle.replaceAll("[^a-zA-Z0-9\\s_-]", "")
                                   .replaceAll("\\s+", "_")
                                   .trim();
        if (safeName.isEmpty()) {
            safeName = "Game";
        }
        return safeName + "_Locked.zip";
    }

    /**
     * Resolve game file path - รองรับทั้ง absolute และ relative path
     */
    private Path resolveGameFilePath(String gameFilePath) {
        Path path = Paths.get(gameFilePath);
        if (path.isAbsolute()) {
            return path;
        }
        // Try relative to base path first
        Path basePath = Paths.get(gameFilesBasePath);
        Path resolved = basePath.resolve(gameFilePath);
        if (Files.exists(resolved)) {
            return resolved;
        }
        // Try relative to working directory
        return Paths.get(System.getProperty("user.dir")).resolve(gameFilePath);
    }
}
