package com.parking.management.application.exception;

/**
 * Thrown when a webhook event is malformed or contains an unrecognised event type.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
