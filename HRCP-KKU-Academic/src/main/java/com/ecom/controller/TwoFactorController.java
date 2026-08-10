package com.ecom.controller;

import java.util.Collection;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.TwoFactorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/2fa")
public class TwoFactorController {

    private static final int MAX_OTP_ATTEMPTS = 5;

    private final TwoFactorService twoFactorService;
    private final UserRepository userRepository;
    private final UserDetailsService userDetailsService;

    public TwoFactorController(
            TwoFactorService twoFactorService,
            UserRepository userRepository,
            UserDetailsService userDetailsService) {
        this.twoFactorService = twoFactorService;
        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
    }

    @GetMapping("/verify")
    public String showVerifyPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute("2FA_USER_EMAIL");
        if (email == null) {
            return "redirect:/signin";
        }
        model.addAttribute("maskedEmail", twoFactorService.maskEmail(email));
        return "guest/verify_2fa";
    }

    @PostMapping("/verify")
    public String verifyOtp(@RequestParam("otp") String otp,
                            HttpServletRequest request,
                            HttpSession session,
                            RedirectAttributes redirect) {
        String email = (String) session.getAttribute("2FA_USER_EMAIL");
        if (email == null) {
            return "redirect:/signin";
        }

        UserDtls user = userRepository.findByEmail(email);
        if (user == null) {
            session.removeAttribute("2FA_USER_EMAIL");
            session.removeAttribute("2FA_FAILED_ATTEMPTS");
            return "redirect:/signin";
        }

        Integer attempts = (Integer) session.getAttribute("2FA_FAILED_ATTEMPTS");
        if (attempts == null) {
            attempts = 0;
        }

        // Check if user has already exceeded max attempts
        if (attempts >= MAX_OTP_ATTEMPTS) {
            twoFactorService.clearOtp(user);
            session.removeAttribute("2FA_USER_EMAIL");
            session.removeAttribute("2FA_FAILED_ATTEMPTS");
            session.removeAttribute("2FA_REDIRECT");
            session.removeAttribute("2FA_RESEND_COOLDOWN");
            redirect.addFlashAttribute("error", "คุณกรอกรหัส OTP ไม่ถูกต้องเกินจำนวนครั้งที่กำหนด กรุณาเข้าสู่ระบบใหม่");
            return "redirect:/signin";
        }

        // Remove all spaces/dashes from OTP input
        String cleanOtp = otp.replaceAll("[\\s\\-]", "");

        String result = twoFactorService.verifyOtp(user, cleanOtp);

        switch (result) {
            case "OK":
                // Reset failed attempts on success
                session.removeAttribute("2FA_FAILED_ATTEMPTS");

                // Grant authentication
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Regenerate session
                request.changeSessionId();

                // Determine redirect URL
                String redirectUrl = (String) session.getAttribute("2FA_REDIRECT");
                session.removeAttribute("2FA_USER_EMAIL");
                session.removeAttribute("2FA_REDIRECT");
                session.removeAttribute("2FA_RESEND_COOLDOWN");

                if (redirectUrl == null) {
                    redirectUrl = getDefaultRedirect(userDetails.getAuthorities());
                }
                return "redirect:" + redirectUrl;

            case "EXPIRED":
                redirect.addFlashAttribute("error", "รหัส OTP หมดอายุแล้ว กรุณากดส่งรหัสใหม่");
                return "redirect:/2fa/verify";

            default:
                attempts++;
                session.setAttribute("2FA_FAILED_ATTEMPTS", attempts);
                int remaining = MAX_OTP_ATTEMPTS - attempts;

                if (remaining <= 0) {
                    twoFactorService.clearOtp(user);
                    session.removeAttribute("2FA_USER_EMAIL");
                    session.removeAttribute("2FA_FAILED_ATTEMPTS");
                    session.removeAttribute("2FA_REDIRECT");
                    session.removeAttribute("2FA_RESEND_COOLDOWN");
                    redirect.addFlashAttribute("error", "คุณกรอกรหัส OTP ไม่ถูกต้องเกิน 5 ครั้ง กรุณาเข้าสู่ระบบใหม่");
                    return "redirect:/signin";
                }

                redirect.addFlashAttribute("error", "รหัส OTP ไม่ถูกต้อง (เหลือโอกาสอีก " + remaining + " ครั้ง)");
                return "redirect:/2fa/verify";
        }
    }

    @PostMapping("/resend")
    public String resendOtp(HttpSession session, RedirectAttributes redirect) {
        String email = (String) session.getAttribute("2FA_USER_EMAIL");
        if (email == null) {
            return "redirect:/signin";
        }

        // Rate limit: 60 seconds
        Long lastSent = (Long) session.getAttribute("2FA_RESEND_COOLDOWN");
        if (lastSent != null && System.currentTimeMillis() - lastSent < 60_000) {
            redirect.addFlashAttribute("error", "กรุณารอ 60 วินาทีก่อนส่ง OTP อีกครั้ง");
            return "redirect:/2fa/verify";
        }

        UserDtls user = userRepository.findByEmail(email);
        if (user != null) {
            twoFactorService.generateOtp(user);
            twoFactorService.sendOtpEmail(user, "LOGIN");
            session.setAttribute("2FA_RESEND_COOLDOWN", System.currentTimeMillis());
            // Reset failed attempt counter on fresh OTP generation
            session.removeAttribute("2FA_FAILED_ATTEMPTS");
        }

        redirect.addFlashAttribute("success", "ส่งรหัส OTP ใหม่แล้ว กรุณาตรวจสอบอีเมล");
        return "redirect:/2fa/verify";
    }

    private String getDefaultRedirect(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority auth : authorities) {
            if ("ROLE_ADMIN".equals(auth.getAuthority())) {
                return "/admin/academic/requests";
            }
        }
        return "/user/academic/dashboard";
    }
}
