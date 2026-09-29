package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("กุญแจเข้ารหัสรหัสผ่าน .p12")
class CertificatePinEncryptionServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CertificatePinEncryptionService legacyOnly =
            new CertificatePinEncryptionService(null, CertificatePinEncryptionService.LEGACY_SECRET);
    private final CertificatePinEncryptionService withKey =
            new CertificatePinEncryptionService(MASTER_KEY, CertificatePinEncryptionService.LEGACY_SECRET);

    @Test
    @DisplayName("ไม่ได้ตั้งกุญแจ — ยังเข้ารหัสและถอดได้ด้วยกุญแจเดิม ไม่ติด prefix")
    void withoutAKeyTheLegacyFormatIsKept() {
        String stored = legacyOnly.encrypt("1234");
        assertThat(stored).doesNotStartWith("v1:");
        assertThat(legacyOnly.decrypt(stored)).isEqualTo("1234");
        assertThat(legacyOnly.needsReEncryption(stored)).isFalse();
    }

    @Test
    @DisplayName("ตั้งกุญแจแล้ว — ค่าใหม่ติด v1: และค่าเก่ายังถอดได้และถูกระบุว่าต้องเข้ารหัสใหม่")
    void withAKeyNewValuesAreVersionedAndOldOnesStillRead() {
        String old = legacyOnly.encrypt("1234");
        String fresh = withKey.encrypt("1234");

        assertThat(fresh).startsWith("v1:");
        assertThat(withKey.decrypt(fresh)).isEqualTo("1234");
        assertThat(withKey.decrypt(old)).isEqualTo("1234");
        assertThat(withKey.needsReEncryption(old)).isTrue();
        assertThat(withKey.needsReEncryption(fresh)).isFalse();
    }

    @Test
    @DisplayName("ค่า v1: ที่ไม่มีกุญแจในรอบนี้ — คืน null ไม่ถอดด้วยกุญแจเดิม")
    void aVersionedValueWithoutTheKeyIsUnreadable() {
        assertThat(legacyOnly.decrypt(withKey.encrypt("1234"))).isNull();
    }

    @Test
    @DisplayName("กุญแจที่ตั้งไว้แต่รูปแบบผิด — แอปต้องไม่เริ่ม")
    void aMalformedKeyFailsFast() {
        assertThatThrownBy(() -> new CertificatePinEncryptionService("not-base64!!", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CertificatePinEncryptionService(
                Base64.getEncoder().encodeToString(new byte[16]), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
