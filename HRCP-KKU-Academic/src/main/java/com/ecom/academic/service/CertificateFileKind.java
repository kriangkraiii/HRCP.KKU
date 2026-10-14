package com.ecom.academic.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateFactory;
import java.util.Collections;
import java.util.Locale;

/**
 * บอกผู้ใช้ว่าไฟล์ที่อัปโหลดเป็นอะไร เมื่อมันไม่ใช่ .p12/.pfx ที่มีกุญแจส่วนตัว
 *
 * <p>ระบบลงนามฝั่งเซิร์ฟเวอร์ จึงต้องมีกุญแจส่วนตัวในไฟล์ ผู้ลงนามจากนอก มข. มักมีไฟล์ใบรับรองแบบอื่น
 * (.cer, .crt, .pem, .p7b) เดิมทุกกรณีได้ข้อความ "รหัสผ่านไม่ถูกต้อง หรือไฟล์ไม่สมบูรณ์" ผู้ใช้จึงลองรหัสซ้ำ
 * ไปเรื่อย ๆ ทั้งที่ไฟล์ใช้ไม่ได้ตั้งแต่ต้น (docs/PLAN-external-magic-link.md ข้อ 4.11)
 *
 * <p>ดูจากเนื้อไฟล์ ไม่ดูจากนามสกุล — คนเปลี่ยนชื่อ .cer เป็น .p12 ได้
 */
final class CertificateFileKind {

    static final String CERTIFICATE_ONLY = "ไฟล์นี้มีแต่ใบรับรอง ไม่มีกุญแจส่วนตัว จึงใช้ลงนามไม่ได้"
            + " กรุณาส่งออก (export) ใบรับรองเป็นไฟล์ .p12 หรือ .pfx พร้อมกุญแจส่วนตัว";

    static final String PEM_KEY = "ไฟล์ PEM ยังใช้ไม่ได้ กรุณาแปลงเป็นไฟล์ .p12 หรือ .pfx"
            + " (ขอให้ฝ่ายเทคโนโลยีสารสนเทศของหน่วยงานช่วยแปลงได้)";

    static final String NO_PRIVATE_KEY = "ไฟล์ .p12 นี้ไม่มีกุญแจส่วนตัว จึงใช้ลงนามไม่ได้"
            + " กรุณาส่งออก (export) ใบรับรองใหม่โดยเลือกรวมกุญแจส่วนตัว";

    static final String WRONG_PASSWORD = "Digital ID Password ไม่ถูกต้อง";

    static final String UNREADABLE = "Digital ID Password ไม่ถูกต้อง หรือไฟล์ .p12 ไม่สมบูรณ์";

    private CertificateFileKind() {
    }

    /** ปัญหาที่รู้ได้โดยไม่ต้องใช้รหัสผ่าน หรือ null เมื่อไฟล์ดูเหมือน PKCS#12 */
    static String problemBeforeOpening(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.US_ASCII).strip();
        if (head.startsWith("-----BEGIN")) {
            String type = head.substring("-----BEGIN".length()).strip().toUpperCase(Locale.ROOT);
            return type.startsWith("CERTIFICATE") || type.startsWith("X509") || type.startsWith("TRUSTED CERTIFICATE")
                    || type.startsWith("PKCS7") || type.startsWith("CMS") ? CERTIFICATE_ONLY : PEM_KEY;
        }
        // DER X.509 (.cer/.der) หรือ PKCS#7 (.p7b) — ไฟล์ PKCS#12 แปลงเป็นใบรับรองไม่ได้
        try {
            var parsed = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));
            return parsed.isEmpty() ? null : CERTIFICATE_ONLY;
        } catch (Exception e) {
            return null;
        }
    }

    /** เปิดไฟล์ได้แล้ว แต่มีกุญแจส่วนตัวไหม — .p12 ที่ส่งออกแบบไม่รวมกุญแจมีแต่ใบรับรอง */
    static boolean hasPrivateKey(byte[] bytes, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), password == null ? new char[0] : password.toCharArray());
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                return true;
            }
        }
        return false;
    }

    /** เปิดไฟล์ไม่สำเร็จ — แยกรหัสผิดออกจากไฟล์เสีย เท่าที่ข้อความของ provider บอกได้ */
    static String problemOpening(Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnrecoverableKeyException) {
                return WRONG_PASSWORD;
            }
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("password")) {
                return WRONG_PASSWORD;
            }
        }
        return UNREADABLE;
    }
}
