package com.ecom.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import java.util.Date;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthFailureHandlerImpl Tests")
class AuthFailureHandlerTest {

    @Mock
    private BruteForceProtection bruteForceProtection;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AuthFailureHandlerImpl handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new AuthFailureHandlerImpl(bruteForceProtection, userService, eventPublisher);
        request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        response = new MockHttpServletResponse();
    }

    // ── Bug Fix B1: Stale user object ──────────────────────────────────

    @Test
    @DisplayName("B1: ใส่ผิดครั้งที่ 5 → ต้องล็อกบัญชี (ไม่ช้าไป 1 ครั้ง)")
    void fifthAttempt_shouldLockAccount() throws Exception {
        request.setParameter("email", "test@kku.ac.th");
        UserDtls user = createUser(4, true, true);
        when(userService.getUserByEmail("test@kku.ac.th")).thenReturn(user);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        verify(userService).increaseFailedAttempt(user);
        verify(userService).userAccountLock(user);
    }

    // ── Bug Fix B2: Duplicate query ────────────────────────────────────

    @Test
    @DisplayName("B2: getUserByEmail ถูกเรียกครั้งเดียวเมื่อบัญชีไม่ถูกล็อก")
    void singleDbQueryForNormalFailure() throws Exception {
        request.setParameter("email", "test@kku.ac.th");
        UserDtls user = createUser(0, true, true);
        when(userService.getUserByEmail("test@kku.ac.th")).thenReturn(user);
        when(bruteForceProtection.getRemainingAttempts("user:test@kku.ac.th")).thenReturn(4);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        verify(userService, times(1)).getUserByEmail("test@kku.ac.th");
    }

    // ── Bug Fix B3: NPE when failedAttempt is null ─────────────────────

    @Test
    @DisplayName("B3: failedAttempt=null → ไม่เกิด NPE")
    void nullFailedAttempt_shouldNotThrowNPE() throws Exception {
        request.setParameter("email", "test@kku.ac.th");
        UserDtls user = createUser(null, true, true);
        when(userService.getUserByEmail("test@kku.ac.th")).thenReturn(user);
        when(bruteForceProtection.getRemainingAttempts("user:test@kku.ac.th")).thenReturn(4);

        assertDoesNotThrow(() ->
                handler.onAuthenticationFailure(request, response,
                        new BadCredentialsException("Bad credentials")));
    }

    // ── Normal failure scenarios ───────────────────────────────────────

    @Test
    @DisplayName("ความผิดพลาดปกติ → แสดงจำนวนครั้ง")
    void normalFailure_shouldShowAttemptCount() throws Exception {
        request.setParameter("email", "test@kku.ac.th");
        UserDtls user = createUser(1, true, true);
        when(userService.getUserByEmail("test@kku.ac.th")).thenReturn(user);
        when(bruteForceProtection.getRemainingAttempts("user:test@kku.ac.th")).thenReturn(3);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        String msg = (String) request.getSession().getAttribute("errorMessage");
        assertTrue(msg.contains("ครั้งที่ 2/5"), "ต้องแสดง ครั้งที่ 2/5 แต่ได้: " + msg);
    }

    // ── Banned account ─────────────────────────────────────────────────

    @Test
    @DisplayName("บัญชีถูกแบน → แสดงข้อความติดต่อผู้ดูแล")
    void bannedAccount_shouldShowBanMessage() throws Exception {
        request.setParameter("email", "banned@kku.ac.th");
        UserDtls user = createUser(5, false, false);
        when(userService.getUserByEmail("banned@kku.ac.th")).thenReturn(user);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        String msg = (String) request.getSession().getAttribute("errorMessage");
        assertTrue(msg.contains("ระงับถาวร"), "ต้องแสดงข้อความแบน");
        assertTrue(msg.contains("ติดต่อผู้ดูแลระบบ"));
    }

    // ── IP block ───────────────────────────────────────────────────────

    @Test
    @DisplayName("IP ถูกบล็อก → แสดง countdown")
    void ipBlocked_shouldSetBlockedUntil() throws Exception {
        request.setParameter("email", "test@kku.ac.th");
        UserDtls user = createUser(5, true, true);
        when(userService.getUserByEmail("test@kku.ac.th")).thenReturn(user);
        when(bruteForceProtection.isBlocked("ip:127.0.0.1")).thenReturn(true);
        when(bruteForceProtection.getBlockDurationMinutes("ip:127.0.0.1")).thenReturn(15L);
        long until = System.currentTimeMillis() + 15 * 60_000;
        when(bruteForceProtection.getBlockedUntilMillis("ip:127.0.0.1")).thenReturn(until);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        assertNotNull(request.getSession().getAttribute("blockedUntilMillis"));
    }

    // ── LockedException from DB ────────────────────────────────────────

    @Test
    @DisplayName("LockedException → แสดงข้อความล็อกพร้อม countdown")
    void lockedException_shouldShowLockMessage() throws Exception {
        request.setParameter("email", "locked@kku.ac.th");
        UserDtls user = createUser(5, true, false);
        when(userService.getUserByEmail("locked@kku.ac.th")).thenReturn(user);

        UserDtls lockedUser = createUser(5, true, false);
        lockedUser.setLockTime(new Date());
        // Second call for resolveLockedMessage
        when(userService.getUserByEmail("locked@kku.ac.th")).thenReturn(user, lockedUser);
        when(bruteForceProtection.getBlockDurationMinutes("user:locked@kku.ac.th")).thenReturn(30L);

        handler.onAuthenticationFailure(request, response,
                new LockedException("Account locked"));

        String msg = (String) request.getSession().getAttribute("errorMessage");
        assertTrue(msg.contains("ระงับชั่วคราว"), "ต้องแสดงข้อความระงับชั่วคราว");
    }

    // ── Email sanitization ─────────────────────────────────────────────

    @Test
    @DisplayName("Sanitize: CRLF injection → ถูกลบออก")
    void sanitizeEmail_removesNewlines() {
        assertEquals("evil@test.com", AuthFailureHandlerImpl.sanitizeEmail("evil@test.com\r\nInjected-Header: true"));
    }

    @Test
    @DisplayName("Sanitize: blank input → null")
    void sanitizeEmail_blankReturnsNull() {
        assertNull(AuthFailureHandlerImpl.sanitizeEmail("   "));
        assertNull(AuthFailureHandlerImpl.sanitizeEmail(null));
    }

    @Test
    @DisplayName("Sanitize: uppercase → lowercase")
    void sanitizeEmail_lowercased() {
        assertEquals("test@kku.ac.th", AuthFailureHandlerImpl.sanitizeEmail("Test@KKU.AC.TH"));
    }

    // ── No email provided ──────────────────────────────────────────────

    @Test
    @DisplayName("ไม่ส่ง email → ใช้ IP เพื่อนับครั้ง")
    void noEmail_shouldUseIpForAttemptCount() throws Exception {
        when(bruteForceProtection.getRemainingAttempts("ip:127.0.0.1")).thenReturn(4);

        handler.onAuthenticationFailure(request, response,
                new BadCredentialsException("Bad credentials"));

        String msg = (String) request.getSession().getAttribute("errorMessage");
        assertNotNull(msg);
        assertTrue(msg.contains("ครั้งที่ 1/5"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private UserDtls createUser(Integer failedAttempt, boolean enabled, boolean nonLocked) {
        UserDtls user = new UserDtls();
        user.setId(1);
        user.setEmail("test@kku.ac.th");
        user.setFailedAttempt(failedAttempt);
        user.setIsEnable(enabled);
        user.setAccountNonLocked(nonLocked);
        return user;
    }
}
