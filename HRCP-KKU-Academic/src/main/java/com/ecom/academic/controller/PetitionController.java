package com.ecom.academic.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ecom.academic.dto.PetitionForm;
import com.ecom.academic.model.Petition;
import com.ecom.academic.service.PetitionService;
import com.ecom.exception.ActivePetitionExistsException;
import com.ecom.model.UserDtls;
import com.ecom.repository.UserRepository;
import com.ecom.service.AdminLogService;

import jakarta.validation.Valid;

/**
 * Controller for managing petition-related operations.
 * Handles petition creation, viewing, and listing.
 */
@Controller
@RequestMapping("/petitions")
public class PetitionController {

    @Autowired
    private PetitionService petitionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminLogService adminLogService;

    @Autowired
    private jakarta.servlet.http.HttpServletRequest httpRequest;

    /**
     * Show new petition form.
     * If user has an active petition, redirect to cannot_submit page.
     */
    @GetMapping("/new")
    public String showNewPetitionForm(Model model, Principal principal) {
        UserDtls user = getUser(principal);

        // Check if user can submit
        if (!petitionService.canUserSubmitPetition(user.getId())) {
            Petition activePetition = petitionService.getActivePetition(user.getId())
                .orElseThrow(() -> new RuntimeException("Active petition not found"));
            model.addAttribute("error", "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่");
            model.addAttribute("activePetition", activePetition);
            return "petition/cannot_submit";
        }

        model.addAttribute("petition", new PetitionForm());
        return "petition/new";
    }

    /**
     * Create a new petition.
     * Validates form data and creates petition if user is eligible.
     */
    @PostMapping("/create")
    public String createPetition(@Valid @ModelAttribute("petition") PetitionForm form,
                                 BindingResult result,
                                 Principal principal,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "petition/new";
        }

        UserDtls user = getUser(principal);

        try {
            Petition petition = petitionService.createPetition(
                user.getId(),
                form.getTitle(),
                form.getDescription()
            );

            // Log activity
            try {
                adminLogService.log(principal.getName(), user.getName(),
                        "CREATE_PETITION",
                        "สร้างคำร้องทั่วไป: " + form.getTitle(),
                        getClientIpAddress());
            } catch (Exception ignored) {}

            redirectAttributes.addFlashAttribute("success", "ยื่นคำร้องสำเร็จ");
            return "redirect:/petitions/" + petition.getId();
        } catch (ActivePetitionExistsException e) {
            model.addAttribute("error", e.getMessage());
            return "petition/new";
        }
    }

    /**
     * View petition details with status history.
     */
    @GetMapping("/{id}")
    public String viewPetition(@PathVariable Long id, Model model, Principal principal) {
        Petition petition = petitionService.getPetitionWithHistory(id);
        
        // Verify user has access to this petition
        UserDtls user = getUser(principal);
        if (!petition.getUser().getId().equals(user.getId()) && !"ROLE_ADMIN".equals(user.getRole())) {
            return "redirect:/petitions/my-petitions";
        }

        model.addAttribute("petition", petition);
        model.addAttribute("statusHistory", petition.getStatusHistory());
        return "petition/view";
    }

    /**
     * List all petitions for the current user.
     */
    @GetMapping("/my-petitions")
    public String myPetitions(Model model, Principal principal) {
        UserDtls user = getUser(principal);
        List<Petition> petitions = petitionService.getUserPetitions(user.getId());
        model.addAttribute("petitions", petitions);
        return "petition/list";
    }

    /**
     * Get current authenticated user.
     */
    private UserDtls getUser(Principal principal) {
        return userRepository.findByEmail(principal.getName());
    }

    private String getClientIpAddress() {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0];
        }
        return httpRequest.getRemoteAddr();
    }
}
