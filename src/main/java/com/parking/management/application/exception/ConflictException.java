package com.parking.management.application.exception;

/**
 * Thrown when the operation conflicts with the current state
 * (e.g. garage/sector at full capacity, duplicate session).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
