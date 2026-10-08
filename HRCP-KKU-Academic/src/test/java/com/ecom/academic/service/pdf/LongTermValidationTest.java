package com.ecom.academic.service.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.support.TestPki;

/**
 * ลายมือชื่อตรวจสอบได้ระยะยาว (PAdES baseline LT): ตราประทับเวลา + สายใบรับรองและสถานะในไฟล์
 * ทดสอบกับ PKI จำลองบน localhost — TSA, OCSP, CRL และใบรับรองผู้ออกให้ดาวน์โหลด
 */
@DisplayName("ลายมือชื่อตรวจสอบได้ระยะยาว — ตราประทับเวลาและสถานะใบรับรอง")
class LongTermValidationTest {

    private TestPki pki;

    @BeforeEach
    void startPki() throws Exception {
        pki = new TestPki();
    }

    @AfterEach
    void stopPki() {
        pki.close();
    }

    private LongTermValidation configured(boolean tsaRequired, boolean ltvRequired) {
        return new LongTermValidation(pki.tsaUrl(), "", "", "", 10_000, tsaRequired, true, 10_000, ltvRequired);
    }

    private CmsSigner signer() throws Exception {
        return CmsSigner.open(pki.signerP12(), TestPki.PIN.toCharArray());
    }

    private static SignerInformation signerInfo(byte[] cms) throws Exception {
        try (ASN1InputStream in = new ASN1InputStream(cms)) {
            return new CMSSignedData(in.readObject().getEncoded()).getSignerInfos().getSigners().iterator().next();
        }
    }

    /** PDF หน้าเดียวที่มีช่องลายเซ็น sig_test — แบบเดียวกับไฟล์ตั้งต้นของซองลงนาม */
    private static byte[] blankForm() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDAcroForm form = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(form);
            PDSignatureField field = new PDSignatureField(form);
            field.setPartialName("sig_test");
            PDAnnotationWidget widget = field.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(50, 50, 200, 60));
            widget.setPage(page);
            page.getAnnotations().add(widget);
            form.getFields().add(field);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] signForm(CmsSigner signer) throws Exception {
        return new PdfIncrementService(ThaiText.sarabun()).sign(blankForm(), new PdfIncrementService.SignSpec(signer,
                "sig_test", Map.of(), null, true, null, "Test Signer", "ทดสอบ", "มหาวิทยาลัยขอนแก่น",
                Calendar.getInstance()));
    }

    @Test
    @DisplayName("ลายมือชื่อมีตราประทับเวลาของหน่วยงานรับรองเวลา ครอบค่าลายมือชื่อจริง")
    void signatureCarriesATimestampOverItsValue() throws Exception {
        CmsSigner signer = configured(true, false).prepare(signer());

        SignerInformation si = signerInfo(signer.sign(new ByteArrayInputStream("เนื้อหา".getBytes())));

        Attribute attribute = si.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
        assertThat(attribute).as("signature-time-stamp").isNotNull();
        TimeStampToken token = new TimeStampToken(new CMSSignedData(
                attribute.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
        assertThat(token.getTimeStampInfo().getMessageImprintDigest())
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(si.getSignature()));
        assertThat(token.isSignatureValid(new JcaSimpleSignerInfoVerifierBuilder().build(pki.tsaCertificate())))
                .isTrue();
    }

    @Test
    @DisplayName("ติดต่อหน่วยงานรับรองเวลาไม่ได้ — ตั้งให้บังคับ: ไม่ลงนาม / ไม่บังคับ: ลงนามโดยไม่มีตราประทับเวลา")
    void unreachableAuthority() throws Exception {
        pki.tsaDown.set(true);

        assertThatThrownBy(() -> configured(true, false).prepare(signer())
                .sign(new ByteArrayInputStream(new byte[] { 1 })))
                .isInstanceOf(TimestampClient.TimestampUnavailableException.class);

        SignerInformation si = signerInfo(configured(false, false).prepare(signer())
                .sign(new ByteArrayInputStream(new byte[] { 1 })));
        assertThat(si.getUnsignedAttributes() == null
                || si.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken) == null)
                .isTrue();
    }

    @Test
    @DisplayName("ไม่ตั้งค่าบริการตราประทับเวลา — ทำงานแบบเดิม ไม่มีตราประทับเวลาและไม่ฝังสถานะ")
    void offWithoutConfiguration() throws Exception {
        LongTermValidation off = new LongTermValidation("", "", "", "", 10_000, true, true, 10_000, false);
        assertThat(off.enabled()).isFalse();
        byte[] signed = signForm(off.prepare(signer()));

        assertThat(off.validationDataFor(signed)).isNull();
        assertThat(pki.tsaCalls.get()).isZero();
    }

    @Test
    @DisplayName("ลงนามแล้วฝังสายใบรับรอง (ดาวน์โหลดรากเอง) สถานะ OCSP ของผู้ลงนาม และ CRL ของ TSA — ลายมือชื่อเดิมยังถูกต้อง")
    void validationDataIsEmbedded() throws Exception {
        LongTermValidation ltv = configured(true, true);
        byte[] signed = signForm(ltv.prepare(signer()));

        byte[] withData = ltv.validationDataFor(signed);

        assertThat(withData).isNotNull();
        assertThat(java.util.Arrays.copyOf(withData, signed.length)).as("ต่อท้ายไฟล์เดิม").isEqualTo(signed);
        assertThat(pki.ocspCalls.get()).isPositive();
        try (PDDocument doc = Loader.loadPDF(withData)) {
            COSDictionary dss = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.getPDFName("DSS"));
            assertThat(dss).isNotNull();
            assertThat(dss.getCOSArray(COSName.getPDFName("Certs")).size())
                    .as("ผู้ลงนาม ราก และ TSA").isGreaterThanOrEqualTo(3);
            assertThat(dss.getCOSArray(COSName.getPDFName("OCSPs")).size()).isEqualTo(1);
            assertThat(dss.getCOSArray(COSName.getPDFName("CRLs")).size()).isEqualTo(1);
            assertThat(dss.getCOSDictionary(COSName.getPDFName("VRI")).keySet()).hasSize(1);
        }
        List<PdfIncrementService.SignatureCheck> checks = PdfIncrementService.verify(withData);
        assertThat(checks).singleElement().satisfies(c -> {
            assertThat(c.valid()).isTrue();
            assertThat(c.signerDn()).isEqualTo(new JcaX509CertificateHolder(
                    (java.security.cert.X509Certificate) signer().certificate()).getSubject().toString());
        });
    }

    @Test
    @DisplayName("ข้อมูลที่ฝังไว้แล้วไม่ฝังซ้ำ — ลงนามด้วยใบรับรองเดิมอีกครั้งไม่เพิ่มอะไร")
    void nothingTwice() throws Exception {
        LongTermValidation ltv = configured(true, false);
        byte[] withData = ltv.validationDataFor(signForm(ltv.prepare(signer())));

        ValidationDataService.Result again = new ValidationDataService(Duration.ofSeconds(10))
                .addForLatestSignature(withData);

        assertThat(again.added().nothing()).isTrue();
        assertThat(again.added().withoutStatus()).isZero();
        assertThat(again.pdf()).isSameAs(withData);
    }
}
