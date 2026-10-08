package com.ecom.academic.service.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ฝังข้อมูลสำหรับตรวจลายมือชื่อระยะยาว (PAdES baseline LT) ลงในไฟล์ — Document Security Store (DSS)
 *
 * <p>หลังลงนามแต่ละครั้ง เก็บสายใบรับรองของผู้ลงนามและของหน่วยงานรับรองเวลา พร้อมสถานะ ณ ตอนนั้น
 * (OCSP หรือ CRL) ไว้ในไฟล์เป็น revision ต่อท้าย โปรแกรมอ่าน PDF ตรวจได้โดยไม่ต้องถามผู้ออกใบรับรองอีก
 * แม้ใบรับรองหมดอายุ หรือบริการตรวจสถานะของผู้ออกใบรับรองปิดไปแล้ว
 *
 * <p>การเพิ่ม DSS ไม่ทำให้ลายมือชื่อเดิมเสีย — DocMDP P=2 และ FieldMDP อนุญาต (ISO 32000-2 §12.8.4.3)
 */
public final class ValidationDataService {

    private static final Logger log = LoggerFactory.getLogger(ValidationDataService.class);
    private static final COSName DSS = COSName.getPDFName("DSS");
    private static final COSName VRI = COSName.getPDFName("VRI");
    private static final COSName CERTS = COSName.getPDFName("Certs");
    private static final COSName OCSPS = COSName.getPDFName("OCSPs");
    private static final COSName CRLS = COSName.getPDFName("CRLs");
    private static final COSName CERT = COSName.getPDFName("Cert");
    private static final COSName OCSP = COSName.getPDFName("OCSP");
    private static final COSName CRL = COSName.getPDFName("CRL");
    /** สายใบรับรองยาวกว่านี้ไม่มีในทางปฏิบัติ — กันวนไม่รู้จบเมื่อ AIA ชี้กันไปมา */
    private static final int MAX_CHAIN = 8;

    /**
     * สิ่งที่ฝังลงไฟล์รอบนี้ — ใช้ในเทสต์และบันทึก
     *
     * @param withoutStatus ใบรับรอง (ที่ไม่ใช่ราก) ที่หาสถานะไม่ได้ ไม่ว่าจะฝังไว้ก่อนแล้วหรือไม่
     */
    public record Added(int certificates, int ocspResponses, int crls, int withoutStatus) {
        public boolean nothing() {
            return certificates == 0 && ocspResponses == 0 && crls == 0;
        }
    }

    /** ผลของการเพิ่ม: ไฟล์ใหม่ (หรือไฟล์เดิมเมื่อไม่มีอะไรเพิ่ม) และสิ่งที่เพิ่ม */
    public record Result(byte[] pdf, Added added) {
    }

    private final HttpClient http;
    private final Duration timeout;

