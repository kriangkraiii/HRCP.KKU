package com.ecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.ecom.model.UserDtls;

import jakarta.servlet.http.HttpSession;

/**
 * The second factor has to mean the same thing whichever door someone came
 * through. These tests pin that: a KKU SSO sign-in is a first factor and nothing
 * more, so an account with 2FA switched on gets no session until the code is in.
 */
class SignInServiceTest {

    private final TwoFactorService twoFactorService = mock(TwoFactorService.class);
    private final AdminLogService adminLogService = mock(AdminLogService.class);
    private final SignInService signInService = new SignInService(twoFactorService, adminLogService);

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserDtls user(String role, boolean twoFactor) {
        UserDtls u = new UserDtls();
        u.setEmail("somchai@kku.ac.th");
        u.setName("สมชาย");
        u.setRole(role);
        u.setIsEnable(true);
        u.setAccountNonLocked(true);
        u.setTwoFactorEnabled(twoFactor);
        return u;
    }

    @Test
    @DisplayName("บัญชีที่เปิด 2FA ต้องถูกถาม OTP ไม่ว่าจะเข้าทางไหน")
    void twoFactorIsOwedRegardlessOfHowTheSignInStarted() {
        assertThat(signInService.requiresTwoFactor(user("ROLE_USER", true))).isTrue();
        assertThat(signInService.requiresTwoFactor(user("ROLE_USER", false))).isFalse();

        // General admin accounts must always require 2FA
        UserDtls generalAdmin = user("ROLE_ADMIN", false);
        assertThat(signInService.requiresTwoFactor(generalAdmin)).isTrue();

        // admin@admin.com test account is exempt from mandatory 2FA unless enabled
        UserDtls testAdmin = user("ROLE_ADMIN", false);
        testAdmin.setEmail("admin@admin.com");
        assertThat(signInService.requiresTwoFactor(testAdmin)).isFalse();

        testAdmin.setTwoFactorEnabled(true);
        assertThat(signInService.requiresTwoFactor(testAdmin)).isTrue();
    }

    @Test
    @DisplayName("พัก SSO ไว้ที่ขั้น OTP ต้องยังไม่ได้ session และ token ต้องไม่หาย")
    void parkedSsoSignInHoldsTheTokenAndGrantsNoSession() {
        UserDtls u = user("ROLE_USER", true);

        signInService.startTwoFactor(request, u, SignInService.Method.SSO, "sso-access-token");

        HttpSession session = request.getSession();
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_EMAIL)).isEqualTo(u.getEmail());
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_METHOD)).isEqualTo("SSO");
        assertThat(signInService.pendingSsoToken(session)).isEqualTo("sso-access-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("ยืนยันตัวตนขั้นแรกผ่านแล้ว แต่ยังไม่ถือว่าเข้าสู่ระบบ")
                .isNull();
        // Half a sign-in is not a sign-in, and the audit trail must not claim one.
        verify(adminLogService, never()).logWithDetails(any(), any(), any(), any(), any(), any(), any());
        verify(twoFactorService).generateOtp(u);
        verify(twoFactorService).sendOtpEmail(org.mockito.ArgumentMatchers.eq(u), any(), org.mockito.ArgumentMatchers.eq("LOGIN"));
    }

    @Test
    @DisplayName("พักไว้ที่ขั้น OTP ต้องไม่มี security context ค้างในเซสชัน")
    void parkingTheSignInStripsTheAuthenticatedContextFromTheSession() {
        UserDtls u = user("ROLE_USER", true);
        // What the form-login filter leaves behind before the success handler runs.
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(u.getEmail(), null)));

        signInService.startTwoFactor(request, u, SignInService.Method.PASSWORD, null);

        assertThat(request.getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .as("ถ้าปล่อยค้างไว้ ผู้ใช้พิมพ์ URL ตรงก็ข้ามหน้า OTP ได้เลย")
                .isNull();
    }

    @Test
    @DisplayName("ผ่าน OTP แล้ว ต้องได้ session พร้อม token ของ SSO และล้างสถานะที่พักไว้")
    void completingAnSsoChallengePromotesTheTokenIntoTheSession() {
        UserDtls u = user("ROLE_USER", true);
        signInService.startTwoFactor(request, u, SignInService.Method.SSO, "sso-access-token");

        String landing = signInService.completeSignIn(request, response, u,
                SignInService.Method.SSO, signInService.pendingSsoToken(request.getSession()));

        assertThat(landing).isEqualTo("/user/academic/dashboard");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        HttpSession session = request.getSession();
        assertThat(session.getAttribute(SignInService.SESSION_SSO_ACCESS_TOKEN)).isEqualTo("sso-access-token");
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_EMAIL)).isNull();
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_SSO_TOKEN))
                .as("token ที่พักไว้ต้องไม่ค้างอยู่หลังใช้เสร็จ")
                .isNull();
    }

    @Test
    @DisplayName("ล็อกอินด้วยรหัสผ่านต้องไม่ทิ้ง token ของ SSO ไว้ในเซสชัน")
    void passwordSignInLeavesNoSsoToken() {
        UserDtls u = user("ROLE_ADMIN", false);

        String landing = signInService.completeSignIn(request, response, u,
                SignInService.Method.PASSWORD, null);

        assertThat(landing).isEqualTo("/admin/academic/requests");
        assertThat(request.getSession().getAttribute(SignInService.SESSION_SSO_ACCESS_TOKEN)).isNull();
    }

    @Test
    @DisplayName("บัญชีที่ถูกปิดใช้งาน ต้องไม่ผ่านด่านนี้")
    void deactivatedAccountIsNotUsable() {
        UserDtls u = user("ROLE_USER", false);
        u.setIsEnable(false);

        assertThat(signInService.isLocallyUsable(u)).isFalse();
        assertThat(signInService.isLocallyUsable(null)).isFalse();
    }

    @Test
    @DisplayName("การล็อกชั่วคราวจากการเดารหัสผ่าน ต้องไม่ปิดกั้นการเข้าผ่าน SSO")
    void temporaryPasswordLockDoesNotBlockSso() {
        UserDtls u = user("ROLE_USER", false);
        u.setAccountNonLocked(false);

        assertThat(signInService.isLocallyUsable(u))
                .as("SSO ไม่ได้ใช้รหัสผ่านในระบบ การถูกล็อกจากการเดารหัสจึงไม่เกี่ยว")
                .isTrue();
    }

    @Test
    @DisplayName("ยกเลิกการยืนยัน ต้องล้างทุกอย่างที่พักไว้")
    void abandoningTheChallengeClearsEverything() {
        UserDtls u = user("ROLE_USER", true);
        signInService.startTwoFactor(request, u, SignInService.Method.SSO, "sso-access-token");
        HttpSession session = request.getSession();
        session.setAttribute(SignInService.SESSION_ATTEMPTS, 3);

        signInService.abandonTwoFactor(session);

        assertThat(session.getAttribute(SignInService.SESSION_PENDING_EMAIL)).isNull();
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_METHOD)).isNull();
        assertThat(session.getAttribute(SignInService.SESSION_PENDING_SSO_TOKEN)).isNull();
        assertThat(session.getAttribute(SignInService.SESSION_ATTEMPTS)).isNull();
    }
}
