package com.ecom.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ecom.external.service.ManualSyncGuard;

/**
 * The admin "sync now" buttons are the one place a person can spend the
 * upstream request budget at will. One publication sync costs five requests
 * against a 100-per-minute allowance, so roughly twenty rapid clicks would get
 * the integration rate-limited — taking the nightly job down with it.
 */
class ManualSyncGuardTest {

    private static final String USERS = "users";
    private static final String SCOPUS = "scopus";

    /** 60 s for users, 180 s for publications — the shipped defaults. */
    private ManualSyncGuard guard() {
        return new ManualSyncGuard(60, 180);
    }

    @Test
    @DisplayName("กดครั้งแรกต้องผ่าน")
    void firstClickIsAllowed() {
        assertThat(guard().claim(USERS)).isZero();
    }

    @Test
    @DisplayName("กดซ้ำทันทีต้องโดนกัน พร้อมบอกเวลาที่เหลือ")
    void secondClickIsRejectedWithRemainingTime() {
        ManualSyncGuard guard = guard();
        guard.claim(SCOPUS);

        Duration wait = guard.claim(SCOPUS);

        assertThat(wait).isPositive();
        assertThat(wait.toSeconds()).isBetween(170L, 180L);
    }

    @Test
    @DisplayName("กดรัว 20 ครั้ง ต้องผ่านแค่ครั้งเดียว")
    void twentyRapidClicksAllowOnlyOne() {
        ManualSyncGuard guard = guard();

        long allowed = IntStream.range(0, 20)
                .filter(i -> guard.claim(SCOPUS).isZero())
                .count();

        assertThat(allowed)
                .as("20 คลิก = 100 คำขอ ซึ่งเท่ากับโควตาทั้งนาทีของต้นทาง")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("กดพร้อมกันหลาย tab ต้องผ่านแค่ครั้งเดียว")
    void concurrentClicksAllowOnlyOne() throws Exception {
        ManualSyncGuard guard = guard();
        int threads = 16;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(i -> () -> guard.claim(SCOPUS).isZero())
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(tasks);
            long allowed = results.stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            }).count();

            assertThat(allowed)
                    .as("การเช็คกับการจองต้องเป็นขั้นตอนเดียวกัน ไม่งั้นสองคำขอพร้อมกันจะผ่านทั้งคู่")
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("งานคนละชนิดต้องมี cooldown แยกกัน")
    void cooldownsAreTrackedPerJob() {
        ManualSyncGuard guard = guard();

        assertThat(guard.claim(USERS)).isZero();
        // Blocking the faculty sync must not block the publication sync.
        assertThat(guard.claim(SCOPUS)).isZero();
        assertThat(guard.claim(USERS)).isPositive();
    }

    @Test
    @DisplayName("ผลงานต้อง cooldown นานกว่าข้อมูลอาจารย์ เพราะใช้คำขอมากกว่า")
    void publicationSyncHasTheLongerCooldown() {
        ManualSyncGuard guard = guard();
        guard.claim(USERS);
        guard.claim(SCOPUS);

        assertThat(guard.claim(SCOPUS).toSeconds())
                .isGreaterThan(guard.claim(USERS).toSeconds());
    }

    @Test
    @DisplayName("ถ้าการดึงล้มเหลว ต้องกดใหม่ได้ทันที ไม่ต้องรอ cooldown")
    void aFailedRunReleasesTheCooldown() {
        ManualSyncGuard guard = guard();
        guard.claim(SCOPUS);
        assertThat(guard.claim(SCOPUS)).isPositive();

        // What the controller does when the sync reports a failure.
        guard.release(SCOPUS);

        assertThat(guard.claim(SCOPUS)).isZero();
    }

    @Test
    @DisplayName("อ่านเวลาที่เหลือต้องไม่กินสิทธิ์การกด")
    void readingRemainingTimeDoesNotConsumeTheSlot() {
        ManualSyncGuard guard = guard();

        // The page renders the remaining time on every load; doing so must not
        // start a cooldown of its own.
        assertThat(guard.remaining(USERS)).isZero();
        assertThat(guard.remaining(USERS)).isZero();
        assertThat(guard.claim(USERS)).isZero();
        assertThat(guard.remaining(USERS)).isPositive();
    }

    @Test
    @DisplayName("ตั้ง cooldown เป็น 0 ต้องปิดการกันได้")
    void zeroCooldownDisablesTheGuard() {
        ManualSyncGuard off = new ManualSyncGuard(0, 0);

        assertThat(off.claim(SCOPUS)).isZero();
        assertThat(off.claim(SCOPUS)).isZero();
        assertThat(off.claim(SCOPUS)).isZero();
    }
}
