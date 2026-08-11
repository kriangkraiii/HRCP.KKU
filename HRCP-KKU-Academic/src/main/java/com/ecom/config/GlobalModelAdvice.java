package com.ecom.config;

import java.security.Principal;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.ecom.model.UserDtls;
import com.ecom.service.UserService;

import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalModelAdvice {

    private final UserService userService;

    public GlobalModelAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute
    public void addGlobalAttributes(Principal principal, HttpSession session, Model model) {
        // Add logged-in user to model for ALL controllers
        if (principal != null) {
            UserDtls user = userService.getUserByEmail(principal.getName());
            if (user != null) {
                model.addAttribute("user", user);
            }
        }

        // Transfer session messages to model and clear them
        String succMsg = (String) session.getAttribute("succMsg");
        String errorMsg = (String) session.getAttribute("errorMsg");

        if (succMsg != null) {
            model.addAttribute("succMsg", succMsg);
            session.removeAttribute("succMsg");
        }
        if (errorMsg != null) {
            model.addAttribute("errorMsg", errorMsg);
            session.removeAttribute("errorMsg");
        }
    }
}
