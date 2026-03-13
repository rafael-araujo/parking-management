package com.parking.management.domain.exception;

/**
 * Thrown when a domain business rule is violated (e.g. exit time before entry time).
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
