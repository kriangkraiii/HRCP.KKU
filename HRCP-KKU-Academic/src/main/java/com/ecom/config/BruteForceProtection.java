package com.ecom.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory brute-force protection.
 * Tracks failed login attempts per IP + username.
 * After MAX_ATTEMPTS failures within WINDOW_MS, blocks further attempts.
 */
@Component
public class BruteForceProtection {

    private static final int MAX_ATTEMPTS = 5;
    private static final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    private static final long WINDOW_MS = 10 * 60 * 1000; // 10 minute window

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    /**
     * Record a failed login attempt.
     */
    public void recordFailure(String key) {
        attempts.compute(key, (k, info) -> {
            if (info == null || info.isExpired()) {
                return new AttemptInfo();
            }
            info.increment();
            if (info.count >= MAX_ATTEMPTS) {
                info.blockedUntil = System.currentTimeMillis() + BLOCK_DURATION_MS;
            }
            return info;
        });
    }

    /**
     * Check if the key (IP or username) is currently blocked.
     */
    public boolean isBlocked(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null)
            return false;
        if (info.blockedUntil > 0 && System.currentTimeMillis() < info.blockedUntil) {
            return true;
        }
        if (info.isExpired()) {
            attempts.remove(key);
            return false;
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
        if (info == null || info.blockedUntil <= 0)
            return 0;
        long remaining = info.blockedUntil - System.currentTimeMillis();
        return remaining > 0 ? (remaining / 60_000) + 1 : 0;
    }

    private static class AttemptInfo {
        int count = 1;
        long firstAttempt = System.currentTimeMillis();
        long blockedUntil = 0;

        void increment() {
            count++;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - firstAttempt > WINDOW_MS && blockedUntil <= System.currentTimeMillis();
        }
    }
}
