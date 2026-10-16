package com.ecom.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("โทเค็นลิงก์ตั้งรหัสผ่านใหม่หมดอายุได้")
class PasswordResetTokensTest {

    private static final Instant NOW = Instant.parse("2026-10-16T06:00:00Z");

    @Test
    @DisplayName("ใช้ได้ภายในหนึ่งชั่วโมง หลังจากนั้นใช้ไม่ได้")
    void expiresAfterAnHour() {
        String token = PasswordResetTokens.issue(NOW);

        assertThat(PasswordResetTokens.isFresh(token, NOW)).isTrue();
        assertThat(PasswordResetTokens.isFresh(token, NOW.plus(PasswordResetTokens.LIFETIME))).isTrue();
        assertThat(PasswordResetTokens.isFresh(token, NOW.plus(PasswordResetTokens.LIFETIME).plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("โทเค็นรุ่นเก่า (UUID เปล่า) รูปแบบผิด หรือเวลาในอนาคต ใช้ไม่ได้")
    void malformedTokensAreNotFresh() {
        assertThat(PasswordResetTokens.isFresh("6f1c2e9a-0d4b-4a7e-9c55-1b2d3e4f5a6b", NOW)).isFalse();
        assertThat(PasswordResetTokens.isFresh("abc.notanumber", NOW)).isFalse();
        assertThat(PasswordResetTokens.isFresh(null, NOW)).isFalse();
        assertThat(PasswordResetTokens.isFresh("x." + NOW.plusSeconds(3600).getEpochSecond(), NOW)).isFalse();
    }
}
