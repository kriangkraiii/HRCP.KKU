package com.ecom.academic.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecom.academic.model.DigitalCertificateAudit.Event;
import com.ecom.academic.model.SignatureRequest;
import com.ecom.academic.model.SignatureStep;
import com.ecom.academic.model.SignatureStepStatus;
import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;
import com.ecom.config.BruteForceProtection;
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
    /** Null only in unit tests that build the service by hand. */
    private final DigitalCertificateAuditService audit;
    /** Wrong PINs against a stored .p12, per user. The file never leaves the server,
     *  so this is the only place a PIN can be guessed. */
    private final BruteForceProtection pinAttempts;

    @org.springframework.beans.factory.annotation.Autowired
    public UserDigitalCertificateService(
            UserDigitalCertificateRepository certificateRepository,
            DigitalCertificateStorage storage,
            CertificatePinEncryptionService pinEncryptionService,
            PdfDigitalSignatureService pdfSignatureService,
            DigitalCertificateAuditService audit,
            BruteForceProtection pinAttempts) {
        this.certificateRepository = certificateRepository;
        this.storage = storage;
        this.pinEncryptionService = pinEncryptionService;
        this.pdfSignatureService = pdfSignatureService;
        this.audit = audit;
        this.pinAttempts = pinAttempts;
    }

    /** For unit tests: no trail, a private attempt counter. */
    public UserDigitalCertificateService(
            UserDigitalCertificateRepository certificateRepository,
            DigitalCertificateStorage storage,
            CertificatePinEncryptionService pinEncryptionService,
            PdfDigitalSignatureService pdfSignatureService) {
        this(certificateRepository, storage, pinEncryptionService, pdfSignatureService, null,
                new BruteForceProtection());
    }

    public enum UnlockStatus { OK, NO_PIN, WRONG_PIN, LOCKED, FILE_MISSING }

    /** Outcome of opening a stored .p12 with a PIN. {@code info} is set only on OK. */
    public record UnlockResult(UnlockStatus status, String error,
            PdfDigitalSignatureService.ParsedCertificateInfo info) {
        public boolean ok() {
            return status == UnlockStatus.OK;
        }
    }

    static final String PIN_REQUIRED = "กรุณากรอกรหัสผ่าน (PIN) ของ Digital ID เพื่อยืนยันการลงนาม";
    static final String PIN_WRONG = "รหัสผ่าน (PIN) สำหรับ Digital ID ไม่ถูกต้อง";

    private static String attemptKey(UserDtls user) {
        return "p12pin:" + user.getId();
    }

    private void record(UserDtls user, UserDigitalCertificate cert, Event event, String detail, String ip) {
        if (audit != null) {
            audit.record(user, cert, event, detail, ip);
        }
    }

    /**
     * Opens the user's stored .p12 with the PIN they typed, or the saved one if they
     * typed nothing. Counts wrong typed PINs and refuses after too many.
     */
    public UnlockResult unlock(UserDtls user, UserDigitalCertificate cert, String onDemandPin, String ipAddress) {
        return unlock(user, cert, onDemandPin, ipAddress, false);
    }

    /**
     * @param typedOnly refuse the saved PIN: the signer must type it now. Used where
     *                  the signature is made at this moment with the signer's key.
     */
    public UnlockResult unlock(UserDtls user, UserDigitalCertificate cert, String onDemandPin, String ipAddress,
            boolean typedOnly) {
        String key = attemptKey(user);
        if (pinAttempts.isBlocked(key)) {
            record(user, cert, Event.PIN_LOCKED, null, ipAddress);
            return new UnlockResult(UnlockStatus.LOCKED,
                    "กรอกรหัสผ่าน Digital ID ผิดหลายครั้งเกินไป กรุณารอ "
                            + pinAttempts.getBlockMinutesRemaining(key) + " นาทีแล้วลองใหม่", null);
        }
        boolean typed = onDemandPin != null && !onDemandPin.isBlank();
        String pin = typedOnly ? (typed ? onDemandPin : null) : resolvePin(cert, onDemandPin);
        if (pin == null || pin.isBlank()) {
            return new UnlockResult(UnlockStatus.NO_PIN, PIN_REQUIRED, null);
        }
        byte[] p12Bytes = storage.read(cert.getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            return new UnlockResult(UnlockStatus.FILE_MISSING,
                    "ไม่พบไฟล์ใบรับรอง (.p12) ในระบบ — กรุณาอัปโหลดไฟล์ .p12 ใหม่", null);
        }
        try {
            var info = pdfSignatureService.inspect(p12Bytes, pin);
            pinAttempts.resetAttempts(key);
            record(user, cert, Event.PIN_OK, typed ? "typed" : "saved PIN", ipAddress);
            return new UnlockResult(UnlockStatus.OK, null, info);
        } catch (Exception e) {
            if (!typed) {
                // The saved PIN no longer opens the file. Not a guess, so not counted.
                return new UnlockResult(UnlockStatus.NO_PIN,
                        "รหัสผ่าน Digital ID ที่บันทึกไว้ใช้ไม่ได้แล้ว " + PIN_REQUIRED, null);
            }
            pinAttempts.recordFailure(key);
            record(user, cert, Event.PIN_WRONG, null, ipAddress);
            return new UnlockResult(UnlockStatus.WRONG_PIN, PIN_WRONG, null);
        }
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
     * The certificate's key, opened for one signature. Call only after
     * {@link #unlock} accepted the same PIN.
     */
    public com.ecom.academic.service.pdf.CmsSigner openSigner(UserDigitalCertificate cert, String pin) throws IOException {
        byte[] p12Bytes = storage.read(cert.getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            throw new IOException("Certificate file missing for certificate " + cert.getId());
        }
        try {
            return com.ecom.academic.service.pdf.CmsSigner.open(p12Bytes, pin.toCharArray());
        } catch (java.security.GeneralSecurityException e) {
            throw new IOException("Could not open the certificate: " + e.getMessage(), e);
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
        if (password == null || password.isBlank()) {
            return VerifyResult.failed("กรุณากรอก Digital ID Password");
        }
        UnlockResult unlock = unlock(user, optCert.get(), password, null);
        if (!unlock.ok()) {
            return VerifyResult.failed(unlock.error());
        }
        var info = unlock.info();
        if (info.validTo() != null && info.validTo().isBefore(LocalDateTime.now())) {
            return VerifyResult.failed("ใบรับรองดิจิทัลนี้หมดอายุแล้วเมื่อ " + info.validTo().format(DATE_FMT));
        }
        return VerifyResult.success(info.commonName(), info.issuerDn(),
                info.validTo() != null ? info.validTo().format(DATE_FMT) : "-");
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

        // Verify that the new password actually unlocks the file — counted like any
        // other PIN typed against a stored .p12, or this would be a guessing oracle.
        UnlockResult unlock = unlock(user, cert, newPassword, null);
        if (!unlock.ok()) {
            return SaveResult.failed(unlock.status() == UnlockStatus.WRONG_PIN
                    ? "Digital ID Password ไม่ถูกต้องสำหรับไฟล์ใบรับรองนี้"
                    : unlock.error());
        }

        cert.setEncryptedPin(pinEncryptionService.encrypt(newPassword));
        certificateRepository.save(cert);
        record(user, cert, Event.PIN_CHANGED, null, null);
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
        record(user, saved, Event.UPLOADED, info.subjectDn(), null);
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
            record(user, cert, Event.DEACTIVATED, null, null);
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
        return signWith(pdfBytes, signer, optCert.get(), onDemandPin, reason, location, signDate);
    }

    private byte[] signWith(byte[] pdfBytes, UserDtls signer, UserDigitalCertificate cert, String onDemandPin,
                            String reason, String location, java.time.LocalDateTime signDate) {
        if (cert.isExpired()) {
            log.warn("Signer {} certificate {} is expired, skipping digital signature", signer.getId(), cert.getId());
            record(signer, cert, Event.PDF_SIGN_SKIPPED, "certificate expired", null);
            return pdfBytes;
        }

        boolean typed = onDemandPin != null && !onDemandPin.isBlank();
        String pin = resolvePin(cert, onDemandPin);
        if (pin == null || pin.isBlank()) {
            log.warn("No PIN available for signer {} certificate {}, skipping digital signature", signer.getId(), cert.getId());
            record(signer, cert, Event.PDF_SIGN_SKIPPED, "no PIN available", null);
            return pdfBytes;
        }

        byte[] p12Bytes = storage.read(cert.getCertificatePath());
        if (p12Bytes == null || p12Bytes.length == 0) {
            log.warn("Certificate file missing on disk for cert {}", cert.getId());
            record(signer, cert, Event.PDF_SIGN_SKIPPED, "certificate file missing", null);
            return pdfBytes;
        }

        try {
            String signerDisplayName = cert.getCommonName();
            byte[] signed = pdfSignatureService.signPdf(pdfBytes, p12Bytes, pin, signerDisplayName, reason, location, signDate);
            record(signer, cert, typed ? Event.PDF_SIGNED : Event.STORED_PIN_USED, reason, null);
            return signed;
        } catch (Exception e) {
            log.error("Failed to apply digital signature for signer {}: {}", signer.getId(), e.getMessage(), e);
            record(signer, cert, Event.PDF_SIGN_SKIPPED, "signing failed: " + e.getMessage(), null);
            return pdfBytes;
        }
    }

    /**
     * The certificate checked when the step was signed; the signer's current one
     * only for steps signed before that was recorded.
     */
    private Optional<UserDigitalCertificate> certificateFor(SignatureStep step) {
        if (step.getDigitalCertificateId() != null) {
            return certificateRepository.findById(step.getDigitalCertificateId());
        }
        return findActive(step.getSigner());
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

                Optional<UserDigitalCertificate> cert = certificateFor(step);
                if (cert.isEmpty()) {
                    continue;
                }
                byte[] signed = signWith(currentPdf, signer, cert.get(), onDemandPin, reason, location, step.getSignedAt());
                if (signed != null && signed.length > 0) {
                    currentPdf = signed;
                }
            }
        }
        return currentPdf;
    }
}
