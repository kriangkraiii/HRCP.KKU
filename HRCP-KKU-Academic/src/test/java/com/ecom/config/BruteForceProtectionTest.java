package com.ecom.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


@DisplayName("BruteForceProtection Tests")
class BruteForceProtectionTest {

    private BruteForceProtection bfp;

    @BeforeEach
    void setUp() {
        bfp = new BruteForceProtection();
    }

    // ── Basic blocking ─────────────────────────────────────────────────

    @Test
    @DisplayName("ยังไม่เคย fail → ไม่ถูกบล็อก")
    void noAttempts_shouldNotBeBlocked() {
        assertFalse(bfp.isBlocked("user:fresh@test.com"));
    }

    @Test
    @DisplayName("fail 4 ครั้ง → ยังไม่ถูกบล็อก")
    void fourAttempts_shouldNotBeBlocked() {
        String key = "user:test@test.com";
        for (int i = 0; i < 4; i++) {
            bfp.recordFailure(key);
        }
        assertFalse(bfp.isBlocked(key));
        assertEquals(1, bfp.getRemainingAttempts(key));
    }

    @Test
    @DisplayName("fail 5 ครั้ง → ถูกบล็อก")
    void fiveAttempts_shouldBeBlocked() {
        String key = "user:test@test.com";
        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(key);
        }
        assertTrue(bfp.isBlocked(key));
        assertEquals(0, bfp.getRemainingAttempts(key));
    }

    // ── Remaining attempts ─────────────────────────────────────────────

    @Test
    @DisplayName("ยังไม่มี record → remaining = 5")
    void unknownKey_shouldReturnMaxAttempts() {
        assertEquals(5, bfp.getRemainingAttempts("user:unknown@test.com"));
    }

    @Test
    @DisplayName("fail 3 ครั้ง → remaining = 2")
    void threeAttempts_shouldHaveTwoRemaining() {
        String key = "user:counting@test.com";
        for (int i = 0; i < 3; i++) {
            bfp.recordFailure(key);
        }
        assertEquals(2, bfp.getRemainingAttempts(key));
    }

    // ── Progressive lockout ────────────────────────────────────────────

    @Test
    @DisplayName("1st lock → blockDuration = 15min")
    void firstLock_shouldBe15Minutes() {
        String key = "user:escalate@test.com";
        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(key);
        }
        assertEquals(15, bfp.getBlockDurationMinutes(key));
    }

    // ── Reset ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetAttempts → ลบ record ทั้งหมดของ key")
    void reset_shouldClearBlocked() {
        String key = "user:reset@test.com";
        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(key);
        }
        assertTrue(bfp.isBlocked(key));

        bfp.resetAttempts(key);

        assertFalse(bfp.isBlocked(key));
        assertEquals(5, bfp.getRemainingAttempts(key));
    }

    // ── BlockedUntilMillis ─────────────────────────────────────────────

    @Test
    @DisplayName("blockedUntilMillis → อนาคตเมื่อถูกบล็อก")
    void blockedUntil_shouldBeInFuture() {
        String key = "user:until@test.com";
        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(key);
        }
        long until = bfp.getBlockedUntilMillis(key);
        assertTrue(until > System.currentTimeMillis());
    }

    @Test
    @DisplayName("blockedUntilMillis → 0 เมื่อไม่เคย fail")
    void blockedUntil_unknownKey_shouldBeZero() {
        assertEquals(0, bfp.getBlockedUntilMillis("user:nobody@test.com"));
    }

    // ── IP vs User isolation ───────────────────────────────────────────

    @Test
    @DisplayName("IP key กับ User key → แยกกัน")
    void ipAndUser_shouldBeIsolated() {
        String ipKey = "ip:192.168.1.1";
        String userKey = "user:test@test.com";

        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(ipKey);
        }

        assertTrue(bfp.isBlocked(ipKey));
        assertFalse(bfp.isBlocked(userKey));
        assertEquals(5, bfp.getRemainingAttempts(userKey));
    }

    // ── Currently blocked should not increment ─────────────────────────

    @Test
    @DisplayName("บล็อกอยู่ → recordFailure ไม่เพิ่ม count")
    void blocked_shouldNotIncrementFurther() {
        String key = "user:doublehit@test.com";
        for (int i = 0; i < 5; i++) {
            bfp.recordFailure(key);
        }
        long durationBefore = bfp.getBlockDurationMinutes(key);

        // Extra attempts while blocked
        bfp.recordFailure(key);
        bfp.recordFailure(key);

        assertEquals(durationBefore, bfp.getBlockDurationMinutes(key));
    }
}
