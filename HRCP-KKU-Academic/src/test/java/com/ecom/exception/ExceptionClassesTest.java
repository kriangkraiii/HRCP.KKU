package com.ecom.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for application exception classes.
 * Tests verify that exceptions can be created with messages and causes,
 * and that they properly extend RuntimeException.
 */
class ExceptionClassesTest {

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
        // Verify exceptions are RuntimeExceptions (unchecked)
        assertTrue(RuntimeException.class.isAssignableFrom(UserNotFoundException.class));
    }
}
