package com.ecom.exception;

/**
 * Exception thrown when a requested petition cannot be found in the system.
 */
public class PetitionNotFoundException extends RuntimeException {
    
    public PetitionNotFoundException(String message) {
        super(message);
    }
    
    public PetitionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
