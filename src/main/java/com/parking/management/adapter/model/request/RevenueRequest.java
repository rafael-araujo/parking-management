package com.parking.management.adapter.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "Filter parameters for the revenue query")
public class RevenueRequest {

    @Schema(description = "Date to query revenue for (YYYY-MM-DD)", example = "2024-03-11", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate date;

    @Schema(description = "Sector identifier", example = "A", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sector;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
}
