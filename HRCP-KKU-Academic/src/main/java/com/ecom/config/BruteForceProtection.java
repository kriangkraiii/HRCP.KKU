package com.ecom.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory brute-force protection with progressive lockout.
 * 
 * After MAX_ATTEMPTS failures within WINDOW_MS, blocks further attempts.
 * Lock duration escalates on repeated lockouts:
 *   1st lock: 15 min, 2nd: 30 min, 3rd: 60 min, 4th+: 120 min (cap)
 */
@Component
public class BruteForceProtection {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_BLOCK_MINUTES = 15;
    private static final long WINDOW_MS = 10 * 60 * 1000; // 10 minute window

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    /**
     * Record a failed login attempt.
     */
    public void recordFailure(String key) {
        attempts.compute(key, (k, info) -> {
            if (info == null) {
                return new AttemptInfo();
            }
            // If previous block has expired, start a new cycle but keep lockCount
            if (info.isBlockExpired() && info.blockedUntil > 0) {
                AttemptInfo fresh = new AttemptInfo();
                fresh.lockCount = info.lockCount; // carry over escalation
                return fresh;
            }
            // If window expired and not blocked, reset attempts but keep lockCount
            if (info.isWindowExpired() && info.blockedUntil <= 0) {
                AttemptInfo fresh = new AttemptInfo();
                fresh.lockCount = info.lockCount;
                return fresh;
            }
            // If currently blocked, don't increment
            if (info.isCurrentlyBlocked()) {
                return info;
            }
            info.increment();
            if (info.count >= MAX_ATTEMPTS) {
                long blockMinutes = BASE_BLOCK_MINUTES * (1L << info.lockCount); // 15, 30, 60, 120, 240...
                info.blockedUntil = System.currentTimeMillis() + (blockMinutes * 60 * 1000);
                info.lastBlockMinutes = blockMinutes;
                info.lockCount++;
            }
            return info;
        });
    }

    /**
     * Check if the key (IP or username) is currently blocked.
     */
    public boolean isBlocked(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) return false;
        if (info.isCurrentlyBlocked()) return true;
        // Only clean up entries that have NEVER been locked (lockCount=0)
        // Entries with lockCount > 0 must be preserved for escalation
        if (info.isWindowExpired() && !info.isCurrentlyBlocked() && info.lockCount == 0) {
            attempts.remove(key);
        }
        return false;
    }

    /**
     * Clear attempts on successful login.
     */
    public void resetAttempts(String key) {
        attempts.remove(key);
    }

    /**
     * Get remaining block time in minutes.
     */
    public long getBlockMinutesRemaining(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null || info.blockedUntil <= 0) return 0;
        long remaining = info.blockedUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 60_000) + 1 : 0;
    }

    /**
     * Get the epoch millis when the block expires (for real-time countdown).
     */
    public long getBlockedUntilMillis(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) return 0;
        return info.blockedUntil;
    }

    /**
     * Get remaining attempts before lockout.
     */
    public int getRemainingAttempts(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) return MAX_ATTEMPTS;
        if (info.isCurrentlyBlocked()) return 0;
        if (info.isWindowExpired()) return MAX_ATTEMPTS;
        return Math.max(0, MAX_ATTEMPTS - info.count);
    }

    /**
     * Get the current block duration in minutes (for the most recent lock).
     */
    public long getBlockDurationMinutes(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) return 0;
        return info.lastBlockMinutes;
    }

    private static class AttemptInfo {
        int count = 1;
        long firstAttempt = System.currentTimeMillis();
        long blockedUntil = 0;
        int lockCount = 0; // how many times this key has been locked (for escalation)
        long lastBlockMinutes = 0; // duration of most recent lock

        void increment() {
            count++;
        }

        boolean isWindowExpired() {
            return System.currentTimeMillis() - firstAttempt > WINDOW_MS;
        }

        boolean isCurrentlyBlocked() {
            return blockedUntil > 0 && System.currentTimeMillis() < blockedUntil;
        }

        boolean isBlockExpired() {
            return blockedUntil > 0 && System.currentTimeMillis() >= blockedUntil;
        }
    }
}
