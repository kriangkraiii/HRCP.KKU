package com.ecom.academic.service;

/**
 * Thrown when a committee member being saved matches another active member by email or Thai name.
 */
public class DuplicateCommitteeMemberException extends RuntimeException {

    public DuplicateCommitteeMemberException(String message) {
        super(message);
    }
}
