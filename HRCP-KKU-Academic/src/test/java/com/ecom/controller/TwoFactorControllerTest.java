package com.ecom.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.SignInService;
import com.ecom.service.TwoFactorService;

/**
 * The OTP screen stands between a proven first factor and a session, and it is
 * reachable without being signed in — so what it refuses matters as much as what
 * it accepts. These tests cover the refusals, and that a KKU SSO sign-in is
 * resumed as an SSO sign-in rather than quietly demoted to a password one.
 */
class TwoFactorControllerTest {

    private static final String EMAIL = "somchai@kku.ac.th";

    private final TwoFactorService twoFactorService = mock(TwoFactorService.class);
    private final SignInService signInService = mock(SignInService.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private final TwoFactorController controller =
            new TwoFactorController(twoFactorService, signInService, userRepository);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockHttpSession session = new MockHttpSession();
    private final RedirectAttributes redirect = new RedirectAttributesModelMap();

    private UserDtls user;

    @BeforeEach
    void setUp() {
        user = new UserDtls();
        user.setEmail(EMAIL);
        user.setName("สมชาย");
        user.setRole("ROLE_USER");
        user.setIsEnable(true);
        user.setTwoFactorEnabled(true);

        when(userRepository.findByEmail(EMAIL)).thenReturn(user);
        when(signInService.pendingMethod(any())).thenReturn(SignInService.Method.PASSWORD);
        when(signInService.isLocallyUsable(any())).thenReturn(true);
    }

    /** Marks a sign-in as parked, the way SignInService.startTwoFactor would. */
    private void parkSignIn() {
        session.setAttribute(SignInService.SESSION_PENDING_EMAIL, EMAIL);
    }

    @Test
    @DisplayName("ไม่มีการล็อกอินค้างอยู่ ต้องเข้าหน้า OTP ไม่ได้เลย")
    void withoutAParkedSignInEveryEntryPointBouncesToSignin() {
        Model model = new ExtendedModelMap();

        assertThat(controller.showVerifyPage(session, model)).isEqualTo("redirect:/signin");
        assertThat(controller.verifyOtp("12345678", request, response, session, redirect))
                .isEqualTo("redirect:/signin");
        assertThat(controller.resendOtp(request, session, redirect)).isEqualTo("redirect:/signin");

        // Nothing may be verified or sent on the strength of a URL alone.
        verify(twoFactorService, never()).verifyOtp(any(), anyString());
        verify(twoFactorService, never()).generateOtp(any());
    }

    @Test
    @DisplayName("OTP ถูกต้อง ต้องสานการล็อกอิน SSO ต่อด้วย token เดิม ไม่ใช่กลายเป็นล็อกอินรหัสผ่าน")
    void aCorrectCodeResumesTheSignInThroughTheDoorItStarted() {
        parkSignIn();
        when(twoFactorService.verifyOtp(user, "12345678")).thenReturn("OK");
        when(signInService.pendingMethod(any())).thenReturn(SignInService.Method.SSO);
        when(signInService.pendingSsoToken(any())).thenReturn("sso-access-token");
        when(signInService.completeSignIn(any(), any(), any(), any(), any()))
                .thenReturn("/user/academic/dashboard");

        String view = controller.verifyOtp("1234-5678", request, response, session, redirect);

        assertThat(view).isEqualTo("redirect:/user/academic/dashboard");
        verify(signInService).completeSignIn(any(), any(), eq(user),
                eq(SignInService.Method.SSO), eq("sso-access-token"));
    }

    @Test
    @DisplayName("OTP ผิด ต้องนับครั้งและยังไม่ให้เข้า")
    void aWrongCodeCountsAgainstTheAttemptBudget() {
        parkSignIn();
        when(twoFactorService.verifyOtp(any(), anyString())).thenReturn("INVALID");

        String view = controller.verifyOtp("00000000", request, response, session, redirect);

        assertThat(view).isEqualTo("redirect:/2fa/verify");
        assertThat(session.getAttribute(SignInService.SESSION_ATTEMPTS)).isEqualTo(1);
        verify(signInService, never()).completeSignIn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ผิดครบ 5 ครั้ง ต้องล้างรหัสและเริ่มใหม่ที่หน้าเข้าสู่ระบบ")
    void exhaustingTheAttemptsThrowsTheSignInAway() {
        parkSignIn();
        session.setAttribute(SignInService.SESSION_ATTEMPTS, 4);
        when(twoFactorService.verifyOtp(any(), anyString())).thenReturn("INVALID");

        String view = controller.verifyOtp("00000000", request, response, session, redirect);

        assertThat(view).isEqualTo("redirect:/signin");
        // The live code must not survive to be reused by the next attempt.
        verify(twoFactorService).clearOtp(user);
        verify(signInService).abandonTwoFactor(session);
        assertThat(redirect.getFlashAttributes()).containsKey("errorMsg");
    }

    @Test
    @DisplayName("OTP หมดอายุ ต้องกลับไปหน้าเดิมให้ขอรหัสใหม่ ไม่ใช่ทิ้งการล็อกอิน")
    void anExpiredCodeKeepsTheSignInParked() {
        parkSignIn();
        when(twoFactorService.verifyOtp(any(), anyString())).thenReturn("EXPIRED");

        String view = controller.verifyOtp("12345678", request, response, session, redirect);

        assertThat(view).isEqualTo("redirect:/2fa/verify");
        verify(signInService, never()).abandonTwoFactor(any());
    }

    @Test
    @DisplayName("บัญชีถูกปิดใช้งานระหว่างรอ OTP ต้องเข้าไม่ได้ แม้กรอกรหัสถูก")
    void anAccountDeactivatedMidChallengeStillCannotEnter() {
        parkSignIn();
        when(twoFactorService.verifyOtp(any(), anyString())).thenReturn("OK");
        when(signInService.isLocallyUsable(user)).thenReturn(false);

        String view = controller.verifyOtp("12345678", request, response, session, redirect);

        assertThat(view).isEqualTo("redirect:/signin");
        verify(signInService, never()).completeSignIn(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("กดขอ OTP ใหม่รัว ๆ ต้องไม่ยิงเมลซ้ำภายใน 60 วินาที")
    void resendIsRateLimitedWithinTheCooldown() {
        parkSignIn();
        session.setAttribute(SignInService.SESSION_RESEND_COOLDOWN, System.currentTimeMillis());

        String view = controller.resendOtp(request, session, redirect);

        assertThat(view).isEqualTo("redirect:/2fa/verify");
        verify(twoFactorService, never()).generateOtp(any());
        verify(twoFactorService, never()).sendOtpEmail(any(), anyString());
    }

    @Test
    @DisplayName("พ้นเวลารอแล้ว ขอ OTP ใหม่ได้ และโควตาการกรอกผิดต้องรีเซ็ต")
    void resendAfterTheCooldownIssuesAFreshCodeAndBudget() {
        parkSignIn();
        session.setAttribute(SignInService.SESSION_RESEND_COOLDOWN, System.currentTimeMillis() - 61_000);
        session.setAttribute(SignInService.SESSION_ATTEMPTS, 3);

        controller.resendOtp(request, session, redirect);

        verify(twoFactorService).generateOtp(user);
        verify(twoFactorService).sendOtpEmail(user, "LOGIN");
        assertThat(session.getAttribute(SignInService.SESSION_ATTEMPTS)).isNull();
    }
}
