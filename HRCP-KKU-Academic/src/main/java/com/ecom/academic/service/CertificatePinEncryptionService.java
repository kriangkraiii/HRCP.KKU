package com.ecom.academic.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts and decrypts user digital certificate PINs using AES-256-GCM.
 * Used only when the user explicitly opts in to "Remember PIN" for one-click signing.
 */
@Service
public class CertificatePinEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(CertificatePinEncryptionService.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec secretKey;

    public CertificatePinEncryptionService(
            @Value("${app.security.p12-master-key:HrcpKKU_P12_MasterKey_Secret_2026}") String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize CertificatePinEncryptionService key", e);
        }
    }

    /**
     * Encrypts plaintext PIN with AES-GCM and returns IV + ciphertext as Base64.
     */
    public String encrypt(String plaintextPin) {
        if (plaintextPin == null || plaintextPin.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintextPin.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Failed to encrypt certificate PIN: {}", e.getMessage());
            throw new RuntimeException("Could not secure certificate PIN", e);
        }
    }

    /**
     * Decrypts Base64 string containing IV + ciphertext back to plaintext PIN.
     */
    public String decrypt(String encryptedPinBase64) {
        if (encryptedPinBase64 == null || encryptedPinBase64.isBlank()) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedPinBase64);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted PIN format");
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            int ciphertextLen = combined.length - IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLen];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertextLen);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] decrypted = cipher.doFinal(ciphertext);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt certificate PIN: {}", e.getMessage());
            return null;
        }
    }
}
