package com.ecom.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfDigitalSignatureServiceTest {

    private PdfDigitalSignatureService service;

    @BeforeEach
    void setUp() {
        service = new PdfDigitalSignatureService();
    }

    @Test
    void testInspectAndSignPdfSuccessfully() throws Exception {
        String testPin = "TestKkuPin1234!";
        byte[] p12Bytes = generateTestP12("CN=ผศ.ดร.สมชาย ใจดี, O=Khon Kaen University, C=TH", testPin);
        byte[] samplePdf = generateSamplePdf("เอกสารขอรับการประเมินตำแหน่งทางวิชาการ");

        // 1. Inspect
        PdfDigitalSignatureService.ParsedCertificateInfo info = service.inspect(p12Bytes, testPin);
        assertNotNull(info);
        assertEquals("ผศ.ดร.สมชาย ใจดี", info.commonName());
        assertTrue(info.subjectDn().contains("Khon Kaen University"));

        // 2. Sign PDF
        byte[] signedPdf = service.signPdf(samplePdf, p12Bytes, testPin,
                "ผศ.ดร.สมชาย ใจดี", "อนุมัติคำขอ", "ขอนแก่น");

        assertNotNull(signedPdf);
        assertTrue(signedPdf.length > samplePdf.length);

        // 3. Verify with PDFBox that digital signature is registered
        try (PDDocument doc = Loader.loadPDF(signedPdf)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            assertEquals(1, signatures.size());
            PDSignature sig = signatures.get(0);
            assertEquals("ผศ.ดร.สมชาย ใจดี", sig.getName());
            assertEquals("อนุมัติคำขอ", sig.getReason());
            assertEquals("ขอนแก่น", sig.getLocation());
            assertEquals("Adobe.PPKLite", sig.getFilter());
        }
    }

    @Test
    void testSigningWithExactTimestampPreservation() throws Exception {
        String testPin = "ExactTimePin123!";
        byte[] p12Bytes = generateTestP12("CN=รศ.ดร.วิชัย ทดสอบ, O=Khon Kaen University, C=TH", testPin);
        byte[] samplePdf = generateSamplePdf("บันทึกข้อความ");

        java.time.LocalDateTime specificTime = java.time.LocalDateTime.of(2026, 9, 4, 15, 30, 45);
        byte[] signedPdf = service.signPdf(samplePdf, p12Bytes, testPin,
                "รศ.ดร.วิชัย ทดสอบ", "อนุมัติ", "มข.", specificTime);

        try (PDDocument doc = Loader.loadPDF(signedPdf)) {
            PDSignature sig = doc.getSignatureDictionaries().get(0);
            assertNotNull(sig.getSignDate());
            assertEquals(2026, sig.getSignDate().get(java.util.Calendar.YEAR));
            assertEquals(java.util.Calendar.SEPTEMBER, sig.getSignDate().get(java.util.Calendar.MONTH));
            assertEquals(4, sig.getSignDate().get(java.util.Calendar.DAY_OF_MONTH));
            assertEquals(15, sig.getSignDate().get(java.util.Calendar.HOUR_OF_DAY));
            assertEquals(30, sig.getSignDate().get(java.util.Calendar.MINUTE));
            assertEquals(45, sig.getSignDate().get(java.util.Calendar.SECOND));
        }
    }

    @Test
    void testMultiSignerIncrementalSigning() throws Exception {
        String pin1 = "PinDean1!";
        String pin2 = "PinPresident2!";
        byte[] p12Signer1 = generateTestP12("CN=คณบดี สมศักดิ์, O=KKU", pin1);
        byte[] p12Signer2 = generateTestP12("CN=อธิการบดี ประเสริฐ, O=KKU", pin2);

        byte[] samplePdf = generateSamplePdf("แบบประเมินผู้ขอกำหนดตำแหน่งทางวิชาการ");

        // Step 1: First signer
        byte[] signedOnce = service.signPdf(samplePdf, p12Signer1, pin1,
                "คณบดี สมศักดิ์", "เห็นชอบ", "คณะวิศวกรรมศาสตร์");

        // Step 2: Second signer on top of first signed PDF
        byte[] signedTwice = service.signPdf(signedOnce, p12Signer2, pin2,
                "อธิการบดี ประเสริฐ", "อนุมัติแต่งตั้ง", "สำนักงานอธิการบดี");

        // Verify that BOTH signatures are present and valid in the PDF
        try (PDDocument doc = Loader.loadPDF(signedTwice)) {
            List<PDSignature> signatures = doc.getSignatureDictionaries();
            assertEquals(2, signatures.size());
            assertEquals("คณบดี สมศักดิ์", signatures.get(0).getName());
            assertEquals("อธิการบดี ประเสริฐ", signatures.get(1).getName());
        }
    }

    // --- Helpers to create in-memory test artifacts without external files ---

    private byte[] generateSamplePdf(String content) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                stream.newLineAtOffset(50, 700);
                stream.showText("Academic Request Document");
                stream.endText();
            }
            document.save(baos);
            return baos.toByteArray();
        }
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
        ks.setKeyEntry("kku-digital-id", kp.getPrivate(), pin.toCharArray(), new Certificate[]{cert});

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ks.store(baos, pin.toCharArray());
        return baos.toByteArray();
    }
}
