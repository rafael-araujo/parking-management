package com.parking.management.adapter.controller;

import com.parking.management.adapter.model.response.ErrorResponse;
import com.parking.management.adapter.model.response.RevenueResponse;
import com.parking.management.application.exception.InvalidEventException;
import com.parking.management.application.mapper.RevenueMapper;
import com.parking.management.application.usecase.RevenueUseCase;
import com.parking.management.domain.model.RevenueDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Revenue", description = "Retrieves aggregated revenue data for a sector on a given date")
@RestController
public class RevenueController {

    private final RevenueUseCase revenueUseCase;
    private final RevenueMapper revenueMapper;

    public RevenueController(RevenueUseCase revenueUseCase, RevenueMapper revenueMapper) {
        this.revenueUseCase = revenueUseCase;
        this.revenueMapper = revenueMapper;
    }

    @Operation(
            summary = "Get revenue for a sector",
            description = "Returns the total revenue collected for the given sector on the specified date, in BRL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Revenue data returned successfully",
                    content = @Content(schema = @Schema(implementation = RevenueResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or missing request fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Sector not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/revenue")
    public ResponseEntity<RevenueResponse> getRevenue(
            @Parameter(description = "Date to query revenue for (YYYY-MM-DD)", example = "2025-01-01", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Sector identifier", example = "A", required = true)
            @RequestParam String sector) {

        if (sector.isBlank()) {
            throw new InvalidEventException("Parameters 'date' and 'sector' are required");
        }

        RevenueDTO dto = revenueUseCase.getRevenue(sector, date);
        return ResponseEntity.ok(revenueMapper.toResponse(dto));
    }
}
