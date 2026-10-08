package com.ecom.academic.service.pdf;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

/**
 * ขอตราประทับเวลา (RFC 3161) จากหน่วยงานรับรองเวลา — PAdES baseline T
 *
 * <p>ตราประทับเวลาพิสูจน์ว่าลายมือชื่อมีอยู่แล้ว ณ เวลานั้น ซึ่งใบรับรองของผู้ลงนามยังใช้ได้ โปรแกรมอ่าน PDF
 * จึงตรวจลายมือชื่อผ่านได้แม้ใบรับรองหมดอายุไปแล้ว (ใบรับรองของ มข. เปลี่ยนทุก 6 เดือน) ส่งออกไปแค่ค่า hash
 * ของลายมือชื่อ ไม่ใช่ตัวเอกสาร
 */
public final class TimestampClient {

    /** ติดต่อหน่วยงานรับรองเวลาไม่ได้ หรือได้คำตอบที่ใช้ไม่ได้ — ผู้ลงนามลองใหม่ได้ */
    public static final class TimestampUnavailableException extends IOException {
        public TimestampUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final URI url;
    private final String authorization;
    private final ASN1ObjectIdentifier policy;
    private final Duration timeout;
    private final HttpClient http;
    private final SecureRandom random = new SecureRandom();

    /**
     * @param url      ที่อยู่บริการ เช่น http://tsa.example/tsr
     * @param username ว่างเมื่อไม่ต้องยืนยันตัวตน
     * @param policy   OID นโยบายที่ขอ ว่างเมื่อใช้นโยบายตั้งต้นของบริการ
     */
    public TimestampClient(String url, String username, String password, String policy, Duration timeout) {
        this.url = URI.create(url.strip());
        this.authorization = username == null || username.isBlank() ? null
                : "Basic " + Base64.getEncoder().encodeToString(
                        (username + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
        this.policy = policy == null || policy.isBlank() ? null : new ASN1ObjectIdentifier(policy.strip());
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public URI url() {
        return url;
    }

    /**
     * ตราประทับเวลาของข้อมูลนี้ (SHA-256)
     *
     * @return TimeStampToken แบบ DER — ใส่เป็น unsigned attribute signatureTimeStampToken ของลายมือชื่อ
     */
    public byte[] stamp(byte[] data) throws TimestampUnavailableException {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true); // ใบรับรองของหน่วยงานรับรองเวลาอยู่ในตราประทับ — ใช้ตรวจและฝังสถานะได้
        if (policy != null) {
            generator.setReqPolicy(policy);
        }
        BigInteger nonce = new BigInteger(64, random);
        TimeStampRequest request = generator.generate(NISTObjectIdentifiers.id_sha256, digest, nonce);
        try {
            HttpRequest.Builder post = HttpRequest.newBuilder(url)
                    .timeout(timeout)
                    .header("Content-Type", "application/timestamp-query")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()));
            if (authorization != null) {
                post.header("Authorization", authorization);
            }
            HttpResponse<byte[]> reply = http.send(post.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (reply.statusCode() != 200) {
                throw new TimestampUnavailableException("TSA " + url + " answered HTTP " + reply.statusCode(), null);
            }
            TimeStampResponse response = new TimeStampResponse(reply.body());
            // ตรงกับที่ขอ: ค่า hash, nonce และนโยบาย — คำตอบของคำขออื่นใช้ไม่ได้
            response.validate(request);
            TimeStampToken token = response.getTimeStampToken();
            if (token == null) {
                throw new TimestampUnavailableException("TSA " + url + " refused: " + response.getStatusString(), null);
            }
            return token.getEncoded();
        } catch (TimestampUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimestampUnavailableException("Interrupted while waiting for TSA " + url, e);
        } catch (IOException | TSPException | RuntimeException e) {
            throw new TimestampUnavailableException("Could not get a timestamp from " + url + ": " + e.getMessage(), e);
        }
    }
}
