package com.ecom.academic.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core engine for PAdES-BES digital signing on PDF documents using PKCS#12 (.p12) keystores.
 *
 * <p>Produces cryptographic digital signatures compliant with PDF standards (Adobe Green Checkmark).
 * Operates as an invisible cryptographic layer over the already-rendered visual signature,
 * completely avoiding the need for ugly boxed tables or .fdf stamps.
 */
@Service
public class PdfDigitalSignatureService {

    private static final Logger log = LoggerFactory.getLogger(PdfDigitalSignatureService.class);

    public record ParsedCertificateInfo(
            String subjectDn,
            String issuerDn,
            String serialNumber,
            LocalDateTime validFrom,
            LocalDateTime validTo,
            String commonName) {
    }

    /**
     * Inspects and validates a .p12 certificate file with the provided PIN.
     *
     * @return ParsedCertificateInfo if valid
     * @throws Exception if PIN is wrong or file is corrupted
     */
    public ParsedCertificateInfo inspect(byte[] p12Bytes, String pin) throws Exception {
        KeyStore keystore = loadKeystore(p12Bytes, pin);
        String alias = findKeyAlias(keystore);
        Certificate[] chain = keystore.getCertificateChain(alias);
        if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate x509)) {
            throw new IllegalArgumentException("No valid X.509 certificate chain found in .p12 file");
        }

        LocalDateTime from = x509.getNotBefore() != null
                ? LocalDateTime.ofInstant(x509.getNotBefore().toInstant(), ZoneId.systemDefault())
                : null;
        LocalDateTime to = x509.getNotAfter() != null
                ? LocalDateTime.ofInstant(x509.getNotAfter().toInstant(), ZoneId.systemDefault())
                : null;

        String subject = x509.getSubjectX500Principal().getName();
        String issuer = x509.getIssuerX500Principal().getName();
        String serial = x509.getSerialNumber() != null ? x509.getSerialNumber().toString(16) : null;

        String commonName = extractCn(subject);

        return new ParsedCertificateInfo(subject, issuer, serial, from, to, commonName);
    }

    /**
     * Signs a PDF document with the private key and certificate chain from a .p12 file.
     * Uses incremental saving to preserve any previous digital signatures on the document.
     *
     * @param srcPdfBytes source PDF bytes
     * @param p12Bytes    raw bytes of the .p12 file
     * @param pin         password for the .p12 keystore
     * @param signerName  display name of the signer (defaults to certificate CN)
     * @param reason      signing reason (e.g. "อนุมัติคำขอกำหนดตำแหน่งทางวิชาการ")
     * @param location    signing location (e.g. "Khon Kaen University")
     * @return digitally signed PDF bytes
     */
    public byte[] signPdf(byte[] srcPdfBytes, byte[] p12Bytes, String pin,
                          String signerName, String reason, String location) throws Exception {
        return signPdf(srcPdfBytes, p12Bytes, pin, signerName, reason, location, null);
    }

    /**
     * Signs a PDF document using a PKCS#12 (.p12) certificate, creating an Adobe/PAdES-compatible
     * cryptographic digital signature with exact signing timestamp.
     *
     * @param srcPdfBytes source PDF bytes
     * @param p12Bytes    raw bytes of the .p12 file
     * @param pin         password for the .p12 keystore
     * @param signerName  display name of the signer (defaults to certificate CN)
     * @param reason      signing reason (e.g. "อนุมัติคำขอกำหนดตำแหน่งทางวิชาการ")
     * @param location    signing location (e.g. "Khon Kaen University")
     * @param signDate    exact timestamp when the signer confirmed their signature
     * @return digitally signed PDF bytes
     */
    public byte[] signPdf(byte[] srcPdfBytes, byte[] p12Bytes, String pin,
                          String signerName, String reason, String location,
                          java.time.LocalDateTime signDate) throws Exception {

        if (srcPdfBytes == null || srcPdfBytes.length == 0) {
            throw new IllegalArgumentException("Source PDF content is empty");
        }

        KeyStore keystore = loadKeystore(p12Bytes, pin);
        String alias = findKeyAlias(keystore);

        PrivateKey privateKey = (PrivateKey) keystore.getKey(alias, pin != null ? pin.toCharArray() : new char[0]);
        Certificate[] chain = keystore.getCertificateChain(alias);
        if (privateKey == null || chain == null || chain.length == 0) {
            throw new IllegalStateException("Could not retrieve private key or certificate chain from alias: " + alias);
        }

        X509Certificate signerCert = (X509Certificate) chain[0];
        if (signerCert.getNotAfter() != null && signerCert.getNotAfter().before(new java.util.Date())) {
            throw new IllegalStateException("ใบรับรองดิจิทัล (.p12) หมดอายุแล้ว ไม่สามารถใช้ลงนามเอกสารได้");
        }
        String resolvedSignerName = (signerName != null && !signerName.isBlank())
                ? signerName
                : extractCn(signerCert.getSubjectX500Principal().getName());

        try (PDDocument document = Loader.loadPDF(srcPdfBytes);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setName(resolvedSignerName);
            if (reason != null && !reason.isBlank()) {
                signature.setReason(reason);
            }
            if (location != null && !location.isBlank()) {
                signature.setLocation(location);
            }

            Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Bangkok"));
            if (signDate != null) {
                java.time.ZonedDateTime zdt = signDate.atZone(java.time.ZoneId.of("Asia/Bangkok"));
                cal.setTimeInMillis(zdt.toInstant().toEpochMilli());
            }
            signature.setSignDate(cal);

            // SignatureInterface creates the CMS / PKCS#7 container using BouncyCastle
            SignatureInterface signatureInterface = new SignatureInterface() {
                @Override
                public byte[] sign(InputStream content) throws IOException {
                    try {
                        List<Certificate> certList = Arrays.asList(chain);
                        Store certStore = new JcaCertStore(certList);

                        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
                        ContentSigner sha256Signer = new JcaContentSignerBuilder("SHA256withRSA")
                                .build(privateKey);

                        gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                                new JcaDigestCalculatorProviderBuilder().build())
                                .build(sha256Signer, signerCert));
                        gen.addCertificates(certStore);

                        CMSProcessableByteArray msg = new CMSProcessableByteArray(content.readAllBytes());
                        CMSSignedData signedData = gen.generate(msg, false); // detached
                        return signedData.getEncoded();
                    } catch (Exception e) {
                        log.error("Failed to generate CMS signature: {}", e.getMessage(), e);
                        throw new IOException("Cryptographic signing failed: " + e.getMessage(), e);
                    }
                }
            };

            // Register signature and save incrementally to maintain PDF validity
            document.addSignature(signature, signatureInterface);
            document.saveIncremental(baos);

            log.info("Successfully applied PAdES digital signature for '{}' ({})",
                    resolvedSignerName, signerCert.getSubjectX500Principal().getName());

            return baos.toByteArray();
        }
    }

    private KeyStore loadKeystore(byte[] p12Bytes, String pin) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        char[] password = pin != null ? pin.toCharArray() : new char[0];
        try (ByteArrayInputStream bais = new ByteArrayInputStream(p12Bytes)) {
            ks.load(bais, password);
        }
        return ks;
    }

    private String findKeyAlias(KeyStore ks) throws Exception {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (ks.isKeyEntry(alias)) {
                return alias;
            }
        }
        if (ks.aliases().hasMoreElements()) {
            return ks.aliases().nextElement();
        }
        throw new IllegalArgumentException("No key entry found in keystore");
    }

    private String extractCn(String dn) {
        if (dn == null) return "Signer";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase().startsWith("CN=")) {
                return trimmed.substring(3).trim();
            }
        }
        return dn;
    }
}
