package com.parking.management.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public class RevenueDTO {

    private BigDecimal amount;
    private String currency;
    private Instant timestamp;

    public RevenueDTO() {}

    public RevenueDTO(BigDecimal amount, String currency, Instant timestamp) {
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
