package com.parking.management.adapter.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Standard error response returned by all error handlers")
public class ErrorResponse {

    @Schema(description = "Timestamp of the error (ISO-8601 UTC)", example = "2024-03-11T10:30:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP reason phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Human-readable summary of the error", example = "Dados de entrada inválidos")
    private String message;

    @Schema(description = "Request URI that triggered the error", example = "/webhook")
    private String path;

    @Schema(description = "List of field-level validation errors (empty when not applicable)")
    private List<FieldDetail> details;

    public ErrorResponse() {}

    public ErrorResponse(int status, String error, String message, String path,
                         List<FieldDetail> details) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.details = details != null ? details : List.of();
    }

    public Instant getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getError() { return error; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public List<FieldDetail> getDetails() { return details; }

    @Schema(description = "Field-level validation error detail")
    public static class FieldDetail {
        @Schema(description = "Field name that failed validation", example = "event_type")
        private String field;

        @Schema(description = "Validation error message", example = "must not be blank")
        private String message;

        public FieldDetail(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() { return field; }
        public String getMessage() { return message; }
    }
}
