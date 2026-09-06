package com.ecom.support;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * สร้างไฟล์ .p12 ที่ใช้งานได้จริงในหน่วยความจำ สำหรับเทส
 *
 * <p>ระบบบังคับให้ผู้ลงนามต้องมีใบรับรอง Digital ID (.p12) ของมหาวิทยาลัยติดตั้งไว้
 * ก่อนจึงจะลงนามได้ — {@code SigningController.sign} ตรวจข้อนี้เป็นอย่างแรกสุด
 * ก่อนจะแตะ workflow ใด ๆ เทสที่เดินเส้นทางลงนามจึงต้องมีใบรับรองจริงให้ระบบตรวจ
 * ไม่ใช่แค่ภาพลายเซ็น
 *
 * <p>ใบรับรองที่สร้างจากที่นี่เป็นแบบ self-signed เซ็นด้วยกุญแจของตัวเอง ใช้ได้กับ
 * ทุกอย่างที่ระบบต้องการ (เปิดด้วยรหัสผ่านได้, อ่าน CN/ผู้ออก/วันหมดอายุได้,
 * ใช้เซ็น PDF ได้) โดยไม่ต้องพึ่งไฟล์จริงจาก i.kku.ac.th ที่เอามา commit ไม่ได้
 */
public final class TestCertificates {

    /** รหัสผ่านของไฟล์ .p12 ที่สร้างจากคลาสนี้ทั้งหมด */
    public static final String PIN = "Test-P12-Pin!";

    private TestCertificates() {
    }

    /** ไฟล์ .p12 ที่ยังไม่หมดอายุ พร้อมใช้ลงนาม */
    public static byte[] validP12(String commonName) throws Exception {
        return p12(commonName, PIN, -60_000L, 365L * 24 * 60 * 60 * 1000);
    }

    /**
     * ไฟล์ .p12 ที่หมดอายุไปแล้ว
     *
     * <p>มีไว้เพื่อให้เทสด่าน "ใบรับรองหมดอายุ" ได้ของจริงมาตรวจ แทนการ mock
     * ซึ่งจะพิสูจน์แค่ว่า mock ทำงาน ไม่ได้พิสูจน์ว่าระบบอ่านวันหมดอายุถูก
     */
    public static byte[] expiredP12(String commonName) throws Exception {
        long twoYears = 2L * 365 * 24 * 60 * 60 * 1000;
        return p12(commonName, PIN, -twoYears, twoYears - (30L * 24 * 60 * 60 * 1000));
    }

    private static byte[] p12(String commonName, String pin, long startOffsetMs, long lifetimeMs)
            throws Exception {

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now + startOffsetMs);
        Date notAfter = new Date(notBefore.getTime() + lifetimeMs);

        X500Name subject = new X500Name("CN=" + commonName + ", O=Khon Kaen University, C=TH");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), notBefore, notAfter, subject, keys.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keys.getPrivate());
        X509Certificate certificate =
                new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry("kku-id", keys.getPrivate(), pin.toCharArray(),
                new Certificate[] { certificate });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        store.store(out, pin.toCharArray());
        return out.toByteArray();
    }
}
