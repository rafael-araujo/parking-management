package com.parking.management.application.exception;

/**
 * Thrown when communication with an external service (e.g. the simulator) fails.
 * Maps to HTTP 503 Service Unavailable.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
