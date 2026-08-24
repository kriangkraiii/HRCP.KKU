package com.ecom.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Global exception handler for application exceptions.
 * Handles exceptions thrown by controllers and provides appropriate error views.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handle UserNotFoundException.
     * Returns a 500 error page when a user is not found.
     * 
     * @param ex the exception
     * @param model the model to add error attributes
     * @return the error view name
     */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleUserNotFound(UserNotFoundException ex, Model model) {
        model.addAttribute("error", "เกิดข้อผิดพลาดในการระบุตัวตนผู้ใช้");
        model.addAttribute("errorTitle", "ข้อผิดพลาดระบบ");
        return "error/500";
    }
}
