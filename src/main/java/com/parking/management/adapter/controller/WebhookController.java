package com.parking.management.adapter.controller;

import com.parking.management.adapter.model.request.WebhookEventRequest;
import com.parking.management.adapter.model.response.ErrorResponse;
import com.parking.management.application.usecase.WebhookUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Webhook", description = "Receives parking garage events from the simulator (ENTRY, PARKED, EXIT)")
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookUseCase webhookUseCase;

    public WebhookController(WebhookUseCase webhookUseCase) {
        this.webhookUseCase = webhookUseCase;
    }

    @Operation(
            summary = "Process a parking event",
            description = "Accepts ENTRY, PARKED, or EXIT events from the simulator. Idempotent: re-delivering the same event has no side effects.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Event processed successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed JSON, unknown event type, or missing required fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Sector at full capacity (ENTRY rejected)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<Void> handleWebhook(@Valid @RequestBody WebhookEventRequest request) {
        log.info("Received webhook event: type={}, plate={}", request.getEventType(), request.getLicensePlate());

        switch (request.getEventType()) {
            case ENTRY -> webhookUseCase.processEntry(
                    request.getLicensePlate(),
                    request.getEntryTime(),
                    request.getGateId());
            case PARKED -> webhookUseCase.processParked(
                    request.getLicensePlate(),
                    request.getLat(),
                    request.getLng());
            case EXIT -> webhookUseCase.processExit(
                    request.getLicensePlate(),
                    request.getExitTime());
        }

        return ResponseEntity.noContent().build();
    }
}
