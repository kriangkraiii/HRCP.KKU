package com.ecom.util;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ตรวจว่าไฟล์ที่อัปโหลดเป็นไฟล์เอกสารที่อนุญาตหรือไม่
 *
 * <p>เดิมการตรวจนี้อยู่ใน {@code UserStorageService} ของคลังไฟล์ส่วนตัว
 * และมีที่อื่นยืมไปใช้ด้วย พอคลังไฟล์นั้นถูกปิดไปทั้งชุด การตรวจก็หายตามไป
 * ทั้งที่การอัปโหลดไฟล์แก้ไขของผู้ยื่นยังต้องใช้อยู่
 *
 * <p>แยกออกมาเป็นคลาสของตัวเองเพื่อไม่ให้ผูกกับฟีเจอร์ใดฟีเจอร์หนึ่งอีก
 * ถ้าวันหนึ่งมีที่อื่นต้องตรวจแบบเดียวกัน ให้ฉีดตัวนี้ไปใช้ อย่าคัดลอกรายการ
 * นามสกุลไปวางซ้ำ
 */
@Component
public class DocumentFileTypeValidator {

    private final Set<String> allowedExtensions;

    public DocumentFileTypeValidator(
            @Value("${app.upload.document-extensions:pdf,doc,docx,zip}") String allowedExtensionsStr) {
        this.allowedExtensions = Arrays.stream(allowedExtensionsStr.split(","))
                .map(ext -> ext.trim().toLowerCase())
                .filter(ext -> !ext.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * โยน {@link IllegalArgumentException} พร้อมข้อความภาษาไทยถ้าไฟล์ไม่ผ่าน
     *
     * <p>ข้อความบอกทั้งนามสกุลที่ถูกปฏิเสธและรายการที่อนุญาต เพราะผู้ใช้ที่
     * เจอแค่คำว่า "ไฟล์ไม่ถูกต้อง" จะเดาไม่ออกว่าต้องแปลงไฟล์เป็นอะไร
     */
    public void validate(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("ชื่อไฟล์ไม่ถูกต้อง");
        }

        String ext = extractExtension(filename);
        if (ext.isEmpty() || !allowedExtensions.contains(ext)) {
            String allowed = allowedExtensions.stream().sorted().map(e -> "." + e)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "ไม่อนุญาตให้อัปโหลดไฟล์ประเภท ." + (ext.isEmpty() ? "(ไม่มีนามสกุล)" : ext)
                    + " — อนุญาตเฉพาะไฟล์เอกสาร: " + allowed);
        }
    }

    private String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
