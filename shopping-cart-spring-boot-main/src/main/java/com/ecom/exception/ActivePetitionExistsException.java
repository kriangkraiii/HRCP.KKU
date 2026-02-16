package com.ecom.exception;

/**
 * Exception thrown when a user attempts to submit a new petition
 * while they already have an active petition in progress.
 * 
 * An active petition is one that has a status other than REJECTED or COMPLETED.
 */
public class ActivePetitionExistsException extends RuntimeException {
    
    public ActivePetitionExistsException(String message) {
        super(message);
    }
    
    public ActivePetitionExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
