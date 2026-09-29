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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts and decrypts user digital certificate PINs using AES-256-GCM.
 *
 * <p><b>Two keys.</b> PINs used to be encrypted with a key derived from a passphrase
 * that is committed to this repository, so anyone with the source and a database
 * dump could decrypt every PIN. That key is kept for reading old rows only
 * ({@link #LEGACY_SECRET}). New ciphertext is written with the key from
 * {@code APP_ESIGN_P12_MASTER_KEY} (32 random bytes, Base64) and carries the
 * prefix {@code v1:}. PINs are no longer stored at all (V33); the key now protects the
 * .p12 files at rest ({@link #encryptFile}).
 *
 * <p><b>Unset key.</b> The service still starts, logs an error, and keeps using the
 * legacy key for both directions. Refusing to start would take signing down on a
 * deploy that forgot the variable, which is worse than the state it is already in.
 */
@Service
public class CertificatePinEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(CertificatePinEncryptionService.class);
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    /** The passphrase that shipped in source. Compromised: decrypt-only. */
    static final String LEGACY_SECRET = "HrcpKKU_P12_MasterKey_Secret_2026";

    static final String V1_PREFIX = "v1:";

    private final SecretKeySpec legacyKey;
    /** Null when {@code APP_ESIGN_P12_MASTER_KEY} is not set. */
    private final SecretKeySpec currentKey;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public CertificatePinEncryptionService(
            @Value("${app.esign.p12-master-key:}") String masterKeyBase64,
            @Value("${app.security.p12-master-key:" + LEGACY_SECRET + "}") String legacySecret) {
        this.legacyKey = deriveLegacyKey(legacySecret);
        this.currentKey = parseMasterKey(masterKeyBase64);
        if (currentKey == null) {
            log.error("APP_ESIGN_P12_MASTER_KEY is not set — certificate PINs are still encrypted with the "
                    + "key committed to source. Generate one with `openssl rand -base64 32` and set it on "
                    + "the service before relying on digital signatures.");
        }
    }

    /** Legacy-only instance, for tests that do not care about key versions. */
    public CertificatePinEncryptionService(String legacySecret) {
        this(null, legacySecret);
    }

    public boolean hasCurrentKey() {
        return currentKey != null;
    }

    /** True for a stored value that should be rewritten with the current key. */
    public boolean needsReEncryption(String stored) {
        return currentKey != null && stored != null && !stored.isBlank() && !stored.startsWith(V1_PREFIX);
    }

    /**
     * Encrypts plaintext PIN with AES-GCM and returns IV + ciphertext as Base64,
     * prefixed with {@code v1:} when the current key is configured.
     */
    public String encrypt(String plaintextPin) {
        if (plaintextPin == null || plaintextPin.isBlank()) {
            return null;
        }
        try {
            if (currentKey != null) {
                return V1_PREFIX + seal(currentKey, plaintextPin);
            }
            return seal(legacyKey, plaintextPin);
        } catch (Exception e) {
            log.error("Failed to encrypt certificate PIN: {}", e.getMessage());
            throw new RuntimeException("Could not secure certificate PIN", e);
        }
    }

    /**
     * Decrypts a stored PIN back to plaintext.
     *
     * @return the PIN, or null when the value cannot be decrypted (wrong or missing key)
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            if (stored.startsWith(V1_PREFIX)) {
                if (currentKey == null) {
                    log.error("A certificate PIN was encrypted with APP_ESIGN_P12_MASTER_KEY, but the key is "
                            + "not set in this run — it cannot be decrypted.");
                    return null;
                }
                return open(currentKey, stored.substring(V1_PREFIX.length()));
            }
            return open(legacyKey, stored);
        } catch (Exception e) {
            log.error("Failed to decrypt certificate PIN: {}", e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ .p12 files

    /** Marks a .p12 file encrypted with the current key. A PKCS#12 file never starts like this. */
    private static final byte[] FILE_MAGIC = "HRCPP12E1".getBytes(StandardCharsets.US_ASCII);

    public static boolean isEncryptedFile(byte[] data) {
        return data != null && data.length > FILE_MAGIC.length
                && java.util.Arrays.equals(data, 0, FILE_MAGIC.length, FILE_MAGIC, 0, FILE_MAGIC.length);
    }

    /**
     * Encrypts a .p12 file for storage, or returns it unchanged when no key is set.
     * The PIN no longer being kept makes the file what an attacker would try to
     * brute-force offline; this keeps a copied file or database dump useless without the key.
     */
    public byte[] encryptFile(byte[] plain) {
        if (currentKey == null || plain == null || isEncryptedFile(plain)) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] out = new byte[FILE_MAGIC.length + IV_LENGTH + ct.length];
            System.arraycopy(FILE_MAGIC, 0, out, 0, FILE_MAGIC.length);
            System.arraycopy(iv, 0, out, FILE_MAGIC.length, IV_LENGTH);
            System.arraycopy(ct, 0, out, FILE_MAGIC.length + IV_LENGTH, ct.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt the certificate file", e);
        }
    }

    /**
     * The .p12 bytes of a stored file, encrypted or not.
     *
     * @throws IllegalStateException for an encrypted file when the key is missing or wrong
     */
    public byte[] decryptFile(byte[] stored) {
        if (!isEncryptedFile(stored)) {
            return stored;
        }
        if (currentKey == null) {
            throw new IllegalStateException("Certificate file is encrypted but APP_ESIGN_P12_MASTER_KEY is not set");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, currentKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH, stored, FILE_MAGIC.length, IV_LENGTH));
            int off = FILE_MAGIC.length + IV_LENGTH;
            return cipher.doFinal(stored, off, stored.length - off);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt the certificate file (wrong key?)", e);
        }
    }

    private String seal(SecretKeySpec key, String plaintext) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private static String open(SecretKeySpec key, String base64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(base64);
        if (combined.length < IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted PIN format");
        }
        Cipher cipher = Cipher.getInstance(ALGO);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, IV_LENGTH));
        byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveLegacyKey(String secret) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest((secret == null || secret.isBlank() ? LEGACY_SECRET : secret)
                            .getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize CertificatePinEncryptionService key", e);
        }
    }

    /**
     * A set-but-malformed key fails startup: silently falling back would write PINs
     * with the compromised key while the operator believes the new one is in use.
     */
    private static SecretKeySpec parseMasterKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_ESIGN_P12_MASTER_KEY is not valid Base64", e);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("APP_ESIGN_P12_MASTER_KEY must decode to 32 bytes (got "
                    + bytes.length + "). Generate one with `openssl rand -base64 32`.");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
