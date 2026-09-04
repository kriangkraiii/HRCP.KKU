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
    @DisplayName("Generate test .p12 certificates for development")
    void generateDevCertificates() throws Exception {
        // 1. Valid certificate (2 years validity)
        long now = System.currentTimeMillis();
        Date validFrom = new Date(now - (24L * 60 * 60 * 1000)); // 1 day ago
        Date validTo = new Date(now + (2L * 365 * 24 * 60 * 60 * 1000)); // 2 years later
        byte[] validP12 = createP12(
                "CN=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล, OU=ODT KKU, O=Khon Kaen University, C=TH",
                "CN=ODT KKU CA, O=Khon Kaen University, C=TH",
                DEV_PASSWORD, validFrom, validTo);

        // 2. Expired certificate (expired 1 day ago)
        Date expiredFrom = new Date(now - (2L * 365 * 24 * 60 * 60 * 1000)); // 2 years ago
        Date expiredTo = new Date(now - (24L * 60 * 60 * 1000)); // 1 day ago
        byte[] expiredP12 = createP12(
                "CN=ผศ.ดร.เกรียงไกร เกียรติบูรณกุล (หมดอายุ), OU=ODT KKU, O=Khon Kaen University, C=TH",
                "CN=ODT KKU CA, O=Khon Kaen University, C=TH",
                DEV_PASSWORD, expiredFrom, expiredTo);

        // Target directories
        Path[] targets = new Path[] {
                Paths.get("dev-certs"),
                Paths.get("../dev-certs")
        };

        for (Path dir : targets) {
            Files.createDirectories(dir);
            Files.write(dir.resolve("kku-digital-id-dev.p12"), validP12);
            Files.write(dir.resolve("kku-digital-id-expired-dev.p12"), expiredP12);
        }

        System.out.println("==========================================================");
        System.out.println("  Dev Certificates Generated Successfully!");
        System.out.println("  1. Valid Cert:   dev-certs/kku-digital-id-dev.p12");
        System.out.println("  2. Expired Cert: dev-certs/kku-digital-id-expired-dev.p12");
        System.out.println("  Digital ID Password: " + DEV_PASSWORD);
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
