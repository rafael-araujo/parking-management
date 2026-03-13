package com.parking.management.adapter.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Revenue totals for the requested sector and date")
public class RevenueResponse {

    @Schema(description = "Total revenue amount", example = "150.75")
    private BigDecimal amount;

    @Schema(description = "Currency code (always BRL)", example = "BRL")
    private String currency;

    @Schema(description = "Timestamp of the response (ISO-8601 UTC)", example = "2024-03-11T10:30:00Z")
    private Instant timestamp;

    public RevenueResponse() {}

    public RevenueResponse(BigDecimal amount, String currency, Instant timestamp) {
        this.amount = amount;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
