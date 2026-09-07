package com.ecom.academic.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Utility test to generate test PKCS#12 (.p12) files for developers to test
 * Digital ID signing in local/dev environment.
 */
class DevCertGeneratorTest {

    public static final String DEV_PASSWORD = "KkuDev1234!";

    @Test
    @DisplayName("Generate test .p12 certificates for development (All Roles)")
    void generateDevCertificates() throws Exception {
        long now = System.currentTimeMillis();
        Date validFrom = new Date(now - (24L * 60 * 60 * 1000)); // 1 day ago
        Date validTo = new Date(now + (2L * 365 * 24 * 60 * 60 * 1000)); // 2 years later

        Date expiredFrom = new Date(now - (2L * 365 * 24 * 60 * 60 * 1000)); // 2 years ago
        Date expiredTo = new Date(now - (24L * 60 * 60 * 1000)); // 1 day ago

        String issuer = "CN=ODT KKU CA, O=Khon Kaen University, C=TH";

        // Map of filename -> Subject DN
        java.util.Map<String, String> validCerts = new java.util.LinkedHashMap<>();
        validCerts.put("kku-digital-id-dev.p12", "CN=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล, OU=ODT KKU, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-admin.p12", "CN=ผู้ดูแลระบบ (Admin), OU=HR Division, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-dean.p12", "CN=ศ.ดร.สมชาย ใจดี (คณบดี), OU=Faculty of Science, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-head.p12", "CN=รศ.ดร.วิชัย มั่นคง (หัวหน้าสาขาวิชา), OU=Department of Computer Science, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-committee.p12", "CN=ศ.ดร.ประเสริฐ ดีเลิศ (กรรมการผู้ทรงคุณวุฒิ), OU=Academic Committee, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-hr.p12", "CN=นางสาวกานดา นามดี (เจ้าหน้าที่ HR), OU=Human Resources, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-applicant.p12", "CN=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล (ผู้ยื่นคำร้อง), OU=Faculty of Science, O=Khon Kaen University, C=TH");
        validCerts.put("kku-digital-id-staff.p12", "CN=นายสมศักดิ์ รักงาน (เจ้าหน้าที่ทั่วไป), OU=Faculty of Science, O=Khon Kaen University, C=TH");

        // Expired certs for negative testing
        java.util.Map<String, String> expiredCerts = new java.util.LinkedHashMap<>();
        expiredCerts.put("kku-digital-id-expired-dev.p12", "CN=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล (หมดอายุ), OU=ODT KKU, O=Khon Kaen University, C=TH");

        // Target directories
        Path[] targets = new Path[] {
                Paths.get("dev-certs"),
                Paths.get("../dev-certs")
        };

        for (Path dir : targets) {
            Files.createDirectories(dir);
        }

        System.out.println("==========================================================");
        System.out.println("  Generating Dev Certificates for All Roles...");
        System.out.println("  Digital ID Password: " + DEV_PASSWORD);
        System.out.println("==========================================================");

        // Generate and write valid certs
        for (var entry : validCerts.entrySet()) {
            String filename = entry.getKey();
            String dn = entry.getValue();
            byte[] p12 = createP12(dn, issuer, DEV_PASSWORD, validFrom, validTo);
            for (Path dir : targets) {
                Files.write(dir.resolve(filename), p12);
            }
            System.out.println("  [VALID]   " + filename + " -> " + dn);
        }

        // Generate and write expired certs
        for (var entry : expiredCerts.entrySet()) {
            String filename = entry.getKey();
            String dn = entry.getValue();
            byte[] p12 = createP12(dn, issuer, DEV_PASSWORD, expiredFrom, expiredTo);
            for (Path dir : targets) {
                Files.write(dir.resolve(filename), p12);
            }
            System.out.println("  [EXPIRED] " + filename + " -> " + dn);
        }

        System.out.println("==========================================================");
        System.out.println("  All role certificates generated successfully in dev-certs/ !");
        System.out.println("==========================================================");
    }

    private byte[] createP12(String subjectDn, String issuerDn, String pin, Date from, Date to) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        X500Name subject = new X500Name(subjectDn);
        X500Name issuer = new X500Name(issuerDn);
        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                issuer, BigInteger.valueOf(System.currentTimeMillis()), from, to, subject, kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certGen.build(signer));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("kku-digital-id", kp.getPrivate(), pin.toCharArray(), new Certificate[]{cert});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, pin.toCharArray());
        return baos.toByteArray();
    }
}
