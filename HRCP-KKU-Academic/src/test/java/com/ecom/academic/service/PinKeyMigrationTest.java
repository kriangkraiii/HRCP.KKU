package com.ecom.academic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.academic.model.UserDigitalCertificate;
import com.ecom.academic.repository.UserDigitalCertificateRepository;

@DisplayName("ย้ายรหัสที่เก็บไว้ไปกุญแจใหม่ตอนแอปเริ่ม")
class PinKeyMigrationTest {

    private final CertificatePinEncryptionService legacy =
            new CertificatePinEncryptionService(null, CertificatePinEncryptionService.LEGACY_SECRET);
    private final CertificatePinEncryptionService current = new CertificatePinEncryptionService(
            Base64.getEncoder().encodeToString(new byte[32]), CertificatePinEncryptionService.LEGACY_SECRET);
    private final UserDigitalCertificateRepository repository = mock(UserDigitalCertificateRepository.class);

    private static UserDigitalCertificate withPin(String stored) {
        UserDigitalCertificate cert = new UserDigitalCertificate();
        cert.setEncryptedPin(stored);
        return cert;
    }

    @Test
    @DisplayName("แถวแบบเก่าถูกเข้ารหัสใหม่ แถวที่ใหม่แล้วไม่ถูกแตะ และรันซ้ำไม่เปลี่ยนอะไร")
    void rewritesLegacyRowsOnce() {
        UserDigitalCertificate old = withPin(legacy.encrypt("1234"));
        UserDigitalCertificate fresh = withPin(current.encrypt("5678"));
        String freshValue = fresh.getEncryptedPin();
        when(repository.findByEncryptedPinIsNotNull()).thenReturn(List.of(old, fresh));

        new PinKeyMigration(repository, current).migrate();

        assertThat(old.getEncryptedPin()).startsWith("v1:");
        assertThat(current.decrypt(old.getEncryptedPin())).isEqualTo("1234");
        assertThat(fresh.getEncryptedPin()).isEqualTo(freshValue);
        verify(repository, times(1)).save(any());

        new PinKeyMigration(repository, current).migrate();
        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("ไม่ได้ตั้งกุญแจใหม่ — ไม่ทำอะไรเลย")
    void doesNothingWithoutANewKey() {
        new PinKeyMigration(repository, legacy).migrate();
        verify(repository, never()).findByEncryptedPinIsNotNull();
    }
}
