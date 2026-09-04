package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Optional;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;
import com.ecom.model.UserDtls;

class UserDigitalCertificateServiceTest {

    private UserDigitalCertificateRepository repository;
    private DigitalCertificateStorage storage;
    private CertificatePinEncryptionService pinEncryptionService;
    private PdfDigitalSignatureService pdfSignatureService;
    private UserDigitalCertificateService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserDigitalCertificateRepository.class);
        storage = mock(DigitalCertificateStorage.class);
        pinEncryptionService = new CertificatePinEncryptionService("TestMasterSecretKey2026!#SecureGCM");
        pdfSignatureService = new PdfDigitalSignatureService();
        service = new UserDigitalCertificateService(repository, storage, pinEncryptionService, pdfSignatureService);
    }

    @Test
    void testPinEncryptionAndDecryption() {
        String originalPin = "MyKKUDigitalPin999#";
        String encrypted = pinEncryptionService.encrypt(originalPin);
        assertNotNull(encrypted);
        assertTrue(!encrypted.equals(originalPin));

        String decrypted = pinEncryptionService.decrypt(encrypted);
        assertEquals(originalPin, decrypted);
    }

    @Test
    void testRegisterCertificateSuccess() throws Exception {
        UserDtls user = new UserDtls();
        user.setId(101);
        user.setName("ดร.กฤษดา มหาวิทยาลัย");

        String pin = "SecretPin1234!";
        byte[] p12Bytes = generateTestP12("CN=ดร.กฤษดา มหาวิทยาลัย, O=Khon Kaen University, C=TH", pin);

        when(storage.store(any())).thenReturn("cert_uuid_test.p12");
        when(repository.save(any(UserDigitalCertificate.class))).thenAnswer(invocation -> {
            UserDigitalCertificate cert = invocation.getArgument(0);
            cert.setId(55L);
            return cert;
        });

        UserDigitalCertificateService.SaveResult result = service.registerCertificate(
                user, p12Bytes, "kku_digital_id.p12", pin, true);

        assertTrue(result.ok());
        assertNotNull(result.certificate());
        assertEquals("ดร.กฤษดา มหาวิทยาลัย", result.certificate().getCommonName());
        assertTrue(result.certificate().hasSavedPin());

        verify(repository).deactivateAllFor(101);
        verify(repository).save(any(UserDigitalCertificate.class));
    }

    @Test
    void testVerifyP12ValidAndInvalidPassword() throws Exception {
        String correctPin = "CorrectKkuPin123!";
        byte[] p12Bytes = generateTestP12("CN=ศ.ดร.วิโรจน์ ทดสอบ, O=KKU", correctPin);

        // Valid
        UserDigitalCertificateService.VerifyResult resValid = service.verifyP12(p12Bytes, correctPin);
        assertTrue(resValid.ok());
        assertEquals("ศ.ดร.วิโรจน์ ทดสอบ", resValid.commonName());

        // Invalid Password
        UserDigitalCertificateService.VerifyResult resInvalid = service.verifyP12(p12Bytes, "WrongPassword999!");
        assertTrue(!resInvalid.ok());
        assertTrue(resInvalid.error().contains("Digital ID Password ไม่ถูกต้อง"));

        // Missing file or password
        assertTrue(!service.verifyP12(null, correctPin).ok());
        assertTrue(!service.verifyP12(p12Bytes, "").ok());
    }

    @Test
    void testUpdatePasswordForActiveCertificate() throws Exception {
        UserDtls user = new UserDtls();
        user.setId(202);

        String originalPin = "InitialPin111!";
        byte[] p12Bytes = generateTestP12("CN=อาจารย์ สมหญิง, O=KKU", originalPin);

        UserDigitalCertificate cert = new UserDigitalCertificate();
        cert.setId(88L);
        cert.setUser(user);
        cert.setCertificatePath("active_cert.p12");
        cert.setActive(true);
        cert.setEncryptedPin(pinEncryptionService.encrypt(originalPin));

        when(repository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(202)).thenReturn(Optional.of(cert));
        when(storage.read("active_cert.p12")).thenReturn(p12Bytes);

        // 1. Try to update with wrong password for this file -> fails
        UserDigitalCertificateService.SaveResult failRes = service.updatePassword(user, "WrongPin222!");
        assertTrue(!failRes.ok());

        // 2. Try to update with correct password -> succeeds
        UserDigitalCertificateService.SaveResult okRes = service.updatePassword(user, originalPin);
        assertTrue(okRes.ok());
        assertEquals(originalPin, pinEncryptionService.decrypt(cert.getEncryptedPin()));
        verify(repository).save(cert);
    }

    @Test
    void testResolvePinWithOnDemandPriority() {
        UserDigitalCertificate cert = new UserDigitalCertificate();
        cert.setEncryptedPin(pinEncryptionService.encrypt("SavedPin111"));

        // When onDemandPin is provided, it must take precedence
        String resolved = service.resolvePin(cert, "OnDemandPin222");
        assertEquals("OnDemandPin222", resolved);

        // When onDemandPin is null, falls back to saved PIN
        String fallback = service.resolvePin(cert, null);
        assertEquals("SavedPin111", fallback);
    }

    private byte[] generateTestP12(String dn, String pin) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60000);
        Date notAfter = new Date(now + (365L * 24 * 60 * 60 * 1000));

        X500Name subject = new X500Name(dn);
        X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), notBefore, notAfter, subject, kp.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certGen.build(signer));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("kku-id", kp.getPrivate(), pin.toCharArray(), new Certificate[]{cert});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, pin.toCharArray());
        return baos.toByteArray();
    }
}
