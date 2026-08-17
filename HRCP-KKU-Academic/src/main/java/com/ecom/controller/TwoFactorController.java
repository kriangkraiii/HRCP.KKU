package com.ecom.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.SignInService;
import com.ecom.service.TwoFactorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The one-time-code screen, shared by both ways into the system.
 *
 * <p>It knows nothing about how the first factor was proved — a password form or
 * KKU SSO — only that {@link SignInService} parked a sign-in in the session. On
 * success the sign-in is resumed through the same service, so an SSO login keeps
 * its provider token and lands exactly where it would have without the detour.
 */
@Controller
@RequestMapping("/2fa")
public class TwoFactorController {

    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final long RESEND_COOLDOWN_MILLIS = 60_000;

    private final TwoFactorService twoFactorService;
    private final SignInService signInService;
    private final UserRepository userRepository;

    public TwoFactorController(
            TwoFactorService twoFactorService,
            SignInService signInService,
            UserRepository userRepository) {
        this.twoFactorService = twoFactorService;
        this.signInService = signInService;
        this.userRepository = userRepository;
    }

    @GetMapping("/verify")
    public String showVerifyPage(HttpSession session, Model model) {
        String email = pendingEmail(session);
        if (email == null) {
            return "redirect:/signin";
        }
        model.addAttribute("maskedEmail", twoFactorService.maskEmail(email));
        model.addAttribute("loginMethod", signInService.pendingMethod(session).name());
        return "guest/verify_2fa";
    }

    @PostMapping("/verify")
    public String verifyOtp(@RequestParam("otp") String otp,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            HttpSession session,
                            RedirectAttributes redirect) {
        String email = pendingEmail(session);
        if (email == null) {
            return "redirect:/signin";
        }

        UserDtls user = userRepository.findByEmail(email);
        if (user == null) {
            signInService.abandonTwoFactor(session);
            return "redirect:/signin";
        }

        Integer attempts = (Integer) session.getAttribute(SignInService.SESSION_ATTEMPTS);
        if (attempts == null) {
            attempts = 0;
        }

        // Check if user has already exceeded max attempts
        if (attempts >= MAX_OTP_ATTEMPTS) {
            return startOver(user, session, redirect,
                    "คุณกรอกรหัส OTP ไม่ถูกต้องเกินจำนวนครั้งที่กำหนด กรุณาเข้าสู่ระบบใหม่");
        }

        // Remove all spaces/dashes from OTP input
        String cleanOtp = otp.replaceAll("[\\s\\-]", "");

        String result = twoFactorService.verifyOtp(user, cleanOtp);

        switch (result) {
            case "OK":
                // The account could have been deactivated or locked while the code
                // was in transit; the password chain re-checks on every attempt and
                // this path has to as well.
                if (!signInService.isLocallyUsable(user)) {
                    return startOver(user, session, redirect,
                            "บัญชีนี้ถูกปิดการใช้งาน กรุณาติดต่อผู้ดูแลระบบ");
                }

                SignInService.Method method = signInService.pendingMethod(session);
                String ssoToken = signInService.pendingSsoToken(session);

                String landing = signInService.completeSignIn(request, response, user, method, ssoToken);
                return "redirect:" + landing;

            case "EXPIRED":
                redirect.addFlashAttribute("error", "รหัส OTP หมดอายุแล้ว กรุณากดส่งรหัสใหม่");
                return "redirect:" + SignInService.VERIFY_PATH;

            default:
                attempts++;
                session.setAttribute(SignInService.SESSION_ATTEMPTS, attempts);
                int remaining = MAX_OTP_ATTEMPTS - attempts;

                if (remaining <= 0) {
                    return startOver(user, session, redirect,
                            "คุณกรอกรหัส OTP ไม่ถูกต้องเกิน " + MAX_OTP_ATTEMPTS + " ครั้ง กรุณาเข้าสู่ระบบใหม่");
                }

                redirect.addFlashAttribute("error", "รหัส OTP ไม่ถูกต้อง (เหลือโอกาสอีก " + remaining + " ครั้ง)");
                return "redirect:" + SignInService.VERIFY_PATH;
        }
    }

    @PostMapping("/resend")
    public String resendOtp(HttpServletRequest request, HttpSession session, RedirectAttributes redirect) {
        String email = pendingEmail(session);
        if (email == null) {
            return "redirect:/signin";
        }

        Long lastSent = (Long) session.getAttribute(SignInService.SESSION_RESEND_COOLDOWN);
        if (lastSent != null && System.currentTimeMillis() - lastSent < RESEND_COOLDOWN_MILLIS) {
            redirect.addFlashAttribute("error", "กรุณารอ 60 วินาทีก่อนส่ง OTP อีกครั้ง");
            return "redirect:" + SignInService.VERIFY_PATH;
        }

        UserDtls user = userRepository.findByEmail(email);
        if (user != null) {
            twoFactorService.generateOtp(user);
            twoFactorService.sendOtpEmail(user, "LOGIN");
            session.setAttribute(SignInService.SESSION_RESEND_COOLDOWN, System.currentTimeMillis());
            // Reset failed attempt counter on fresh OTP generation
            session.removeAttribute(SignInService.SESSION_ATTEMPTS);
        }

        redirect.addFlashAttribute("success", "ส่งรหัส OTP ใหม่แล้ว กรุณาตรวจสอบอีเมล");
        return "redirect:" + SignInService.VERIFY_PATH;
    }

    private String pendingEmail(HttpSession session) {
        return (String) session.getAttribute(SignInService.SESSION_PENDING_EMAIL);
    }

    /**
     * Abandons the challenge and sends the person back to the start.
     *
     * <p>The stored code is cleared as well: leaving a live OTP behind would let a
     * fresh attempt reuse the one that just ran out of tries.
     */
    private String startOver(UserDtls user, HttpSession session,
            RedirectAttributes redirect, String message) {
        twoFactorService.clearOtp(user);
        signInService.abandonTwoFactor(session);
        // The sign-in page renders "errorMsg"; a flash named "error" would be
        // dropped silently and leave the person guessing why they are back here.
        redirect.addFlashAttribute("errorMsg", message);
        return "redirect:/signin";
    }
}
