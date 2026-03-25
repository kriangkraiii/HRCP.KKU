package com.ecom.academic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GuideController {

    @GetMapping("/admin/academic/guide")
    public String adminGuide(Model model) {
        return "academic/guide_admin";
    }

    @GetMapping("/user/academic/guide")
    public String userGuide(Model model) {
        return "academic/guide_user";
    }
}
