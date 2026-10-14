package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.support.TestCertificates;

/**
 * ไฟล์ใบรับรองแบบที่ผู้ลงนามจากนอก มข. มักมี — แต่ละแบบต้องได้ข้อความของตัวเอง ไม่ใช่ "รหัสผ่านไม่ถูกต้อง"
 * (docs/PLAN-external-magic-link.md ข้อ 4.11)
 */
@DisplayName("CertificateFileKind: บอกว่าไฟล์ที่อัปโหลดเป็นอะไร เมื่อไม่ใช่ .p12 ที่มีกุญแจส่วนตัว")
class CertificateFileKindTest {

    private static byte[] p12;
    private static X509Certificate certificate;

    @BeforeAll
    static void aRealDigitalId() throws Exception {
        p12 = TestCertificates.validP12("วิภา ภายนอก");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(p12), TestCertificates.PIN.toCharArray());
        certificate = (X509Certificate) ks.getCertificate(Collections.list(ks.aliases()).get(0));
    }

    @Test
    @DisplayName(".p12 ที่มีกุญแจ: ไม่มีปัญหา")
    void aRealP12IsFine() throws Exception {
        assertThat(CertificateFileKind.problemBeforeOpening(p12)).isNull();
        assertThat(CertificateFileKind.hasPrivateKey(p12, TestCertificates.PIN)).isTrue();
    }

    @Test
    @DisplayName(".cer แบบ DER และแบบ PEM: มีแต่ใบรับรอง")
    void certificatesWithoutAKey() throws Exception {
        byte[] der = certificate.getEncoded();
        byte[] pem = ("-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder().encodeToString(der) + "\n-----END CERTIFICATE-----\n")
                .getBytes(StandardCharsets.US_ASCII);

        assertThat(CertificateFileKind.problemBeforeOpening(der)).isEqualTo(CertificateFileKind.CERTIFICATE_ONLY);
        assertThat(CertificateFileKind.problemBeforeOpening(pem)).isEqualTo(CertificateFileKind.CERTIFICATE_ONLY);
    }

    @Test
    @DisplayName(".p7b (chain): มีแต่ใบรับรอง")
    void aCertificateChain() throws Exception {
        byte[] p7b = CertificateFactory.getInstance("X.509").generateCertPath(List.of(certificate)).getEncoded("PKCS7");

        assertThat(CertificateFileKind.problemBeforeOpening(p7b)).isEqualTo(CertificateFileKind.CERTIFICATE_ONLY);
    }

    @Test
    @DisplayName("กุญแจแบบ PEM: ต้องแปลงเป็น .p12 ก่อน")
    void aPemPrivateKey() {
        byte[] key = "-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----\n".getBytes(StandardCharsets.US_ASCII);

        assertThat(CertificateFileKind.problemBeforeOpening(key)).isEqualTo(CertificateFileKind.PEM_KEY);
    }

    @Test
    @DisplayName(".p12 ที่ส่งออกแบบไม่รวมกุญแจ: เปิดได้ แต่ไม่มีกุญแจส่วนตัว")
    void aP12WithoutAKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("cert", certificate);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, TestCertificates.PIN.toCharArray());

        assertThat(CertificateFileKind.problemBeforeOpening(out.toByteArray())).isNull();
        assertThat(CertificateFileKind.hasPrivateKey(out.toByteArray(), TestCertificates.PIN)).isFalse();
    }

    @Test
    @DisplayName("รหัสผิด: บอกว่ารหัสผิด ไม่ใช่ไฟล์เสีย")
    void aWrongPassword() {
        Exception thrown = null;
        try {
            KeyStore.getInstance("PKCS12").load(new ByteArrayInputStream(p12), "wrong".toCharArray());
        } catch (Exception e) {
            thrown = e;
        }

        assertThat(thrown).isNotNull();
        assertThat(CertificateFileKind.problemOpening(thrown)).isEqualTo(CertificateFileKind.WRONG_PASSWORD);
    }
}
