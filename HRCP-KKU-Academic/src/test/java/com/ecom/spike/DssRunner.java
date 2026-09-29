package com.ecom.spike;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.PDFRevisionWrapper;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;

/**
 * SPIKE: EU DSS validation, run in its own JVM.
 *
 * <p>DSS 6.2 is built against BouncyCastle 1.80. With the project's 1.85 it cannot
 * read any padded /Contents ("Extra data detected in stream"), so the spike starts
 * this class with 1.80 swapped in. Args: pdf, then trusted DER certificates.
 */
public final class DssRunner {

    public static void main(String[] args) throws Exception {
        SignedDocumentValidator v = SignedDocumentValidator.fromDocument(new FileDocument(args[0]));
        CommonCertificateVerifier cv = new CommonCertificateVerifier();
        CommonTrustedCertificateSource ts = new CommonTrustedCertificateSource();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 1; i < args.length; i++) {
            ts.addCertificate(new CertificateToken((X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(Files.readAllBytes(Path.of(args[i]))))));
        }
        cv.setTrustedCertSources(ts);
        cv.setAIASource(null);
        cv.setOcspSource(null);
        cv.setCrlSource(null);
        v.setCertificateVerifier(cv);
        Reports reports = v.validateDocument();
        SimpleReport sr = reports.getSimpleReport();
        DiagnosticData dd = reports.getDiagnosticData();
        for (String id : sr.getSignatureIdList()) {
            SignatureWrapper sw = dd.getSignatureById(id);
            PDFRevisionWrapper rev = sw.getPDFRevision();
            System.out.printf("  %-18s %-18s %s | intact=%s valid=%s | docMDP=%s fieldMDP=%s | "
                    + "formFill=%d annot=%d undefined=%d extension=%d modified=%s%n",
                    rev.getSignatureFieldNames(), sr.getIndication(id), sr.getSubIndication(id),
                    sw.isSignatureIntact(), sw.isSignatureValid(), rev.getDocMDPPermissions(),
                    rev.getFieldMDP() != null ? rev.getFieldMDP().getAction() + "" + rev.getFieldMDP().getFields() : "-",
                    rev.getPdfSignatureOrFormFillChanges().size(), rev.getPdfAnnotationChanges().size(),
                    rev.getPdfUndefinedChanges().size(), rev.getPdfExtensionChanges().size(),
                    rev.getModifiedFieldNames());
            rev.getPdfAnnotationChanges().forEach(m -> System.out.println("      ANNOT " + m.getAction() + " " + m.getFieldName() + " " + m.getType() + " " + m.getValue()));
            rev.getPdfUndefinedChanges().forEach(m -> System.out.println("      UNDEF " + m.getAction() + " " + m.getFieldName() + " " + m.getType() + " " + m.getValue()));
            sr.getAdESValidationErrors(id).forEach(m -> System.out.println("      ERROR " + m.getValue()));
            sr.getAdESValidationWarnings(id).forEach(m -> System.out.println("      WARN  " + m.getValue()));
        }
    }
}