    public ValidationDataService(Duration timeout) {
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * ข้อมูลตรวจสอบของลายมือชื่อล่าสุดในไฟล์
     *
     * @throws IOException ไฟล์อ่านไม่ได้ — การติดต่อผู้ออกใบรับรองไม่สำเร็จไม่ถือว่าผิดพลาด ฝังเท่าที่ได้
     */
    public Result addForLatestSignature(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(pdf))) {
            PDSignature signature = latestSignature(doc);
            if (signature == null) {
                return new Result(pdf, new Added(0, 0, 0, 0));
            }
            byte[] contents = signature.getContents();
            CMSSignedData cms = new CMSSignedData(stripPadding(contents));

            Map<String, X509CertificateHolder> certs = new LinkedHashMap<>();
            for (X509CertificateHolder c : cms.getCertificates().getMatches(null)) {
                certs.putIfAbsent(key(c.getEncoded()), c);
            }
            for (X509CertificateHolder c : timestampCertificates(cms)) {
                certs.putIfAbsent(key(c.getEncoded()), c);
            }
            completeChains(certs);

            List<byte[]> ocsps = new ArrayList<>();
            List<byte[]> crls = new ArrayList<>();
            int withoutStatus = 0;
            for (X509CertificateHolder cert : new ArrayList<>(certs.values())) {
                if (selfSigned(cert)) {
                    continue; // ราก — ความเชื่อถือมาจากรายการรากที่เชื่อถือของโปรแกรมตรวจ ไม่ใช่การตรวจสถานะ
                }
                X509CertificateHolder issuer = issuerOf(cert, certs.values());
                if (issuer == null) {
                    log.warn("No issuer for {} — its status cannot be embedded", cert.getSubject());
                    withoutStatus++;
                    continue;
                }
                byte[] ocsp = ocsp(cert, issuer, certs);
                if (ocsp != null) {
                    ocsps.add(ocsp);
                    continue;
                }
                byte[] crl = crl(cert);
                if (crl != null) {
                    crls.add(crl);
                } else {
                    withoutStatus++;
                }
            }
            return write(doc, pdf, contents, new ArrayList<>(certs.values()), ocsps, crls, withoutStatus);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not add validation data: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ reading

    private static PDSignature latestSignature(PDDocument doc) throws IOException {
        PDSignature latest = null;
        long end = -1;
        for (PDSignature s : doc.getSignatureDictionaries()) {
            int[] range = s.getByteRange();
            if (range == null || range.length < 4) {
                continue;
            }
            long last = (long) range[2] + range[3];
            if (last > end) {
                end = last;
                latest = s;
            }
        }
        return latest;
    }

    /** /Contents เผื่อที่ด้วยศูนย์ต่อท้าย — อ่านเฉพาะ CMS ตัวจริง */
    private static byte[] stripPadding(byte[] contents) throws IOException {
        try (ASN1InputStream in = new ASN1InputStream(contents)) {
            return in.readObject().getEncoded();
        }
    }

    private static List<X509CertificateHolder> timestampCertificates(CMSSignedData cms) {
        List<X509CertificateHolder> out = new ArrayList<>();
        for (SignerInformation signer : cms.getSignerInfos().getSigners()) {
            if (signer.getUnsignedAttributes() == null) {
                continue;
            }
            Attribute attribute = signer.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            if (attribute == null) {
                continue;
            }
            try {
                TimeStampToken token = new TimeStampToken(new CMSSignedData(
                        attribute.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
                out.addAll(token.getCertificates().getMatches(null));
            } catch (Exception e) {
                log.warn("Unreadable timestamp token: {}", e.toString());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ chain

    /** ใบรับรองผู้ออกที่ไม่ได้มากับลายมือชื่อ — ดาวน์โหลดจาก AIA caIssuers */
    private void completeChains(Map<String, X509CertificateHolder> certs) {
        for (int round = 0; round < MAX_CHAIN; round++) {
            boolean grew = false;
            for (X509CertificateHolder cert : new ArrayList<>(certs.values())) {
                if (selfSigned(cert) || issuerOf(cert, certs.values()) != null) {
                    continue;
                }
                for (String url : accessLocations(cert, X509ObjectIdentifiers.id_ad_caIssuers)) {
                    for (X509CertificateHolder issuer : download(url)) {
                        grew |= certs.putIfAbsent(keyQuietly(issuer), issuer) == null;
                    }
                }
            }
            if (!grew) {
                return;
            }
        }
    }

    private List<X509CertificateHolder> download(String url) {
        byte[] body = get(url);
        if (body == null) {
            return List.of();
        }
        try {
            return List.of(new X509CertificateHolder(body));
        } catch (Exception notDer) {
            try {
                // .p7c — ชุดใบรับรองแบบ PKCS#7
                return new ArrayList<>(new CMSSignedData(body).getCertificates().getMatches(null));
            } catch (Exception e) {
                log.warn("Could not read issuer certificate from {}: {}", url, e.toString());
                return List.of();
            }
        }
    }

    private static boolean selfSigned(X509CertificateHolder cert) {
        return cert.getSubject().equals(cert.getIssuer());
    }

    private static X509CertificateHolder issuerOf(X509CertificateHolder cert, Collection<X509CertificateHolder> pool) {
        for (X509CertificateHolder candidate : pool) {
            if (candidate != cert && candidate.getSubject().equals(cert.getIssuer())) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ status

    /** คำตอบ OCSP ทั้งก้อน (OCSPResponse) หรือ null เมื่อไม่มีบริการหรือถามไม่ได้ */
    private byte[] ocsp(X509CertificateHolder cert, X509CertificateHolder issuer,
            Map<String, X509CertificateHolder> certs) {
        for (String url : accessLocations(cert, X509ObjectIdentifiers.id_ad_ocsp)) {
            try {
                CertificateID id = new CertificateID(new JcaDigestCalculatorProviderBuilder().build()
                        .get(CertificateID.HASH_SHA1), issuer, cert.getSerialNumber());
                byte[] request = new OCSPReqBuilder().addRequest(id).build().getEncoded();
                byte[] body = post(url, "application/ocsp-request", request);
                if (body == null) {
                    continue;
                }
                OCSPResp response = new OCSPResp(body);
                if (response.getStatus() != OCSPResp.SUCCESSFUL) {
                    log.warn("OCSP {} answered status {} for {}", url, response.getStatus(), cert.getSubject());
                    continue;
                }
                BasicOCSPResp basic = (BasicOCSPResp) response.getResponseObject();
                for (SingleResp single : basic.getResponses()) {
                    if (single.getCertStatus() instanceof RevokedStatus revoked) {
                        log.error("Certificate {} was revoked at {} — embedding the revocation as found",
                                cert.getSubject(), revoked.getRevocationTime());
                    }
                }
                // ใบรับรองของผู้ตอบ OCSP — ผู้ตรวจต้องใช้ยืนยันคำตอบนี้
                for (X509CertificateHolder responder : basic.getCerts()) {
                    certs.putIfAbsent(keyQuietly(responder), responder);
                }
                return response.getEncoded();
            } catch (Exception e) {
                log.warn("OCSP {} failed for {}: {}", url, cert.getSubject(), e.toString());
            }
        }
        return null;
    }

    /** CRL ล่าสุดจากจุดแจกจ่ายแบบ http หรือ null */
    private byte[] crl(X509CertificateHolder cert) {
        CRLDistPoint points = CRLDistPoint.fromExtensions(cert.getExtensions());
        if (points == null) {
            return null;
        }
        for (DistributionPoint point : points.getDistributionPoints()) {
            DistributionPointName name = point.getDistributionPoint();
            if (name == null || name.getType() != DistributionPointName.FULL_NAME) {
                continue;
            }
            for (GeneralName general : GeneralNames.getInstance(name.getName()).getNames()) {
                if (general.getTagNo() != GeneralName.uniformResourceIdentifier) {
                    continue;
                }
                String url = general.getName().toString();
                if (!url.startsWith("http")) {
                    continue; // ldap:// — ไม่รองรับ
                }
                byte[] body = get(url);
                if (body == null) {
                    continue;
                }
                try {
                    new X509CRLHolder(body); // ต้องอ่านได้จริงก่อนฝัง
                    return body;
                } catch (Exception e) {
                    log.warn("Unreadable CRL from {}: {}", url, e.toString());
                }
            }
        }
        return null;
    }

    private static List<String> accessLocations(X509CertificateHolder cert, ASN1ObjectIdentifier method) {
        AuthorityInformationAccess aia = AuthorityInformationAccess.fromExtensions(cert.getExtensions());
        if (aia == null) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (AccessDescription d : aia.getAccessDescriptions()) {
            GeneralName location = d.getAccessLocation();
            if (d.getAccessMethod().equals(method) && location.getTagNo() == GeneralName.uniformResourceIdentifier) {
                String url = location.getName().toString();
                if (url.startsWith("http")) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private byte[] get(String url) {
        try {
            HttpResponse<byte[]> reply = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (reply.statusCode() == 200) {
                return reply.body();
            }
            log.warn("GET {} answered HTTP {}", url, reply.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("GET {} failed: {}", url, e.toString());
        }
        return null;
    }

    private byte[] post(String url, String contentType, byte[] body) {
        try {
            HttpResponse<byte[]> reply = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (reply.statusCode() == 200) {
                return reply.body();
            }
            log.warn("POST {} answered HTTP {}", url, reply.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("POST {} failed: {}", url, e.toString());
        }
        return null;
    }

    // ------------------------------------------------------------------ writing

    private Result write(PDDocument doc, byte[] original, byte[] contents, List<X509CertificateHolder> certs,
            List<byte[]> ocsps, List<byte[]> crls, int withoutStatus) throws IOException {
        COSDictionary catalog = doc.getDocumentCatalog().getCOSObject();
        COSDictionary dss = catalog.getCOSDictionary(DSS);
        boolean newDss = dss == null;
        if (newDss) {
            dss = new COSDictionary();
        }
        COSArray certArray = array(dss, CERTS);
        COSArray ocspArray = array(dss, OCSPS);
        COSArray crlArray = array(dss, CRLS);

        // VRI ของลายมือชื่อนี้ — กุญแจคือ SHA-1 ของ /Contents ตัวพิมพ์ใหญ่ (ETSI EN 319 142-1 §5.4.2.2)
        COSDictionary vri = new COSDictionary();
        COSArray vriCerts = new COSArray();
        COSArray vriOcsps = new COSArray();
        COSArray vriCrls = new COSArray();

        java.util.Set<COSStream> created = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int addedCerts = 0;
        for (X509CertificateHolder cert : certs) {
            COSStream stream = findOrAdd(doc, certArray, cert.getEncoded(), created);
            vriCerts.add(stream);
        }
        addedCerts = created.size();
        for (byte[] ocsp : ocsps) {
            vriOcsps.add(findOrAdd(doc, ocspArray, ocsp, created));
        }
        int addedOcsps = created.size() - addedCerts;
        for (byte[] crl : crls) {
            vriCrls.add(findOrAdd(doc, crlArray, crl, created));
        }
        int addedCrls = created.size() - addedCerts - addedOcsps;
        Added added = new Added(addedCerts, addedOcsps, addedCrls, withoutStatus);
        if (added.nothing()) {
            return new Result(original, added);
        }

        if (vriCerts.size() > 0) {
            vri.setItem(CERT, vriCerts);
        }
        if (vriOcsps.size() > 0) {
            vri.setItem(OCSP, vriOcsps);
        }
        if (vriCrls.size() > 0) {
            vri.setItem(CRL, vriCrls);
        }
        COSDictionary vris = dss.getCOSDictionary(VRI);
        if (vris == null) {
            vris = new COSDictionary();
            dss.setItem(VRI, vris);
        }
        vris.setItem(COSName.getPDFName(sha1Upper(contents)), vri);

        putIfNotEmpty(dss, CERTS, certArray);
        putIfNotEmpty(dss, OCSPS, ocspArray);
        putIfNotEmpty(dss, CRLS, crlArray);
        for (COSBase updated : new COSBase[] { dss, vris, vri, certArray, ocspArray, crlArray, vriCerts, vriOcsps, vriCrls }) {
            if (updated instanceof COSDictionary d) {
                d.setNeedToBeUpdated(true);
            } else if (updated instanceof COSArray a) {
                a.setNeedToBeUpdated(true);
            }
        }
        if (newDss) {
            catalog.setItem(DSS, dss);
        }
        catalog.setNeedToBeUpdated(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.saveIncremental(out);
        byte[] next = out.toByteArray();
        if (next.length < original.length) {
            throw new IOException("Incremental save shrank the document");
        }
        log.info("Validation data added: {} certificates, {} OCSP responses, {} CRLs",
                addedCerts, addedOcsps, addedCrls);
        return new Result(next, added);
    }

    private static COSArray array(COSDictionary dss, COSName name) {
        COSArray existing = dss.getCOSArray(name);
        return existing != null ? existing : new COSArray();
    }

    private static void putIfNotEmpty(COSDictionary dss, COSName name, COSArray array) {
        if (array.size() > 0) {
            dss.setItem(name, array);
        }
    }

    /** สตรีมที่มีข้อมูลนี้อยู่แล้วในอาร์เรย์ หรือสร้างใหม่ — ใบรับรองเดียวกันไม่ฝังซ้ำทุกลายเซ็น */
    private static COSStream findOrAdd(PDDocument doc, COSArray array, byte[] data,
            java.util.Set<COSStream> created) throws IOException {
        for (int i = 0; i < array.size(); i++) {
            COSBase item = array.get(i);
            COSBase target = item instanceof COSObject o ? o.getObject() : item;
            if (target instanceof COSStream stream) {
                try (var in = stream.createInputStream()) {
                    if (java.util.Arrays.equals(in.readAllBytes(), data)) {
                        return stream;
                    }
                }
            }
        }
        COSStream stream = doc.getDocument().createCOSStream();
        try (OutputStream out = stream.createOutputStream()) {
            out.write(data);
        }
        stream.setNeedToBeUpdated(true);
        array.add(stream);
        created.add(stream);
        return stream;
    }

    private static String key(byte[] der) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(der));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String keyQuietly(X509CertificateHolder cert) {
        try {
            return key(cert.getEncoded());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha1Upper(byte[] data) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
