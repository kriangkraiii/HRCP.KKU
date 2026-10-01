package com.ecom.controller;

import java.security.Principal;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.DevAccountSwitchService;
import com.ecom.service.SignInService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Dev mode only: switches the signed-in session to a test account without a
 * password. The rules live in {@link DevAccountSwitchService}.
 */
@Controller
public class DevAccountSwitchController {

    private static final Logger log = LoggerFactory.getLogger(DevAccountSwitchController.class);

    private final DevAccountSwitchService switcher;
    private final SignInService signInService;
    private final UserRepository userRepository;

    public DevAccountSwitchController(DevAccountSwitchService switcher, SignInService signInService,
            UserRepository userRepository) {
        this.switcher = switcher;
        this.signInService = signInService;
        this.userRepository = userRepository;
    }

    @PostMapping("/dev/switch-account")
    public String switchAccount(@RequestParam String email, Principal principal,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {

        HttpSession session = request.getSession();
        UserDtls current = principal == null ? null : userRepository.findByEmail(principal.getName());

        // Outside dev mode, or for anyone who is not entitled to it, the endpoint
        // does not exist as far as the caller can tell.
        if (current == null || !switcher.isAvailable(current, session)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Optional<UserDtls> target = switcher.resolveTarget(current, session, email);
        if (target.isEmpty()) {
            redirect.addFlashAttribute("errorMsg", "สลับบัญชีไม่ได้: " + email + " ไม่ใช่บัญชีทดสอบที่ใช้งานได้");
            return "redirect:" + signInService.landingPageFor(current);
        }

        UserDtls next = target.get();
        log.info("Dev account switch: {} -> {}", current.getEmail(), next.getEmail());
        switcher.rememberOrigin(session, current, next);
        String landing = signInService.completeSignIn(request, response, next,
                SignInService.Method.DEV_SWITCH, null);

        redirect.addFlashAttribute("succMsg", "สลับเป็นบัญชี " + next.getEmail() + " แล้ว");
        return "redirect:" + landing;
    }
}
