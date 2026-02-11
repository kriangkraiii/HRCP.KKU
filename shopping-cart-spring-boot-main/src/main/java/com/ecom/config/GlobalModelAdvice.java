package com.ecom.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void transferSessionMessages(HttpSession session, Model model) {
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
