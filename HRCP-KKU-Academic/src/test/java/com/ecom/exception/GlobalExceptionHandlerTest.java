package com.ecom.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.Model;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests exception handling for UserNotFoundException.
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    private Model model;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        model = new ConcurrentModel();
    }
    
    @Test
    void testHandleUserNotFound_ShouldReturn500View() {
        // Given
        UserNotFoundException exception = new UserNotFoundException("User not found");
        
        // When
        String viewName = exceptionHandler.handleUserNotFound(exception, model);
        
        // Then
        assertEquals("error/500", viewName);
        assertEquals("เกิดข้อผิดพลาดในการระบุตัวตนผู้ใช้", model.getAttribute("error"));
        assertEquals("ข้อผิดพลาดระบบ", model.getAttribute("errorTitle"));
    }
    
    @Test
    void testHandleUserNotFound_IgnoresOriginalMessage() {
        // Given
        String originalMessage = "User with ID 123 not found";
        UserNotFoundException exception = new UserNotFoundException(originalMessage);
        
        // When
        String viewName = exceptionHandler.handleUserNotFound(exception, model);
        
        // Then
        assertEquals("error/500", viewName);
        // Should use Thai message instead of original
        assertEquals("เกิดข้อผิดพลาดในการระบุตัวตนผู้ใช้", model.getAttribute("error"));
        assertNotEquals(originalMessage, model.getAttribute("error"));
    }
}
