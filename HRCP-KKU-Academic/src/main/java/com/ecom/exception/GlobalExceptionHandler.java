package com.ecom.exception;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for petition-related exceptions.
 * Handles exceptions thrown by controllers and provides appropriate error views.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle PetitionNotFoundException.
     * Returns a 404 error page when a petition is not found.
     * 
     * @param ex the exception
     * @param model the model to add error attributes
     * @return the error view name
     */
    @ExceptionHandler(PetitionNotFoundException.class)
    public String handlePetitionNotFound(PetitionNotFoundException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        model.addAttribute("errorTitle", "ไม่พบคำร้อง");
        return "error/404";
    }
    
    /**
     * Handle UserNotFoundException.
     * Returns a 500 error page when a user is not found.
     * 
     * @param ex the exception
     * @param model the model to add error attributes
     * @return the error view name
     */
    @ExceptionHandler(UserNotFoundException.class)
    public String handleUserNotFound(UserNotFoundException ex, Model model) {
        model.addAttribute("error", "เกิดข้อผิดพลาดในการระบุตัวตนผู้ใช้");
        model.addAttribute("errorTitle", "ข้อผิดพลาดระบบ");
        return "error/500";
    }
}
