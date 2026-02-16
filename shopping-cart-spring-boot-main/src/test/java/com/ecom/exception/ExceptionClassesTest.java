package com.ecom.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for petition-related exception classes.
 * Tests verify that exceptions can be created with messages and causes,
 * and that they properly extend RuntimeException.
 */
class ExceptionClassesTest {

    @Test
    void testActivePetitionExistsException_withMessage() {
        String message = "ไม่สามารถยื่นคำร้องใหม่ได้ เนื่องจากมีคำร้องที่กำลังดำเนินการอยู่";
        
        ActivePetitionExistsException exception = new ActivePetitionExistsException(message);
        
        assertEquals(message, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testActivePetitionExistsException_withMessageAndCause() {
        String message = "Active petition exists";
        Throwable cause = new IllegalStateException("Test cause");
        
        ActivePetitionExistsException exception = new ActivePetitionExistsException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testPetitionNotFoundException_withMessage() {
        String message = "Petition not found";
        
        PetitionNotFoundException exception = new PetitionNotFoundException(message);
        
        assertEquals(message, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testPetitionNotFoundException_withMessageAndCause() {
        String message = "Petition not found";
        Throwable cause = new IllegalArgumentException("Test cause");
        
        PetitionNotFoundException exception = new PetitionNotFoundException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testUserNotFoundException_withMessage() {
        String message = "User not found";
        
        UserNotFoundException exception = new UserNotFoundException(message);
        
        assertEquals(message, exception.getMessage());
        assertInstanceOf(RuntimeException.class, exception);
    }

    @Test
    void testUserNotFoundException_withMessageAndCause() {
        String message = "User not found";
        Throwable cause = new IllegalArgumentException("Test cause");
        
        UserNotFoundException exception = new UserNotFoundException(message, cause);
        
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testExceptionsAreUnchecked() {
        // Verify all exceptions are RuntimeExceptions (unchecked)
        assertTrue(RuntimeException.class.isAssignableFrom(ActivePetitionExistsException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(PetitionNotFoundException.class));
        assertTrue(RuntimeException.class.isAssignableFrom(UserNotFoundException.class));
    }
}
