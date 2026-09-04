package com.ecom.academic.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;
import com.ecom.model.UserDtls;

/**
 * Manages user digital certificates (.p12) and applies cryptographic signatures
 * to workflow documents without requiring any ugly table stamps or .fdf templates.
 */
@Service
public class UserDigitalCertificateService {

    private static final Logger log = LoggerFactory.getLogger(UserDigitalCertificateService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UserDigitalCertificateRepository certificateRepository;
    private final DigitalCertificateStorage storage;
    private final CertificatePinEncryptionService pinEncryptionService;
    private final PdfDigitalSignatureService pdfSignatureService;

    public UserDigitalCertificateService(
            UserDigitalCertificateRepository certificateRepository,
            DigitalCertificateStorage storage,
            CertificatePinEncryptionService pinEncryptionService,
            PdfDigitalSignatureService pdfSignatureService) {
        this.certificateRepository = certificateRepository;
        this.storage = storage;
        this.pinEncryptionService = pinEncryptionService;
        this.pdfSignatureService = pdfSignatureService;
    }

    public record SaveResult(boolean ok, String error, UserDigitalCertificate certificate) {
        public static SaveResult ok(UserDigitalCertificate cert) {
            return new SaveResult(true, null, cert);
        }

        public static SaveResult failed(String error) {
            return new SaveResult(false, error, null);
        }
    }

    public record VerifyResult(boolean ok, String commonName, String issuer, String validTo, String error) {
        public static VerifyResult success(String commonName, String issuer, String validTo) {
            return new VerifyResult(true, commonName, issuer, validTo, null);
        }
        public static VerifyResult failed(String error) {
            return new VerifyResult(false, null, null, null, error);
        }
    }

    public Optional<UserDigitalCertificate> findActive(UserDtls user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return certificateRepository.findFirstByUserIdAndIsActiveTrueOrderByCreatedAtDesc(user.getId());
    }

    public List<UserDigitalCertificate> findMine(UserDtls user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        return certificateRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(user.getId());
    }

    /**
     * Inspects and pre-verifies a .p12 byte array with the provided Digital ID Password.
     * Does NOT save anything to disk or DB — purely pre-flight validation.
     */
    public VerifyResult verifyP12(byte[] p12Bytes, String password) {
        if (p12Bytes == null || p12Bytes.length == 0) {
            return VerifyResult.failed("กรุณาเลือกไฟล์ .p12");
        }
        if (password == null || password.isBlank()) {
            return VerifyResult.failed("กรุณากรอก Digital ID Password");
        }
        try {
            PdfDigitalSignatureService.ParsedCertificateInfo info = pdfSignatureService.inspect(p12Bytes, password);
            if (info.validTo() != null && info.validTo().isBefore(LocalDateTime.now())) {
                return VerifyResult.failed("ใบรับรองดิจิทัลนี้หมดอายุแล้วเมื่อ " + info.validTo().format(DATE_FMT));
            }
            String validToStr = info.validTo() != null ? info.validTo().format(DATE_FMT) : "-";
            return VerifyResult.success(info.commonName(), info.issuerDn(), validToStr);
        } catch (Exception e) {
            log.warn("P12 pre-flight verification failed: {}", e.getMessage());
            return VerifyResult.failed("Digital ID Password ไม่ถูกต้อง หรือไฟล์ .p12 ไม่สมบูรณ์");
        }
    }

    /**
     * Pre-verifies a Digital ID Password against the user's currently active .p12 file on disk.
     */
    public VerifyResult verifyActiveCertificatePassword(UserDtls user, String password) {
        if (user == null) {
            return VerifyResult.failed("ไม่พบข้อมูลผู้ใช้งาน");
        }
        Optional<UserDigitalCertificate> optCert = findActive(user);
        if (optCert.isEmpty()) {
            return VerifyResult.failed("ไม่พบใบรับรองดิจิทัลที่เปิดใช้งานอยู่");
        }
        byte[] p12Bytes = storage.read(optCert.get().getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            return VerifyResult.failed("ไม่พบไฟล์ใบรับรองในระบบ");
        }
        return verifyP12(p12Bytes, password);
    }

    /**
     * Updates the Digital ID Password for the user's active certificate.
     */
    @Transactional
    public SaveResult updatePassword(UserDtls user, String newPassword) {
        if (user == null) {
            return SaveResult.failed("ไม่พบข้อมูลผู้ใช้งาน");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return SaveResult.failed("กรุณากรอก Digital ID Password ใหม่");
        }
        Optional<UserDigitalCertificate> optCert = findActive(user);
        if (optCert.isEmpty()) {
            return SaveResult.failed("ไม่พบใบรับรองดิจิทัลที่เปิดใช้งานอยู่");
        }
        UserDigitalCertificate cert = optCert.get();
        byte[] p12Bytes = storage.read(cert.getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            return SaveResult.failed("ไม่พบไฟล์ใบรับรองในระบบ");
        }

        // Verify that the new password actually unlocks the file
        try {
            pdfSignatureService.inspect(p12Bytes, newPassword);
        } catch (Exception e) {
            return SaveResult.failed("Digital ID Password ไม่ถูกต้องสำหรับไฟล์ใบรับรองนี้");
        }

        cert.setEncryptedPin(pinEncryptionService.encrypt(newPassword));
        certificateRepository.save(cert);
        log.info("Updated Digital ID Password for user {} cert {}", user.getId(), cert.getId());
        return SaveResult.ok(cert);
    }

    /**
     * Registers and verifies a new .p12 digital certificate for a user (Always One-Click Sign).
     */
    @Transactional
    public SaveResult registerCertificate(UserDtls user, byte[] p12Bytes, String originalFilename, String pin) {
        return registerCertificate(user, p12Bytes, originalFilename, pin, true);
    }

    /**
     * Registers and verifies a new .p12 digital certificate for a user.
     */
    @Transactional
    public SaveResult registerCertificate(UserDtls user, byte[] p12Bytes, String originalFilename,
                                          String pin, boolean rememberPin) {
        if (user == null) {
            return SaveResult.failed("ไม่พบข้อมูลผู้ใช้งาน");
        }
        if (p12Bytes == null || p12Bytes.length == 0) {
            return SaveResult.failed("กรุณาเลือกไฟล์ .p12");
        }
        if (pin == null || pin.isBlank()) {
            return SaveResult.failed("กรุณาระบุ Digital ID Password ของไฟล์ .p12 เพื่อทดสอบเปิดใช้งาน");
        }

        // 1. Inspect and verify with the PIN
        PdfDigitalSignatureService.ParsedCertificateInfo info;
        try {
            info = pdfSignatureService.inspect(p12Bytes, pin);
        } catch (Exception e) {
            log.warn("Failed to inspect .p12 certificate for user {}: {}", user.getId(), e.getMessage());
            return SaveResult.failed("Digital ID Password ไม่ถูกต้อง หรือไฟล์ .p12 ไม่สมบูรณ์ (" + e.getMessage() + ")");
        }

        if (info.validTo() != null && info.validTo().isBefore(LocalDateTime.now())) {
            return SaveResult.failed("ใบรับรองดิจิทัลนี้หมดอายุแล้วเมื่อ " + info.validTo().format(DATE_FMT));
        }

        // 2. Store .p12 securely
        String storedFilename;
        try {
            storedFilename = storage.store(p12Bytes);
        } catch (Exception e) {
            log.error("Failed to store .p12 certificate: {}", e.getMessage(), e);
            return SaveResult.failed("ไม่สามารถบันทึกไฟล์ใบรับรองลงระบบได้");
        }

        // 3. Deactivate previous certificates for this user
        certificateRepository.deactivateAllFor(user.getId());

        // 4. Save entity
        UserDigitalCertificate cert = new UserDigitalCertificate();
        cert.setUser(user);
        cert.setCertificatePath(storedFilename);
        cert.setOriginalFilename(originalFilename);
        cert.setSubjectDn(info.subjectDn());
        cert.setIssuerDn(info.issuerDn());
        cert.setSerialNumber(info.serialNumber());
        cert.setValidFrom(info.validFrom());
        cert.setValidTo(info.validTo());
        cert.setActive(true);

        if (rememberPin) {
            cert.setEncryptedPin(pinEncryptionService.encrypt(pin));
        } else {
            cert.setEncryptedPin(null);
        }

        UserDigitalCertificate saved = certificateRepository.save(cert);
        log.info("Registered Digital Certificate {} for user {} (Subject: {})",
                saved.getId(), user.getId(), info.subjectDn());

        return SaveResult.ok(saved);
    }

    /**
     * Deactivates a user's certificate.
     */
    @Transactional
    public void deactivate(Long certId, UserDtls user) {
        if (certId == null || user == null) return;
        certificateRepository.findByIdAndUserId(certId, user.getId()).ifPresent(cert -> {
            cert.setActive(false);
            certificateRepository.save(cert);
            log.info("Deactivated Digital Certificate {} for user {}", certId, user.getId());
        });
    }

    /**
     * Resolves the PIN for a user's certificate (either from on-demand input or decrypted from storage).
     */
    public String resolvePin(UserDigitalCertificate cert, String onDemandPin) {
        if (onDemandPin != null && !onDemandPin.isBlank()) {
            return onDemandPin;
        }
        if (cert != null && cert.hasSavedPin()) {
            return pinEncryptionService.decrypt(cert.getEncryptedPin());
        }
        return null;
    }

    /**
     * Signs a PDF using the active certificate of the given signer.
     *
     * @return signed PDF bytes, or original bytes if signer has no certificate or signing fails
     */
    public byte[] signPdfForSigner(byte[] pdfBytes, UserDtls signer, String onDemandPin,
                                   String reason, String location) {
        return signPdfForSigner(pdfBytes, signer, onDemandPin, reason, location, null);
    }

    /**
     * Signs the given PDF bytes using the signer's active digital certificate and an exact timestamp.
     *
     * @return signed PDF bytes, or original bytes if signer has no certificate or signing fails
     */
    public byte[] signPdfForSigner(byte[] pdfBytes, UserDtls signer, String onDemandPin,
                                   String reason, String location, java.time.LocalDateTime signDate) {
        if (pdfBytes == null || signer == null) {
            return pdfBytes;
        }
        Optional<UserDigitalCertificate> optCert = findActive(signer);
        if (optCert.isEmpty()) {
            return pdfBytes;
        }

        UserDigitalCertificate cert = optCert.get();
        if (cert.isExpired()) {
            log.warn("Signer {} certificate {} is expired, skipping digital signature", signer.getId(), cert.getId());
            return pdfBytes;
        }

        String pin = resolvePin(cert, onDemandPin);
        if (pin == null || pin.isBlank()) {
            log.info("No PIN available for signer {} certificate {}, skipping digital signature", signer.getId(), cert.getId());
            return pdfBytes;
        }

        byte[] p12Bytes = storage.read(cert.getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            log.warn("Certificate file missing on disk for cert {}", cert.getId());
            return pdfBytes;
        }

        try {
            String signerDisplayName = cert.getCommonName();
            return pdfSignatureService.signPdf(pdfBytes, p12Bytes, pin, signerDisplayName, reason, location, signDate);
        } catch (Exception e) {
            log.error("Failed to apply digital signature for signer {}: {}", signer.getId(), e.getMessage(), e);
            return pdfBytes;
        }
    }

    /**
     * Applies cryptographic digital signatures for all completed steps in the envelope
     * whose signers have registered digital certificates with available PINs,
     * preserving each step's exact confirmed signing timestamp.
     */
    public byte[] applyDigitalSignaturesToEnvelope(byte[] pdfBytes, SignatureRequest envelope,
                                                   Map<Integer, String> signerPins) {
        if (pdfBytes == null || envelope == null || envelope.getSteps() == null) {
            return pdfBytes;
        }

        byte[] currentPdf = pdfBytes;
        for (SignatureStep step : envelope.getSteps()) {
            if (step.getStatus() == SignatureStepStatus.SIGNED && step.getSigner() != null) {
                UserDtls signer = step.getSigner();
                String onDemandPin = (signerPins != null) ? signerPins.get(signer.getId()) : null;
                String reason = "ลงนามในตำแหน่ง \"" + (step.getRoleLabel() != null ? step.getRoleLabel() : "ผู้ลงนาม") + "\"";
                String location = "มหาวิทยาลัยขอนแก่น";

                byte[] signed = signPdfForSigner(currentPdf, signer, onDemandPin, reason, location, step.getSignedAt());
                if (signed != null && signed.length > 0) {
                    currentPdf = signed;
                }
            }
        }
        return currentPdf;
    }
}
