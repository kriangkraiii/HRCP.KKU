package com.ecom.academic.service.pdf;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ลายมือชื่อที่ตรวจสอบได้ระยะยาว (PAdES baseline LT) — ตราประทับเวลา + ข้อมูลสถานะใบรับรองในไฟล์
 *
 * <p>ใบรับรอง Digital ID ของ มข. เปลี่ยนทุก 6 เดือน ลายมือชื่อที่มีแค่เวลาจากเซิร์ฟเวอร์เราจะตรวจไม่ผ่าน
 * หลังใบรับรองหมดอายุ ตราประทับเวลาจากหน่วยงานรับรองเวลาพิสูจน์ว่าลงนามตอนใบรับรองยังใช้ได้ และสถานะ
 * ใบรับรองที่ฝังไว้ทำให้ตรวจได้โดยไม่ต้องถามผู้ออกใบรับรองอีก
 *
 * <p>ตั้งค่าใน application.properties — ไม่ตั้ง app.esign.tsa.url คือปิด (ทำงานแบบเดิม):
 * <pre>
 * app.esign.tsa.url=https://tsa.example/tsr        # บริการตราประทับเวลา RFC 3161
 * app.esign.tsa.username= / app.esign.tsa.password= # ถ้าบริการต้องยืนยันตัวตน
 * app.esign.tsa.policy-oid=                         # ถ้าบริการกำหนดนโยบาย
 * app.esign.tsa.timeout-ms=15000
 * app.esign.tsa.required=true                       # ติดต่อไม่ได้ = ไม่ให้ลงนาม
 * app.esign.ltv.enabled=true                        # ฝังสถานะใบรับรอง (OCSP/CRL)
 * app.esign.ltv.timeout-ms=15000
 * app.esign.ltv.required=false                      # ดึงสถานะไม่ได้ = ลงนามต่อ แค่บันทึกไว้
 * </pre>
 */
@Component
public class LongTermValidation {

    private static final Logger log = LoggerFactory.getLogger(LongTermValidation.class);

    /** ดึงข้อมูลสถานะใบรับรองไม่ได้ และตั้งไว้ว่าต้องมี — ผู้ลงนามลองใหม่ได้ */
    public static final class ValidationDataUnavailableException extends IOException {
        public ValidationDataUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final TimestampClient timestamps;
    private final boolean timestampRequired;
    private final ValidationDataService validationData;
    private final boolean validationDataRequired;

    public LongTermValidation(
            @Value("${app.esign.tsa.url:}") String tsaUrl,
            @Value("${app.esign.tsa.username:}") String tsaUsername,
            @Value("${app.esign.tsa.password:}") String tsaPassword,
            @Value("${app.esign.tsa.policy-oid:}") String tsaPolicy,
            @Value("${app.esign.tsa.timeout-ms:15000}") long tsaTimeoutMs,
            @Value("${app.esign.tsa.required:true}") boolean tsaRequired,
            @Value("${app.esign.ltv.enabled:true}") boolean ltvEnabled,
            @Value("${app.esign.ltv.timeout-ms:15000}") long ltvTimeoutMs,
            @Value("${app.esign.ltv.required:false}") boolean ltvRequired) {
        this.timestamps = tsaUrl == null || tsaUrl.isBlank() ? null
                : new TimestampClient(tsaUrl, tsaUsername, tsaPassword, tsaPolicy, Duration.ofMillis(tsaTimeoutMs));
        this.timestampRequired = tsaRequired;
        // สถานะใบรับรองมีประโยชน์เมื่อมีตราประทับเวลาด้วยเท่านั้น — ไม่มีเวลาที่เชื่อถือได้ ก็ไม่รู้ว่าสถานะนั้นของเมื่อไร
        this.validationData = ltvEnabled && timestamps != null ? new ValidationDataService(Duration.ofMillis(ltvTimeoutMs)) : null;
        this.validationDataRequired = ltvRequired;
        if (timestamps != null) {
            log.info("Signatures are timestamped by {} (required={}); validation data {}", timestamps.url(),
                    timestampRequired, validationData != null ? "embedded (required=" + ltvRequired + ")" : "off");
        } else {
            log.info("No timestamp authority configured (app.esign.tsa.url) — signatures are PAdES baseline B");
        }
    }

    /** เปิดใช้อยู่หรือไม่ — ไม่ได้ตั้งบริการตราประทับเวลา คือทำงานแบบเดิม */
    public boolean enabled() {
        return timestamps != null;
    }

    /** ให้ลายมือชื่อจากกุญแจนี้มีตราประทับเวลา (เมื่อเปิดใช้) */
    public CmsSigner prepare(CmsSigner signer) {
        return timestamps == null ? signer : signer.timestampWith(timestamps, timestampRequired);
    }

    /**
     * ไฟล์หลังเพิ่มข้อมูลสถานะใบรับรองของลายมือชื่อล่าสุด
     *
     * @return ไฟล์ใหม่ หรือ null เมื่อไม่มีอะไรเพิ่ม (ปิดอยู่ ใบรับรองฝังไว้หมดแล้ว หรือดึงไม่ได้และไม่บังคับ)
     * @throws ValidationDataUnavailableException ดึงไม่ได้ และตั้งไว้ว่าต้องมี
     */
    public byte[] validationDataFor(byte[] signedPdf) throws ValidationDataUnavailableException {
        if (validationData == null) {
            return null;
        }
        try {
            ValidationDataService.Result result = validationData.addForLatestSignature(signedPdf);
            if (validationDataRequired && result.added().withoutStatus() > 0) {
                throw new ValidationDataUnavailableException(result.added().withoutStatus()
                        + " certificate(s) without revocation data", null);
            }
            return result.added().nothing() ? null : result.pdf();
        } catch (ValidationDataUnavailableException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            if (validationDataRequired) {
                throw new ValidationDataUnavailableException("Could not add validation data: " + e.getMessage(), e);
            }
            log.warn("Validation data not added (the signature stands, timestamped): {}", e.toString());
            return null;
        }
    }
}
