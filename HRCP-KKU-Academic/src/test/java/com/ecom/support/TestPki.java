package com.ecom.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * PKI จำลองสำหรับเทสต์ลายมือชื่อระยะยาว: CA ราก, ใบรับรองผู้ลงนามที่ชี้ไปบริการตรวจสถานะ (OCSP), CRL
 * และใบรับรองผู้ออก (caIssuers) ของเซิร์ฟเวอร์จำลอง, และหน่วยงานรับรองเวลา (TSA) — ทั้งหมดบน localhost
 *
 * <p>ไฟล์ .p12 ของผู้ลงนามมีแต่ใบรับรองของตัวเอง ไม่มีสายถึงราก เหมือนไฟล์จริงบางแบบ — ผู้ตรวจต้องไปดาวน์โหลดราก
 */
public final class TestPki implements AutoCloseable {

    public static final String PIN = "Test-P12-Pin!";
    public static final ASN1ObjectIdentifier TSA_POLICY = new ASN1ObjectIdentifier("1.2.3.4.5");

    private final HttpServer server;
    private final String base;
    private final KeyPair rootKey;
    private final X509Certificate root;
    private final KeyPair signerKey;
    private final X509Certificate signer;
    private final KeyPair tsaKey;
    private final X509Certificate tsa;
    private final AtomicInteger serial = new AtomicInteger(100);

    /** ปิดบริการตราประทับเวลาชั่วคราว (ตอบ 503) */
    public final AtomicBoolean tsaDown = new AtomicBoolean(false);
    public final AtomicInteger tsaCalls = new AtomicInteger();
    public final AtomicInteger ocspCalls = new AtomicInteger();

    public TestPki() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        rootKey = rsa();
        root = certificate(new X500Name("CN=HRCP Test Root CA"), rootKey, new X500Name("CN=HRCP Test Root CA"),
                rootKey, true, null, false);
        signerKey = rsa();
        signer = certificate(new X500Name("CN=Test Signer"), signerKey, new X500Name("CN=HRCP Test Root CA"),
                rootKey, false, null, true);
        tsaKey = rsa();
        tsa = certificate(new X500Name("CN=HRCP Test TSA"), tsaKey, new X500Name("CN=HRCP Test Root CA"),
                rootKey, false, KeyPurposeId.id_kp_timeStamping, false);

        server.createContext("/tsa", this::timestamp);
        server.createContext("/ocsp", this::ocsp);
        server.createContext("/crl", this::crl);
        byte[] rootDer = root.getEncoded();
        server.createContext("/ca.cer", exchange -> reply(exchange, 200, "application/pkix-cert", rootDer));
        server.start();
    }

    public String tsaUrl() {
        return base + "/tsa";
    }

    public X509Certificate root() {
        return root;
    }

    public X509Certificate tsaCertificate() {
        return tsa;
    }

    /** .p12 ของผู้ลงนาม — ใบรับรองของตัวเองอย่างเดียว */
    public byte[] signerP12() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("signer", signerKey.getPrivate(), PIN.toCharArray(), new Certificate[] { signer });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ks.store(out, PIN.toCharArray());
        return out.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------------ certificates

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    /**
     * @param status   ใส่ OCSP + caIssuers (ผู้ลงนาม) — ไม่ใส่ก็มีแต่ CRL (TSA)
     */
    private X509Certificate certificate(X500Name subject, KeyPair key, X500Name issuer, KeyPair issuerKey,
            boolean ca, KeyPurposeId purpose, boolean status) throws Exception {
        Date from = new Date(System.currentTimeMillis() - 60_000L);
        Date to = new Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000);
        X509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(serial.incrementAndGet()),
                from, to, subject, key.getPublic());
        b.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        if (ca) {
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            b.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            if (purpose != null) {
                b.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(purpose));
            }
            if (status) {
                b.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(new AccessDescription[] {
                        new AccessDescription(X509ObjectIdentifiers.id_ad_ocsp,
                                new GeneralName(GeneralName.uniformResourceIdentifier, base + "/ocsp")),
                        new AccessDescription(X509ObjectIdentifiers.id_ad_caIssuers,
                                new GeneralName(GeneralName.uniformResourceIdentifier, base + "/ca.cer")) }));
            } else {
                b.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[] {
                        new DistributionPoint(new DistributionPointName(new GeneralNames(
                                new GeneralName(GeneralName.uniformResourceIdentifier, base + "/crl"))), null, null) }));
            }
        }
        ContentSigner cs = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(b.build(cs));
    }

    // ------------------------------------------------------------------ services

    private void timestamp(HttpExchange exchange) throws IOException {
        tsaCalls.incrementAndGet();
        if (tsaDown.get()) {
            reply(exchange, 503, "text/plain", new byte[0]);
            return;
        }
        try {
            TimeStampRequest request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
            TimeStampTokenGenerator generator = new TimeStampTokenGenerator(
                    new JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withRSA", tsaKey.getPrivate(), tsa),
                    new JcaDigestCalculatorProviderBuilder().build().get(
                            new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                    org.bouncycastle.asn1.oiw.OIWObjectIdentifiers.idSHA1)),
                    TSA_POLICY);
            generator.addCertificates(new org.bouncycastle.cert.jcajce.JcaCertStore(java.util.List.of(tsa)));
            byte[] body = new TimeStampResponseGenerator(generator, TSPAlgorithms.ALLOWED)
                    .generate(request, BigInteger.valueOf(serial.incrementAndGet()), new Date()).getEncoded();
            reply(exchange, 200, "application/timestamp-reply", body);
        } catch (Exception e) {
            reply(exchange, 500, "text/plain", e.toString().getBytes());
        }
    }

    private void ocsp(HttpExchange exchange) throws IOException {
        ocspCalls.incrementAndGet();
        try {
            OCSPReq request = new OCSPReq(exchange.getRequestBody().readAllBytes());
            X509CertificateHolder rootHolder = new JcaX509CertificateHolder(root);
            BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(new RespID(rootHolder.getSubject()));
            for (Req req : request.getRequestList()) {
                builder.addResponse(req.getCertID(), CertificateStatus.GOOD, new Date(), (java.util.Date) null);
            }
            var basic = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKey.getPrivate()),
                    new X509CertificateHolder[] { rootHolder }, new Date());
            reply(exchange, 200, "application/ocsp-response",
                    new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).getEncoded());
        } catch (Exception e) {
            reply(exchange, 500, "text/plain", e.toString().getBytes());
        }
    }

    private void crl(HttpExchange exchange) throws IOException {
        try {
            X509v2CRLBuilder builder = new X509v2CRLBuilder(new JcaX509CertificateHolder(root).getSubject(), new Date());
            builder.setNextUpdate(new Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000));
            byte[] body = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(rootKey.getPrivate()))
                    .getEncoded();
            reply(exchange, 200, "application/pkix-crl", body);
        } catch (Exception e) {
            reply(exchange, 500, "text/plain", e.toString().getBytes());
        }
    }

    private static void reply(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
