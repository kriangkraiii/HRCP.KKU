package com.ecom.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.impl.UserServiceImpl;
import com.ecom.util.AppConstant;

@ExtendWith(MockitoExtension.class)
@DisplayName("Account Lock/Unlock Tests (Progressive Lockout)")
class AccountLockTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private UserDtls user;

    @BeforeEach
    void setUp() {
        user = new UserDtls();
        user.setId(1);
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setAccountNonLocked(true);
        user.setFailedAttempt(0);
        user.setLockTime(null);
        user.setIsEnable(true);
    }

    // =====================================================================
    // increaseFailedAttempt Tests
    // =====================================================================

    @Test
    @DisplayName("ใส่รหัสผิด 1 ครั้ง → failedAttempt เพิ่มเป็น 1")
    void increaseFailedAttempt_firstAttempt_shouldBeOne() {
        user.setFailedAttempt(0);
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        userService.increaseFailedAttempt(user);

        assertEquals(1, user.getFailedAttempt());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("ใส่รหัสผิด 4 ครั้ง → failedAttempt เป็น 4 (ยังไม่ถึงเกณฑ์)")
    void increaseFailedAttempt_fourthAttempt_shouldBeFour() {
        user.setFailedAttempt(3);
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        userService.increaseFailedAttempt(user);

        assertEquals(4, user.getFailedAttempt());
    }

    @Test
    @DisplayName("ใส่รหัสผิดครั้งที่ 5 → failedAttempt เป็น 5 (ถึงเกณฑ์ล็อก)")
    void increaseFailedAttempt_fifthAttempt_shouldReachLockThreshold() {
        user.setFailedAttempt(4);
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        userService.increaseFailedAttempt(user);

        assertEquals(5, user.getFailedAttempt());
        assertEquals((int) AppConstant.ATTEMPT_TIME, user.getFailedAttempt());
    }

    // =====================================================================
    // userAccountLock Tests
    // =====================================================================

    @Test
    @DisplayName("ล็อกบัญชี → accountNonLocked = false, lockTime ไม่เป็น null")
    void userAccountLock_shouldLockAccount() {
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        userService.userAccountLock(user);

        assertFalse(user.getAccountNonLocked());
        assertNotNull(user.getLockTime());
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("ล็อกบัญชี → lockTime ต้องเป็นเวลาปัจจุบัน (±2 วินาที)")
    void userAccountLock_lockTimeShouldBeNow() {
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);
        long before = System.currentTimeMillis();

        userService.userAccountLock(user);

        long after = System.currentTimeMillis();
        long lockTimeMs = user.getLockTime().getTime();
        assertTrue(lockTimeMs >= before && lockTimeMs <= after,
                "lockTime ควรอยู่ระหว่างเวลาก่อนและหลังเรียก lock");
    }

    // =====================================================================
    // unlockAccountTimeExpired Tests
    // =====================================================================

    @Test
    @DisplayName("ยังไม่ครบ 15 นาที → ไม่ปลดล็อก (return false)")
    void unlockAccountTimeExpired_notYetExpired_shouldReturnFalse() {
        user.setAccountNonLocked(false);
        user.setFailedAttempt(5);
        user.setLockTime(new Date(System.currentTimeMillis() - 5 * 60 * 1000));

        boolean result = userService.unlockAccountTimeExpired(user);

        assertFalse(result, "ยังไม่ครบ 15 นาที ต้อง return false");
        assertFalse(user.getAccountNonLocked(), "บัญชีต้องยังล็อกอยู่");
        assertEquals(5, user.getFailedAttempt(), "failedAttempt ต้องยังเป็น 5");
    }

    @Test
    @DisplayName("ครบ 15 นาทีแล้ว → ปลดล็อก (return true)")
    void unlockAccountTimeExpired_expired_shouldReturnTrue() {
        user.setAccountNonLocked(false);
        user.setFailedAttempt(5);
        user.setLockTime(new Date(System.currentTimeMillis() - 20 * 60 * 1000));

        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        boolean result = userService.unlockAccountTimeExpired(user);

        assertTrue(result, "เกิน 15 นาทีแล้ว ต้อง return true (ปลดล็อก)");
        assertTrue(user.getAccountNonLocked(), "บัญชีต้องถูกปลดล็อก");
        assertEquals(0, user.getFailedAttempt(), "failedAttempt ต้อง reset เป็น 0");
        assertNull(user.getLockTime(), "lockTime ต้อง reset เป็น null");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("ล็อกพอดี 15 นาที → ปลดล็อก (boundary)")
    void unlockAccountTimeExpired_exactlyAtBoundary_shouldUnlock() {
        long lockDuration = AppConstant.UNLOCK_DURATION_TIME + 1;
        user.setAccountNonLocked(false);
        user.setFailedAttempt(5);
        user.setLockTime(new Date(System.currentTimeMillis() - lockDuration));

        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        boolean result = userService.unlockAccountTimeExpired(user);

        assertTrue(result, "เกิน 15 นาทีพอดี ต้องปลดล็อก");
        assertTrue(user.getAccountNonLocked());
    }

    @Test
    @DisplayName("ล็อกครบ 14 นาที 59 วินาที → ยังไม่ปลดล็อก (boundary)")
    void unlockAccountTimeExpired_justBeforeBoundary_shouldNotUnlock() {
        long lockDuration = AppConstant.UNLOCK_DURATION_TIME - 1000;
        user.setAccountNonLocked(false);
        user.setFailedAttempt(5);
        user.setLockTime(new Date(System.currentTimeMillis() - lockDuration));

        boolean result = userService.unlockAccountTimeExpired(user);

        assertFalse(result, "ยังไม่ครบ 15 นาที ต้องไม่ปลดล็อก");
        assertFalse(user.getAccountNonLocked());
    }

    // =====================================================================
    // updateAccountStatus Tests (Admin manual block/unblock)
    // =====================================================================

    @Test
    @DisplayName("Admin บล็อกบัญชี → isEnable = false")
    void updateAccountStatus_disable_shouldBlockAccount() {
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        Boolean result = userService.updateAccountStatus(1, false);

        assertTrue(result);
        ArgumentCaptor<UserDtls> captor = ArgumentCaptor.forClass(UserDtls.class);
        verify(userRepository).save(captor.capture());
        assertFalse(captor.getValue().getIsEnable());
    }

    @Test
    @DisplayName("Admin ปลดบล็อกบัญชี → isEnable = true")
    void updateAccountStatus_enable_shouldUnblockAccount() {
        user.setIsEnable(false);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserDtls.class))).thenReturn(user);

        Boolean result = userService.updateAccountStatus(1, true);

        assertTrue(result);
        ArgumentCaptor<UserDtls> captor = ArgumentCaptor.forClass(UserDtls.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().getIsEnable());
    }

    @Test
    @DisplayName("บล็อกบัญชีที่ไม่มีอยู่ → return false")
    void updateAccountStatus_userNotFound_shouldReturnFalse() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        Boolean result = userService.updateAccountStatus(999, false);

        assertFalse(result);
        verify(userRepository, never()).save(any());
    }

    // =====================================================================
    // AppConstant Verification
    // =====================================================================

    @Test
    @DisplayName("UNLOCK_DURATION_TIME ต้องเป็น 15 นาที (900,000 ms)")
    void unlockDuration_shouldBe15Minutes() {
        assertEquals(15 * 60 * 1000, AppConstant.UNLOCK_DURATION_TIME,
                "Lock duration ต้องเป็น 15 นาที");
    }

    @Test
    @DisplayName("ATTEMPT_TIME ต้องเป็น 5 ครั้ง")
    void attemptTime_shouldBeFive() {
        assertEquals(5, AppConstant.ATTEMPT_TIME,
                "จำนวนครั้งที่ผิดก่อนล็อก ต้องเป็น 5");
    }
}
