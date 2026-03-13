package com.parking.management.application.exception;

/**
 * Thrown when a required resource (sector, session, spot) is not found.
 * Maps to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
