package com.ecom.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureRequestStatus;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;

import jakarta.annotation.PostConstruct;

/**
 * Seals each signature so tampering with the evidence itself is detectable.
 *
 * <p>The audit rows already say who signed what and when. This adds a keyed
 * seal over those fields: anyone who can reach the database can edit a
 * timestamp or a signer id, but without the server key they cannot produce a
 * matching HMAC, so the edit shows up as a broken seal on the verification page.
 *
 * <p>Supports the "reliable method" limb of พ.ร.บ.ว่าด้วยธุรกรรมทาง
 * อิเล็กทรอนิกส์ ม.9 — not a PKI digital signature under ม.26, which would need
 * a certificate authority this system does not have.
 */
@Service
public class SignatureVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SignatureVerificationService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Server key for sealing. Supplied by environment, never committed —
     * {@code application.properties} in this project already carries secrets it
     * should not, and this one must not join them.
     */
    @Value("${app.esign.hmac-secret:}")
    private String hmacSecret;

    @PostConstruct
    void warnIfUnconfigured() {
        if (!isConfigured()) {
            // Deliberately loud, and deliberately not a hardcoded fallback: a
            // default key would be in the source tree, which is the same as no
            // key while looking like protection.
            log.warn("app.esign.hmac-secret is not set — electronic signatures will be recorded "
                    + "WITHOUT a tamper-evident seal. Set APP_ESIGN_HMAC_SECRET in the environment "
                    + "before using signatures for anything official.");
        }
    }

    public boolean isConfigured() {
        return hmacSecret != null && !hmacSecret.isBlank();
    }

    /**
     * Computes the seal for a completed signing step.
     *
     * @return hex-encoded HMAC, or null when no key is configured
     */
    public String seal(SignatureStep step) {
        if (!isConfigured()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payloadOf(step).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("Failed to seal signature step {}: {}", step.getId(), e.toString());
            return null;
        }
    }

    /** True when the stored seal still matches the step's current values. */
    public boolean sealIntact(SignatureStep step) {
        if (!isConfigured() || step.getEvidenceHmac() == null) {
            return false;
        }
        String expected = seal(step);
        if (expected == null) {
            return false;
        }
        // Constant-time compare: a seal check is a place where timing leaks.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                step.getEvidenceHmac().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The exact fields the seal covers.
     *
     * <p>Order and separator are fixed forever: changing either invalidates
     * every seal already stored.
     */
    private String payloadOf(SignatureStep step) {
        return String.join("|",
                String.valueOf(step.getId()),
                String.valueOf(step.getSignatureRequest().getId()),
                nullSafe(step.getSlotKey()),
                String.valueOf(step.getSigner() == null ? "" : step.getSigner().getId()),
                nullSafe(step.getSignerNameSnapshot()),
                nullSafe(step.getDocHashSigned()),
                step.getSignedAt() == null ? "" : step.getSignedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).format(STAMP),
                nullSafe(step.getConsentTextVersion()),
                nullSafe(step.getImagePathSnapshot()));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    // =====================================================================
    // Verification report
    // =====================================================================

    /** One signer's line on the verification page. */
    public record SignerLine(
            int order,
            String roleLabel,
            String signerName,
            LocalDateTime signedAt,
            boolean sealIntact,
            boolean sealPresent) {
    }

    /**
     * What the verification page shows.
     *
     * @param contentIntact whether the frozen document still hashes to its
     *                      recorded value
     * @param allSealsIntact whether every signature's seal still verifies
     * @param sealingEnabled whether the server has a key at all; when false the
     *                       page must say so rather than imply the seals passed
     */
    public record VerificationReport(
            SignatureRequest envelope,
            List<SignerLine> signers,
            boolean contentIntact,
            boolean allSealsIntact,
            boolean sealingEnabled) {

        /** True only when nothing at all is in question. */
        public boolean fullyValid() {
            return envelope.getStatus() == SignatureRequestStatus.COMPLETED
                    && contentIntact && allSealsIntact && sealingEnabled;
        }

        public String headlineThai() {
            if (envelope.getStatus() == SignatureRequestStatus.VOIDED) {
                return "เอกสารถูกแก้ไขหลังลงนาม — ลายเซ็นเป็นโมฆะ";
            }
            if (!contentIntact) {
                return "เนื้อหาเอกสารไม่ตรงกับที่บันทึกไว้ตอนลงนาม";
            }
            if (sealingEnabled && !allSealsIntact) {
                return "พบความผิดปกติของหลักฐานการลงนาม";
            }
            if (envelope.getStatus() != SignatureRequestStatus.COMPLETED) {
                return "เอกสารนี้ยังลงนามไม่ครบทุกขั้นตอน";
            }
            return sealingEnabled
                    ? "เอกสารนี้ลงนามครบถ้วนและไม่ถูกแก้ไข"
                    : "ลงนามครบถ้วน — แต่ระบบยังไม่ได้ตั้งค่าการผนึกหลักฐาน";
        }
    }

    /** Checks an envelope end to end and reports what holds and what does not. */
    public VerificationReport verify(SignatureRequest envelope) {
        boolean contentIntact = SignatureWorkflowService.sha256(envelope.getFrozenJson())
                .equals(envelope.getFrozenHash());

        List<SignerLine> lines = new ArrayList<>();
        boolean allSealsIntact = true;

        for (SignatureStep step : envelope.getSteps()) {
            if (step.getStatus() != SignatureStepStatus.SIGNED) {
                continue;
            }
            boolean present = step.getEvidenceHmac() != null;
            boolean intact = present && sealIntact(step);
            if (present && !intact) {
                allSealsIntact = false;
            }
            lines.add(new SignerLine(
                    step.getStepOrder(),
                    step.getRoleLabel(),
                    step.getPrintedSignerName(),
                    step.getSignedAt(),
                    intact,
                    present));
        }

        return new VerificationReport(envelope, lines, contentIntact, allSealsIntact, isConfigured());
    }
}
